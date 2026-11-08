"""회원가입·로그인·세션·차 QR 로그인. 웹(폰)과 API(차)가 같이 쓴다."""

import logging
import re
import uuid
from datetime import timedelta

from sqlalchemy import delete, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.security import hash_password, new_code, new_token, token_hash, verify_password
from app.config import Settings
from app.db import utcnow
from app.models import CarLoginRequest, Session, User

log = logging.getLogger("auth")

LOGIN_ID = re.compile(r"^[a-z0-9_]{4,20}$")
MIN_PASSWORD = 8


class AuthError(Exception):
    """사용자에게 그대로 보여 줄 수 있는 오류 문구."""


async def signup(db: AsyncSession, login_id: str, password: str, display_name: str) -> User:
    login_id = login_id.strip().lower()
    if not LOGIN_ID.match(login_id):
        raise AuthError("아이디는 영문 소문자·숫자·밑줄 4~20자로 만들어 주세요")
    if len(password) < MIN_PASSWORD:
        raise AuthError(f"비밀번호는 {MIN_PASSWORD}자 이상이어야 해요")
    user = User(login_id=login_id, password_hash=hash_password(password), display_name=display_name.strip() or login_id)
    db.add(user)
    try:
        await db.commit()
    except IntegrityError:
        # 동시에 같은 아이디로 가입해도 DB 고유 제약이 하나만 남긴다.
        await db.rollback()
        raise AuthError("이미 쓰고 있는 아이디예요") from None
    log.info("회원가입: %s", login_id)
    return user


async def authenticate(db: AsyncSession, login_id: str, password: str) -> User:
    user = await db.scalar(select(User).where(User.login_id == login_id.strip().lower()))
    if user is None or not verify_password(user.password_hash, password):
        raise AuthError("아이디나 비밀번호가 맞지 않아요")
    return user


async def create_session(db: AsyncSession, settings: Settings, user: User, kind: str, device_name: str = "") -> str:
    """새 세션을 만들고 토큰 원문을 돌려준다 (원문은 이때 한 번만 알 수 있다)."""
    token = new_token()
    db.add(
        Session(
            user_id=user.id,
            token_hash=token_hash(token),
            kind=kind,
            device_name=device_name[:60],
            expires_at=utcnow() + timedelta(days=settings.session_days),
        )
    )
    await db.commit()
    log.info("로그인: %s (%s%s)", user.login_id, kind, f", {device_name}" if device_name else "")
    return token


async def user_for_token(db: AsyncSession, token: str) -> User | None:
    session = await db.scalar(select(Session).where(Session.token_hash == token_hash(token)))
    now = utcnow()
    if session is None or session.revoked_at is not None or session.expires_at < now:
        return None
    session.last_used_at = now
    await db.commit()
    return session.user


async def revoke(db: AsyncSession, token: str) -> None:
    await db.execute(update(Session).where(Session.token_hash == token_hash(token)).values(revoked_at=utcnow()))
    await db.commit()


# 연결된 차 관리·계정 삭제 (폰 웹 MY)


async def car_sessions(db: AsyncSession, user: User) -> list[Session]:
    """지금 로그인돼 있는 차(세션) 목록. 최근에 쓴 차가 위로 온다."""
    now = utcnow()
    rows = await db.scalars(
        select(Session)
        .where(Session.user_id == user.id, Session.kind == "car", Session.revoked_at.is_(None), Session.expires_at > now)
        .order_by(Session.last_used_at.desc())
    )
    return list(rows)


async def disconnect_car(db: AsyncSession, user: User, session_id: uuid.UUID) -> bool:
    """내 차 세션 하나를 끊는다. 다른 사람의 세션이거나 이미 끊긴 것이면 False."""
    result = await db.execute(
        update(Session)
        .where(Session.id == session_id, Session.user_id == user.id, Session.kind == "car", Session.revoked_at.is_(None))
        .values(revoked_at=utcnow())
    )
    await db.commit()
    if result.rowcount:
        log.info("차 연결 해제: %s", user.login_id)
    return bool(result.rowcount)


async def delete_account(db: AsyncSession, user: User, password: str) -> None:
    """비밀번호를 다시 확인한 뒤 계정을 지운다. 세션·공조 프리셋·차 로그인 요청은 DB가 함께 지운다."""
    if not verify_password(user.password_hash, password):
        raise AuthError("비밀번호가 맞지 않아요")
    login_id = user.login_id
    await db.execute(delete(User).where(User.id == user.id))
    await db.commit()
    log.info("계정 삭제: %s", login_id)


# 차 QR 로그인


async def start_car_login(db: AsyncSession, settings: Settings, device_name: str) -> tuple[CarLoginRequest, str]:
    """차가 로그인 요청을 만든다. 폴링 비밀값 원문은 차에게만 한 번 돌려준다."""
    poll_secret = new_token()
    request = CarLoginRequest(
        code=new_code(),
        poll_hash=token_hash(poll_secret),
        device_name=device_name[:60],
        expires_at=utcnow() + timedelta(seconds=settings.car_login_seconds),
    )
    db.add(request)
    await db.commit()
    return request, poll_secret


async def pending_car_login(db: AsyncSession, code: str) -> CarLoginRequest:
    request = await db.scalar(select(CarLoginRequest).where(CarLoginRequest.code == code.strip().upper()))
    if request is None:
        raise AuthError("로그인 요청을 찾지 못했어요. 차 화면의 QR을 다시 찍어 주세요")
    if request.expires_at < utcnow():
        raise AuthError("QR이 만료됐어요. 차 화면에서 새 QR을 띄워 주세요")
    if request.status != "pending":
        raise AuthError("이미 처리된 로그인 요청이에요")
    return request


async def approve_car_login(db: AsyncSession, code: str, user: User) -> CarLoginRequest:
    request = await pending_car_login(db, code)
    # 두 폰이 동시에 승인해도 pending인 요청 하나만 바뀐다.
    result = await db.execute(
        update(CarLoginRequest)
        .where(CarLoginRequest.id == request.id, CarLoginRequest.status == "pending")
        .values(status="approved", user_id=user.id)
    )
    await db.commit()
    if result.rowcount != 1:
        raise AuthError("이미 처리된 로그인 요청이에요")
    log.info("차 로그인 승인: %s → %s", user.login_id, request.device_name or request.code)
    return request


async def poll_car_login(db: AsyncSession, settings: Settings, request_id: uuid.UUID, poll_secret: str) -> tuple[str, str | None, User | None]:
    """(상태, 차 세션 토큰, 사용자). 승인된 요청은 처음 폴링할 때 한 번만 토큰을 준다."""
    request = await db.get(CarLoginRequest, request_id)
    if request is None or request.poll_hash != token_hash(poll_secret):
        raise AuthError("로그인 요청을 찾지 못했어요")
    if request.status == "pending":
        return ("expired" if request.expires_at < utcnow() else "pending"), None, None
    if request.status != "approved":
        return request.status, None, None
    # 같은 요청으로 두 번 토큰을 받지 못하게 approved → consumed를 한 번에 바꾼다.
    result = await db.execute(
        update(CarLoginRequest)
        .where(CarLoginRequest.id == request.id, CarLoginRequest.status == "approved")
        .values(status="consumed")
    )
    if result.rowcount != 1:
        await db.rollback()
        return "consumed", None, None
    user = await db.get(User, request.user_id)
    token = await create_session(db, settings, user, "car", request.device_name)
    return "approved", token, user

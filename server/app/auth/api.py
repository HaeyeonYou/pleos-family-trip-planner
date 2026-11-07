"""차 앱(과 이후 모바일 앱)이 쓰는 JSON API."""

import io
import uuid
from datetime import datetime

import qrcode
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import service
from app.auth.deps import bearer_token, current_user, get_db, public_base_url
from app.config import Settings, get_settings
from app.models import CarLoginRequest, User

router = APIRouter(prefix="/auth", tags=["auth"])


class UserOut(BaseModel):
    login_id: str
    display_name: str


class CarLoginStart(BaseModel):
    device_name: str = Field("", max_length=60, description="차 화면에 보일 기기 이름")


class CarLoginStarted(BaseModel):
    request_id: uuid.UUID
    code: str
    login_url: str = Field(..., description="QR에 넣을 폰 로그인 주소")
    qr_url: str = Field(..., description="QR 이미지 주소 (PNG)")
    poll_secret: str = Field(..., description="폴링할 때 보낼 비밀값. 차만 알고 있어야 한다")
    expires_at: datetime


class CarLoginPoll(BaseModel):
    poll_secret: str


class CarLoginStatus(BaseModel):
    """pending: 폰 승인 대기, approved: 로그인 완료(token 포함, 한 번만), expired: 만료, consumed: 이미 받음."""

    status: str
    token: str | None = None
    user: UserOut | None = None


def _user_out(user: User) -> UserOut:
    return UserOut(login_id=user.login_id, display_name=user.display_name)


@router.post("/car-login", response_model=CarLoginStarted)
async def start_car_login(
    req: CarLoginStart,
    request: Request,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
    base_url: str = Depends(public_base_url),
) -> CarLoginStarted:
    login, secret = await service.start_car_login(db, settings, req.device_name)
    return CarLoginStarted(
        request_id=login.id,
        code=login.code,
        login_url=f"{base_url}/m/car?code={login.code}",
        # 차는 에뮬레이터 주소(10.0.2.2)로 접속하므로 이미지 주소는 차가 접속한 주소 기준으로 준다.
        qr_url=str(request.url_for("car_login_qr", code=login.code)),
        poll_secret=secret,
        expires_at=login.expires_at,
    )


@router.get("/car-login/{code}/qr.png", name="car_login_qr")
async def car_login_qr(code: str, db: AsyncSession = Depends(get_db), base_url: str = Depends(public_base_url)) -> Response:
    try:
        login: CarLoginRequest = await service.pending_car_login(db, code)
    except service.AuthError as e:
        raise HTTPException(404, str(e)) from e
    image = qrcode.make(f"{base_url}/m/car?code={login.code}", box_size=12, border=2)
    buf = io.BytesIO()
    image.save(buf, format="PNG")
    return Response(buf.getvalue(), media_type="image/png", headers={"Cache-Control": "no-store"})


@router.post("/car-login/{request_id}/poll", response_model=CarLoginStatus)
async def poll_car_login(
    request_id: uuid.UUID,
    req: CarLoginPoll,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> CarLoginStatus:
    try:
        status, token, user = await service.poll_car_login(db, settings, request_id, req.poll_secret)
    except service.AuthError as e:
        raise HTTPException(404, str(e)) from e
    return CarLoginStatus(status=status, token=token, user=_user_out(user) if user else None)


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(current_user)) -> UserOut:
    return _user_out(user)


@router.post("/logout", status_code=204)
async def logout(request: Request, db: AsyncSession = Depends(get_db)) -> None:
    token = bearer_token(request)
    if token:
        await service.revoke(db, token)

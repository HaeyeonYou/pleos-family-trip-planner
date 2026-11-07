"""회원·세션·차 QR 로그인 테이블."""

import uuid
from datetime import datetime

from sqlalchemy import ForeignKey, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base, utcnow


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    login_id: Mapped[str] = mapped_column(String(20), unique=True)
    password_hash: Mapped[str] = mapped_column(String(200))
    display_name: Mapped[str] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(default=utcnow)


class Session(Base):
    """로그인 세션 하나 = 기기 하나 (폰 웹, 차). 토큰은 해시만 저장한다."""

    __tablename__ = "sessions"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    # web: 폰 브라우저, car: 차 앱
    kind: Mapped[str] = mapped_column(String(10))
    device_name: Mapped[str] = mapped_column(String(60), default="")
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    last_used_at: Mapped[datetime] = mapped_column(default=utcnow)
    expires_at: Mapped[datetime]
    revoked_at: Mapped[datetime | None] = mapped_column(default=None)

    user: Mapped[User] = relationship(lazy="joined")


class CarLoginRequest(Base):
    """차 화면 QR 로그인 요청. 폰에서 승인하면 차가 폴링으로 세션을 받는다 (한 번만)."""

    __tablename__ = "car_login_requests"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    # QR 주소에 들어가는 코드 (폰이 쓴다)
    code: Mapped[str] = mapped_column(String(16), unique=True)
    # 차만 아는 폴링 비밀값의 해시
    poll_hash: Mapped[str] = mapped_column(String(64))
    device_name: Mapped[str] = mapped_column(String(60), default="")
    # pending → approved → consumed (차가 토큰을 받음)
    status: Mapped[str] = mapped_column(String(10), default="pending")
    user_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), default=None)
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    expires_at: Mapped[datetime]


class ClimatePreset(Base):
    """폰에서 보낸 공조 프리셋. 사용자마다 지금 보낸 것 하나를 두고, 차가 적용하면 applied_at을 채운다."""

    __tablename__ = "climate_presets"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    # 설정 값은 app/presets.py의 ClimateSettings 모양 그대로 둔다 (항목이 늘어도 테이블은 그대로)
    settings: Mapped[dict] = mapped_column(JSONB)
    sent_at: Mapped[datetime] = mapped_column(default=utcnow)
    applied_at: Mapped[datetime | None] = mapped_column(default=None)

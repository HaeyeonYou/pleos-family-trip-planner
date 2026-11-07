"""요청마다 DB 세션과 로그인 사용자를 꺼내는 의존성."""

import socket
from collections.abc import AsyncIterator

from fastapi import Depends, HTTPException, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import service
from app.config import Settings, get_settings
from app.models import User

WEB_COOKIE = "sid"


async def get_db(request: Request) -> AsyncIterator[AsyncSession]:
    async for session in request.app.state.db.session():
        yield session


def bearer_token(request: Request) -> str | None:
    header = request.headers.get("authorization", "")
    return header[7:] if header.lower().startswith("bearer ") else None


async def current_user(request: Request, db: AsyncSession = Depends(get_db)) -> User:
    """차 앱: Authorization: Bearer <토큰>, 폰 웹: 쿠키."""
    token = bearer_token(request) or request.cookies.get(WEB_COOKIE)
    user = await service.user_for_token(db, token) if token else None
    if user is None:
        raise HTTPException(401, "로그인이 필요해요")
    return user


async def optional_web_user(request: Request, db: AsyncSession = Depends(get_db)) -> User | None:
    token = request.cookies.get(WEB_COOKIE)
    return await service.user_for_token(db, token) if token else None


def public_base_url(settings: Settings = Depends(get_settings)) -> str:
    """폰이 접속할 주소. 설정이 없으면 같은 와이파이의 맥 사설 IP를 쓴다 (로컬 개발)."""
    if settings.public_base_url:
        return settings.public_base_url.rstrip("/")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.connect(("10.255.255.255", 1))  # 실제로 보내지 않고 나가는 인터페이스 IP만 얻는다
        ip = s.getsockname()[0]
    return f"http://{ip}:8000"

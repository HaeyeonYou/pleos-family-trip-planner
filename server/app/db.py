"""PostgreSQL 연결. 요청마다 세션을 따로 쓰고, 연결은 풀에서 재사용한다 (여러 사용자 동시 처리)."""

from collections.abc import AsyncIterator
from datetime import datetime, timezone

from sqlalchemy import DateTime, MetaData
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.config import Settings

# 마이그레이션에서 제약 이름이 일정하게 나오도록 규칙을 정한다.
NAMING = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING)
    type_annotation_map = {datetime: DateTime(timezone=True)}


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Database:
    def __init__(self, settings: Settings, url: str | None = None):
        self.engine: AsyncEngine = create_async_engine(
            url or settings.database_url,
            pool_size=settings.db_pool_size,
            max_overflow=settings.db_max_overflow,
            pool_pre_ping=True,  # NAS 재시작 등으로 끊긴 연결을 걸러낸다
        )
        self.sessions = async_sessionmaker(self.engine, expire_on_commit=False)

    async def session(self) -> AsyncIterator[AsyncSession]:
        async with self.sessions() as session:
            yield session

    async def close(self) -> None:
        await self.engine.dispose()

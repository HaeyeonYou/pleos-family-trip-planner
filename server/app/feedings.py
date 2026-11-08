"""수유 기록과 다음 수유 시각. 폰 웹의 탭 한 번 기록과 차 음성 '방금 수유했어'가 같은 기록에 쌓인다."""

import uuid
from datetime import datetime, timedelta
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.db import utcnow
from app.family import _owned, list_children
from app.models import Child, FeedingLog, User

router = APIRouter(prefix="/me/feedings", tags=["feedings"])

Kind = Literal["formula", "breast", "solid"]
KIND_LABELS = {"formula": "분유", "breast": "모유", "solid": "이유식 · 간식"}
DEFAULT_INTERVAL = 180  # 아이 정보에 수유 간격이 없을 때 다음 수유 계산에 쓰는 기본값(분)


class FeedingIn(BaseModel):
    child_id: uuid.UUID | None = Field(None, description="비우면 첫째 아이로 본다")
    fed_at: datetime = Field(..., description="수유한 시각 (시간대 포함 ISO 8601)")
    kind: Kind = "formula"
    amount_ml: int | None = Field(None, ge=0, le=500)
    source: Literal["web", "car"] = "web"

    @field_validator("fed_at")
    @classmethod
    def _aware_and_recent(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            raise ValueError("시간대를 포함해 주세요 (예: 2026-11-08T12:10:00+09:00)")
        now = utcnow()
        if v > now + timedelta(minutes=10) or v < now - timedelta(days=30):
            raise ValueError("수유 시각을 다시 확인해 주세요")
        return v


class FeedingOut(BaseModel):
    id: uuid.UUID
    child_id: uuid.UUID | None
    fed_at: datetime
    kind: Kind
    amount_ml: int | None
    source: str


class FeedingStatus(BaseModel):
    child_id: uuid.UUID | None
    nickname: str | None
    interval_minutes: int
    last_fed_at: datetime | None
    next_due_at: datetime | None
    minutes_left: int | None = Field(None, description="다음 수유까지 남은 분. 지났으면 음수")
    overdue: bool = False


def out(row: FeedingLog) -> FeedingOut:
    return FeedingOut(id=row.id, child_id=row.child_id, fed_at=row.fed_at, kind=row.kind, amount_ml=row.amount_ml, source=row.source)


async def record(db: AsyncSession, user: User, feeding_id: uuid.UUID, body: FeedingIn) -> FeedingLog:
    row = await _owned(db, FeedingLog, feeding_id, user)
    child_id = body.child_id
    if child_id is not None:
        child = await _owned(db, Child, child_id, user)
        if child is None:
            raise HTTPException(404, "아이를 찾지 못했어요")
    elif row is None:
        children = await list_children(db, user)
        child_id = children[0].id if children else None
    if row is None:
        row = FeedingLog(id=feeding_id, user_id=user.id)
        db.add(row)
    row.child_id = child_id
    row.fed_at = body.fed_at
    row.kind = body.kind
    row.amount_ml = body.amount_ml
    row.source = body.source
    await db.commit()
    return row


@router.put("/{feeding_id}", response_model=FeedingOut)
async def put_feeding(feeding_id: uuid.UUID, body: FeedingIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """수유 기록. id를 클라이언트가 정하면 같은 요청을 다시 보내도(차가 오프라인이었다가 재전송) 하나만 남는다."""
    return out(await record(db, user, feeding_id, body))


@router.post("", response_model=FeedingOut, status_code=201)
async def post_feeding(body: FeedingIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    return out(await record(db, user, uuid.uuid4(), body))


@router.get("", response_model=list[FeedingOut])
async def list_feedings(
    child_id: uuid.UUID | None = None,
    since: datetime | None = Query(None, description="이 시각 이후만"),
    limit: int = Query(50, ge=1, le=200),
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(FeedingLog).where(FeedingLog.user_id == user.id)
    if child_id:
        stmt = stmt.where(FeedingLog.child_id == child_id)
    if since:
        stmt = stmt.where(FeedingLog.fed_at >= since)
    rows = await db.scalars(stmt.order_by(FeedingLog.fed_at.desc()).limit(limit))
    return [out(r) for r in rows]


@router.delete("/{feeding_id}", status_code=204)
async def delete_feeding(feeding_id: uuid.UUID, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    row = await _owned(db, FeedingLog, feeding_id, user)
    if row is None:
        raise HTTPException(404, "기록을 찾지 못했어요")
    await db.delete(row)
    await db.commit()


async def status_for(db: AsyncSession, user: User, child_id: uuid.UUID | None = None, now: datetime | None = None) -> FeedingStatus:
    """마지막 수유와 다음 수유 시각. 아이가 없어도 기록만으로 마지막 수유는 알려 준다."""
    now = now or utcnow()
    children = await list_children(db, user)
    child = next((c for c in children if c.id == child_id), None) if child_id else (children[0] if children else None)
    interval = (child.feed_interval_minutes if child else None) or DEFAULT_INTERVAL
    stmt = select(FeedingLog).where(FeedingLog.user_id == user.id)
    if child:
        stmt = stmt.where((FeedingLog.child_id == child.id) | (FeedingLog.child_id.is_(None)))
    last = await db.scalar(stmt.order_by(FeedingLog.fed_at.desc()).limit(1))
    due = last.fed_at + timedelta(minutes=interval) if last else None
    left = round((due - now).total_seconds() / 60) if due else None
    return FeedingStatus(
        child_id=child.id if child else None,
        nickname=child.nickname if child else None,
        interval_minutes=interval,
        last_fed_at=last.fed_at if last else None,
        next_due_at=due,
        minutes_left=left,
        overdue=left is not None and left < 0,
    )


@router.get("/status", response_model=FeedingStatus)
async def feeding_status(child_id: uuid.UUID | None = None, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    return await status_for(db, user, child_id)

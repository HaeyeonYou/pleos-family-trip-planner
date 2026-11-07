"""'지금 N대 가는 중': 코스 시작을 장소별로 세어 최근 1시간 안의 수만 돌려준다.

누가 시작했는지는 저장하지 않는다 (장소 키와 시각만). 로그인 없이 쓴다.
"""

from datetime import timedelta

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import get_db
from app.db import utcnow
from app.models import CourseStart

router = APIRouter(prefix="/course")
WINDOW = timedelta(hours=1)
MAX_KEYS = 20


class StartBody(BaseModel):
    place_key: str = Field(..., min_length=3, max_length=80, description="source:source_id")


@router.post("/starts", status_code=204)
async def record_start(body: StartBody, db: AsyncSession = Depends(get_db)) -> None:
    db.add(CourseStart(place_key=body.place_key))
    await db.commit()


@router.get("/going")
async def going(keys: str = Query(..., description="쉼표로 구분한 장소 키"), db: AsyncSession = Depends(get_db)) -> dict[str, int]:
    """장소 키마다 최근 1시간 안에 시작한 수 (없는 키는 0)."""
    wanted = [k for k in dict.fromkeys(keys.split(",")) if k][:MAX_KEYS]
    rows = await db.execute(
        select(CourseStart.place_key, func.count())
        .where(CourseStart.place_key.in_(wanted), CourseStart.started_at >= utcnow() - WINDOW)
        .group_by(CourseStart.place_key)
    )
    counts = dict(rows.all())
    return {k: counts.get(k, 0) for k in wanted}

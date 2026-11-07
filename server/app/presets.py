"""공조 프리셋: 폰(웹)에서 정해 '차로 보내기' → 차 앱이 시작할 때 받아 확인·적용.

시동이 꺼진 차는 원격 제어할 수 없으므로(Pleos 앱은 인포테인먼트가 켜져야 동작) 미리 보내 두는 방식이다.
"""

import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.db import utcnow
from app.models import ClimatePreset, User


class ClimateSettings(BaseModel):
    """차 앱이 Vehicle SDK로 적용하는 값. 단계 값은 차종 범위를 넘으면 차에서 가장 가까운 값으로 맞춘다."""

    name: str = Field("우리 가족 설정", max_length=30)
    power: bool = True
    temperature: float = Field(23.0, ge=16, le=30, description="설정 온도(°C), 0.5 단위")
    fan_speed: int = Field(3, ge=1, le=8)
    ac: bool = False
    air_cleaner: bool = False
    driver_seat_heat: int = Field(0, ge=0, le=3, description="운전석 열선 단계 (0 끔)")
    passenger_seat_heat: int = Field(0, ge=0, le=3, description="조수석 열선 단계 (0 끔)")
    steering_wheel_heat: int = Field(0, ge=0, le=3, description="핸들 열선 단계 (0 끔)")


class PendingPreset(BaseModel):
    id: uuid.UUID
    settings: ClimateSettings
    sent_at: datetime


async def save(db: AsyncSession, user: User, settings: ClimateSettings) -> None:
    """사용자의 프리셋을 새로 보낸 것으로 바꾼다 (차가 다시 받게 applied_at을 비운다)."""
    stmt = insert(ClimatePreset).values(id=uuid.uuid4(), user_id=user.id, settings=settings.model_dump(), sent_at=utcnow())
    await db.execute(
        stmt.on_conflict_do_update(
            index_elements=[ClimatePreset.user_id],
            # 새로 보낼 때마다 id를 바꿔, 차가 예전 것을 적용 완료로 표시해도 새 프리셋은 대기로 남게 한다.
            set_={"id": stmt.excluded.id, "settings": stmt.excluded.settings, "sent_at": stmt.excluded.sent_at, "applied_at": None},
        )
    )
    await db.commit()


async def current(db: AsyncSession, user: User) -> ClimatePreset | None:
    return await db.scalar(select(ClimatePreset).where(ClimatePreset.user_id == user.id))


router = APIRouter(prefix="/presets", tags=["presets"])


@router.get("/pending", response_model=PendingPreset, responses={204: {"description": "받을 프리셋 없음"}})
async def pending(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """차 앱이 시작할 때 부른다. 아직 적용하지 않은 프리셋이 있으면 준다."""
    preset = await current(db, user)
    if preset is None or preset.applied_at is not None:
        return Response(status_code=204)
    return PendingPreset(id=preset.id, settings=ClimateSettings(**preset.settings), sent_at=preset.sent_at)


@router.post("/{preset_id}/applied", status_code=204)
async def applied(preset_id: uuid.UUID, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    """차에서 적용(또는 넘김)했다고 표시한다. 다른 사용자의 프리셋은 건드릴 수 없다."""
    preset = await db.get(ClimatePreset, preset_id)
    if preset is None or preset.user_id != user.id:
        raise HTTPException(404, "프리셋을 찾지 못했어요")
    preset.applied_at = utcnow()
    await db.commit()

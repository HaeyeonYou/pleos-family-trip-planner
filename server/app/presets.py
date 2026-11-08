"""공조 프리셋: 폰(웹)에서 정해 '차로 보내기' → 차 앱이 시작할 때 받아 확인·적용.

시동이 꺼진 차는 원격 제어할 수 없으므로(Pleos 앱은 인포테인먼트가 켜져야 동작) 미리 보내 두는 방식이다.
프리셋은 슬롯 3개(탑승 준비·낮잠·수유 정차)다. 슬롯을 모르는 예전 차 앱은 `GET /presets/pending`으로 탑승 준비만 받는다.
"""

import uuid
from datetime import datetime
from typing import Literal

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
    media_volume: int | None = Field(None, ge=0, le=100, description="미디어 음량(%). 비우면 음량은 건드리지 않는다")


Slot = Literal["boarding", "nap", "feeding_stop"]
LEGACY_SLOT: Slot = "boarding"

# 슬롯별 이름·적용 시점 문구와, 아직 보낸 적 없을 때 웹이 보여 줄 기본값
SLOTS: dict[str, dict] = {
    "boarding": {"label": "탑승 준비", "when": "탑승 시", "defaults": ClimateSettings(name="탑승 준비", temperature=24, fan_speed=2, media_volume=30)},
    "nap": {"label": "낮잠", "when": "수면 모드와 함께", "defaults": ClimateSettings(name="낮잠", temperature=23, fan_speed=1, media_volume=20)},
    "feeding_stop": {"label": "수유 정차", "when": "정차 시", "defaults": ClimateSettings(name="수유 정차", temperature=24, fan_speed=1, media_volume=10)},
}


class PendingPreset(BaseModel):
    id: uuid.UUID
    settings: ClimateSettings
    sent_at: datetime


class PresetState(BaseModel):
    """슬롯 하나의 현재 상태. 보낸 적이 없으면 sent=False에 기본 설정을 준다."""

    slot: Slot
    label: str
    when: str
    sent: bool
    id: uuid.UUID | None = None
    settings: ClimateSettings
    sent_at: datetime | None = None
    applied_at: datetime | None = None
    pending: bool = Field(False, description="보냈는데 아직 차에서 적용하지 않음")


async def save(db: AsyncSession, user: User, settings: ClimateSettings, slot: str = LEGACY_SLOT, applied: bool = False) -> None:
    """사용자의 슬롯 프리셋을 새로 보낸 것으로 바꾼다 (차가 다시 받게 applied_at을 비운다). 차에서 직접 고친 것이면 applied=True."""
    now = utcnow()
    stmt = insert(ClimatePreset).values(
        id=uuid.uuid4(), user_id=user.id, slot=slot, settings=settings.model_dump(), sent_at=now, applied_at=now if applied else None
    )
    await db.execute(
        stmt.on_conflict_do_update(
            index_elements=[ClimatePreset.user_id, ClimatePreset.slot],
            # 새로 보낼 때마다 id를 바꿔, 차가 예전 것을 적용 완료로 표시해도 새 프리셋은 대기로 남게 한다.
            set_={"id": stmt.excluded.id, "settings": stmt.excluded.settings, "sent_at": stmt.excluded.sent_at, "applied_at": stmt.excluded.applied_at},
        )
    )
    await db.commit()


async def current(db: AsyncSession, user: User, slot: str = LEGACY_SLOT) -> ClimatePreset | None:
    return await db.scalar(select(ClimatePreset).where(ClimatePreset.user_id == user.id, ClimatePreset.slot == slot))


async def states(db: AsyncSession, user: User) -> list[PresetState]:
    """슬롯 3개의 상태. 보낸 적 없는 슬롯은 기본 설정으로 채운다."""
    rows = {p.slot: p for p in await db.scalars(select(ClimatePreset).where(ClimatePreset.user_id == user.id))}
    result = []
    for slot, meta in SLOTS.items():
        row = rows.get(slot)
        result.append(
            PresetState(
                slot=slot,
                label=meta["label"],
                when=meta["when"],
                sent=row is not None,
                id=row.id if row else None,
                settings=ClimateSettings(**row.settings) if row else meta["defaults"],
                sent_at=row.sent_at if row else None,
                applied_at=row.applied_at if row else None,
                pending=row is not None and row.applied_at is None,
            )
        )
    return result


router = APIRouter(prefix="/presets", tags=["presets"])


@router.get("", response_model=list[PresetState])
async def list_presets(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """슬롯 3개(탑승 준비·낮잠·수유 정차)의 현재 설정과 적용 상태. 차 앱 새 버전과 폰 웹이 쓴다."""
    return await states(db, user)


@router.put("/{slot}", response_model=PresetState)
async def put_preset(
    slot: Slot,
    settings: ClimateSettings,
    applied_on_car: bool = False,
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
):
    """슬롯 하나를 저장한다. 차에서 정차 중 직접 고친 것이면 applied_on_car=true (이미 차에 적용된 값이라 대기로 남기지 않는다)."""
    await save(db, user, settings, slot, applied=applied_on_car)
    return next(state for state in await states(db, user) if state.slot == slot)


@router.get("/pending", response_model=PendingPreset, responses={204: {"description": "받을 프리셋 없음"}})
async def pending(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """(예전 차 앱용) 아직 적용하지 않은 탑승 준비 프리셋이 있으면 준다. 낮잠·수유 정차는 `GET /presets`로 받는다."""
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

"""자주 가는 케어 장소 (차 온보딩 O02 '자주 가는 케어 장소', QO9).

차는 도착 기록으로 케어 장소(수유실이 확인됐거나 수유실·정차 공간 후보와 같은 곳)를 고른다. 그 목록을 계정에 올려 두면
다른 차에서 QR로 로그인할 때 내려받아 케어 화면 '자주 가는 케어 장소'를 바로 채울 수 있다.

개인정보 최소화: 장소 키·이름·확인된 시설·방문 수·마지막 방문 날짜만 받는다. 위치 좌표는 받지 않는다.
"""

from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.db import utcnow
from app.models import CarePlace, User

router = APIRouter(prefix="/me", tags=["care-places"])

MAX_PLACES = 20
Amenity = Literal["nursing_room", "stroller", "pet"]


class CarePlaceIn(BaseModel):
    place_key: str = Field("", max_length=80, description="source:source_id. 모르면 비움")
    place_name: str = Field(..., min_length=1, max_length=60)
    amenities: list[Amenity] = Field(default_factory=list, max_length=3, description="방문하며 확인된 시설")
    visit_count: int = Field(..., ge=1, le=10_000)
    last_visited_on: date | None = None

    @field_validator("place_name")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("장소 이름이 비었어요")
        return v

    @field_validator("amenities")
    @classmethod
    def _unique(cls, v: list) -> list:
        return list(dict.fromkeys(v))


class CarePlacesBody(BaseModel):
    places: list[CarePlaceIn] = Field(..., max_length=MAX_PLACES)


class CarePlaces(BaseModel):
    places: list[CarePlaceIn]


async def list_places(db: AsyncSession, user: User) -> list[CarePlace]:
    rows = await db.scalars(
        select(CarePlace)
        .where(CarePlace.user_id == user.id)
        .order_by(CarePlace.visit_count.desc(), CarePlace.last_visited_on.desc().nulls_last(), CarePlace.place_name)
    )
    return list(rows)


def _out(row: CarePlace) -> CarePlaceIn:
    return CarePlaceIn(place_key=row.place_key, place_name=row.place_name, amenities=row.amenities or [], visit_count=row.visit_count, last_visited_on=row.last_visited_on)


@router.get("/care-places", response_model=CarePlaces)
async def get_care_places(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """자주 가는 케어 장소. 많이 간 순 · 최근에 간 순."""
    return CarePlaces(places=[_out(r) for r in await list_places(db, user)])


@router.put("/care-places", response_model=CarePlaces)
async def put_care_places(body: CarePlacesBody, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """차가 고른 목록을 올린다. 이름이 같은 곳은 하나로 합친다(방문 수·시설은 큰 쪽·합친 쪽).

    여러 차가 각자 올려도 지워지지 않게 **합치기**로 동작한다: 이미 있는 곳은 방문 수를 큰 값으로, 시설은 합집합으로,
    마지막 방문은 늦은 날짜로 바꾼다. 계정 전체 목록은 많이 간 순으로 [MAX_PLACES]곳까지 남긴다.
    """
    existing = {r.place_name: r for r in await list_places(db, user)}
    for item in body.places:
        row = existing.get(item.place_name)
        if row is None:
            row = CarePlace(user_id=user.id, place_name=item.place_name, place_key=item.place_key, amenities=[], visit_count=0)
            db.add(row)
            existing[item.place_name] = row
        row.place_key = item.place_key or row.place_key
        row.amenities = [a for a in ("nursing_room", "stroller", "pet") if a in set(row.amenities or []) | set(item.amenities)]
        row.visit_count = max(row.visit_count or 0, item.visit_count)
        if item.last_visited_on is not None and (row.last_visited_on is None or item.last_visited_on > row.last_visited_on):
            row.last_visited_on = item.last_visited_on
        row.updated_at = utcnow()
    await db.flush()
    keep = await list_places(db, user)
    extra = [r.id for r in keep[MAX_PLACES:]]
    if extra:
        await db.execute(delete(CarePlace).where(CarePlace.id.in_(extra)))
    await db.commit()
    return CarePlaces(places=[_out(r) for r in (await list_places(db, user))])


@router.delete("/care-places", status_code=204)
async def delete_care_places(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    """목록을 비운다 (차의 '기록 지우기'와 함께 쓴다)."""
    await db.execute(delete(CarePlace).where(CarePlace.user_id == user.id))
    await db.commit()

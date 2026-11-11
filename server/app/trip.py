"""진행 중 코스 (Figma H03·H03-1·H04, #218).

차가 코스 안내를 시작하고, 장소에 도착하고, 다음 장소를 바꾸거나 한 곳을 더 붙이고, 귀가나 '여기서 코스 끝내기'로
마칠 때마다 지금 코스를 통째로 올린다(`PUT /me/trip`). 폰 웹 홈은 이것을 읽어 "코스 진행 중 · 1/3 장소"를 보여 준다
(Figma "바꾼 일정은 웹앱에도 바로 반영돼요"). 계정마다 한 개만 두고, 새 코스를 시작하면 덮어쓴다.

개인정보 최소화: 장소 이름·역할·예정 시각·머무는 시간·진행 상태만 받는다. 위치 좌표는 받지 않는다.
"""

from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, Response
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.db import utcnow
from app.models import CurrentTrip, User

router = APIRouter(prefix="/me", tags=["trip"])

MAX_STOPS = 12
HHMM = r"^([01]\d|2[0-3]):[0-5]\d$"

# fuel: 연료가 부족할 때 넣는 주유소·충전소 경유지(#215)
Role = Literal["meal", "outing", "cafe", "fuel", "home"]
# visited 도착함 · current 지금 가는 곳(또는 도착해 머무는 곳) · upcoming 아직 · skipped 건너뜀(코스를 일찍 끝냄)
StopState = Literal["visited", "current", "upcoming", "skipped"]


class TripStop(BaseModel):
    name: str = Field(..., min_length=1, max_length=60)
    role: Role
    arrive_at: str = Field(..., pattern=HHMM, description="예정 도착 HH:MM")
    stay_minutes: int = Field(0, ge=0, le=600)
    state: StopState

    @field_validator("name")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("장소 이름이 비었어요")
        return v


class Trip(BaseModel):
    title: str = Field(..., min_length=1, max_length=60, description="예: 덕수궁 나들이 코스")
    started_on: date
    departure_at: str = Field(..., pattern=HHMM)
    status: Literal["active", "finished"]
    # 끝난 방법: home 귀가를 골라 집 안내 중 · here 여기서 코스 끝내기. 진행 중이면 null
    ended_by: Literal["home", "here"] | None = None
    stops: list[TripStop] = Field(..., min_length=1, max_length=MAX_STOPS, description="귀가까지 포함한 순서")


@router.put("/trip", response_model=Trip)
async def put_trip(body: Trip, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """지금 코스를 올린다(덮어쓰기). 차가 바뀔 때마다 부른다."""
    row = await db.get(CurrentTrip, user.id)
    payload = body.model_dump(mode="json")
    if row is None:
        db.add(CurrentTrip(user_id=user.id, payload=payload, updated_at=utcnow()))
    else:
        row.payload = payload
        row.updated_at = utcnow()
    await db.commit()
    return body


async def load_trip(db: AsyncSession, user: User) -> Trip | None:
    row = await db.scalar(select(CurrentTrip).where(CurrentTrip.user_id == user.id))
    return Trip.model_validate(row.payload) if row else None


@router.get("/trip", response_model=Trip, responses={204: {"description": "올린 코스 없음"}})
async def get_trip(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    trip = await load_trip(db, user)
    return trip if trip else Response(status_code=204)


@router.delete("/trip", status_code=204)
async def delete_trip(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    await db.execute(delete(CurrentTrip).where(CurrentTrip.user_id == user.id))
    await db.commit()


def web_card(trip: Trip | None, today: date) -> dict | None:
    """폰 웹 홈 카드. 오늘 코스만 보여 준다(어제 것은 지난 일)."""
    if trip is None or trip.started_on != today:
        return None
    places = [s for s in trip.stops if s.role not in ("home", "fuel")]
    visited = sum(1 for s in places if s.state == "visited")
    current = next((i for i, s in enumerate(places) if s.state == "current"), None)
    home = next((s for s in trip.stops if s.role == "home"), None)
    if trip.status == "finished":
        status = "코스를 마쳤어요 · 귀가 안내 중" if trip.ended_by == "home" else "코스를 마쳤어요"
    else:
        number = (current + 1) if current is not None else visited
        status = f"코스 진행 중 · {number}/{len(places)} 장소"
    end = home.arrive_at if home else (places[-1].arrive_at if places else trip.departure_at)
    return {
        "title": trip.title,
        "status": status,
        "finished": trip.status == "finished",
        "span": f"{trip.departure_at} – {end}",
        "visited": visited,
        "stops": [
            {"time": s.arrive_at, "name": "귀가" if s.role == "home" else s.name, "stay": s.stay_minutes, "state": s.state}
            for s in trip.stops
            # 일찍 끝냈으면 귀가 줄은 뺀다(집으로 가지 않음)
            if not (s.role == "home" and trip.ended_by == "here")
        ],
    }

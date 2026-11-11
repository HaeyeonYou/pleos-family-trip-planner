"""폰 웹과 차 앱이 서로의 변경을 알아채는 가벼운 확인. 내용은 주지 않고 영역별 변경 표시(rev)만 준다.

양쪽이 몇 초마다 `GET /me/sync`를 불러 표시가 바뀐 영역만 다시 받으면 된다 (프리셋을 보냈는지·적용했는지, 수유·방문 기록, 아이·멤버, 연결된 차).
표시는 내용을 해시한 것이라 고쳐도, 지워도 바뀐다. 시각이 아니라 내용 기준이라 두 기기의 시계가 달라도 상관없다.
"""

import hashlib
import json

from fastapi import APIRouter, Depends, Response
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import service
from app.auth.deps import current_user, get_db
from app.models import CarePlace, Child, ClimatePreset, CurrentTrip, FeedingLog, OutingMember, User, VisitRecord

router = APIRouter(prefix="/me", tags=["sync"])

AREAS = ("presets", "feedings", "visits", "family", "cars", "trip")


class SyncRevisions(BaseModel):
    presets: str = Field(..., description="프리셋(가족 기본값·아이별 슬롯 3개): 보냄·적용 상태·적용 시점이 바뀌면 달라짐")
    feedings: str = Field(..., description="수유 기록: 추가·삭제되면 달라짐")
    visits: str = Field(..., description="방문 기록: 추가·확인·삭제되면 달라짐")
    family: str = Field(..., description="아이·기본 외출 멤버")
    cars: str = Field(..., description="로그인된 차 목록")
    trip: str = Field(..., description="진행 중 코스(#218): 차가 도착·변경·종료를 올리면 달라짐")
    all: str = Field(..., description="위 여섯 가지를 합친 표시. 이것이 같으면 아무것도 안 바뀐 것")


def _digest(rows: list) -> str:
    raw = json.dumps(rows, ensure_ascii=False, default=str, separators=(",", ":"))
    return hashlib.sha1(raw.encode()).hexdigest()[:12]


async def revisions(db: AsyncSession, user: User) -> SyncRevisions:
    """내 데이터의 영역별 변경 표시. 모두 작은 표라 매번 읽어 해시한다."""
    presets = (await db.execute(select(ClimatePreset.child_id, ClimatePreset.slot, ClimatePreset.id, ClimatePreset.sent_at, ClimatePreset.applied_at, ClimatePreset.apply_at).where(ClimatePreset.user_id == user.id).order_by(ClimatePreset.child_id.nulls_first(), ClimatePreset.slot))).all()
    feedings = (await db.execute(select(FeedingLog.id, FeedingLog.fed_at).where(FeedingLog.user_id == user.id).order_by(FeedingLog.fed_at.desc()).limit(50))).all()
    visits = (await db.execute(select(VisitRecord.id, VisitRecord.status, VisitRecord.mood).where(VisitRecord.user_id == user.id).order_by(VisitRecord.id))).all()
    # 자주 가는 케어 장소는 방문 기록에서 나오므로 visits 영역에 함께 넣는다
    care_places = (await db.execute(select(CarePlace.place_name, CarePlace.amenities, CarePlace.visit_count, CarePlace.last_visited_on).where(CarePlace.user_id == user.id).order_by(CarePlace.place_name))).all()
    children = (await db.execute(select(Child.id, Child.nickname, Child.birth_month, Child.birth_date, Child.rear_facing_seat, Child.feeding_type, Child.feed_interval_minutes, Child.nap_count).where(Child.user_id == user.id).order_by(Child.id))).all()
    members = (await db.execute(select(OutingMember.id, OutingMember.label, OutingMember.is_driver, OutingMember.is_default).where(OutingMember.user_id == user.id).order_by(OutingMember.id))).all()
    cars = [row.id for row in await service.car_sessions(db, user)]
    trip = await db.scalar(select(CurrentTrip.payload).where(CurrentTrip.user_id == user.id))

    parts = {
        "presets": _digest([list(r) for r in presets]),
        "feedings": _digest([list(r) for r in feedings]),
        "visits": _digest([list(r) for r in visits] + [list(r) for r in care_places]),
        "family": _digest([list(r) for r in children] + [list(r) for r in members]),
        "cars": _digest(sorted(str(c) for c in cars)),
        "trip": _digest(trip or {}),
    }
    return SyncRevisions(**parts, all=_digest([parts[a] for a in AREAS]))


@router.get("/sync", response_model=SyncRevisions)
async def sync(response: Response, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """변경 확인. 폰 웹(쿠키)과 차 앱(Bearer) 모두 쓴다. 몇 초마다 불러서 `all`이 달라진 영역만 다시 받으면 된다."""
    response.headers["Cache-Control"] = "no-store"
    return await revisions(db, user)

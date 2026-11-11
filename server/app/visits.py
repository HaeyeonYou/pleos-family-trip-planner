"""방문 기록과 경험맵. 사용자가 '차 안 기기에만 저장' 정책을 바꿔 서버에 저장한다.

개인정보 최소화: 장소 키·장소 이름·분야·장소 분류·날짜·머문 시간·아이 기분만 받는다.
위치 좌표, 도착·출발 시각, 아이 음성, 사진은 받지 않는다.
"""

import uuid
from collections import defaultdict
from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.family import _owned, today_kst
from app.course import jelly as jelly_rules
from app.course.jelly import judge
from app.models import CarePlace, CurrentTrip, User, VisitRecord
from app.schemas import Jelly, Theme, VisitSummary

router = APIRouter(prefix="/me", tags=["visits"])

Status = Literal["confirmed", "pending", "dismissed"]
Mood = Literal["excited", "normal", "sleepy", "bad"]
MOOD_LABELS = {"excited": "신났어요", "normal": "평범", "sleepy": "졸려요", "bad": "별로"}
THEME_LABELS = {"nature": "자연", "experience": "체험", "exhibition": "전시", "play": "놀이", "pet": "반려동물"}
MAX_BULK = 200


class VisitIn(BaseModel):
    place_key: str = Field("", max_length=80, description="source:source_id. 모르면 비움")
    place_name: str = Field(..., min_length=1, max_length=60)
    theme: Theme | None = None
    category: str = Field("", max_length=120, description="장소 분류 원문 (코스 장소의 category). 젤리 12분류 판정 근거")
    visited_on: date
    stayed_minutes: int | None = Field(None, ge=0, le=1440)
    status: Status = "confirmed"
    mood: Mood | None = None


class VisitOut(VisitIn):
    id: uuid.UUID
    jelly: Jelly = Field(description="그로우 젤리 12분류. 서버가 이름·분류·분야로 정한다 (분류가 없는 예전 기록도 조회할 때 정함)")


class BulkItem(VisitIn):
    id: uuid.UUID | None = Field(None, description="클라이언트가 정한 UUID. 있으면 다시 보내도 중복되지 않는다")


class BulkBody(BaseModel):
    visits: list[BulkItem] = Field(..., max_length=MAX_BULK)


class VisitPatch(BaseModel):
    status: Status | None = None
    mood: Mood | None = None


def jelly_of(row: VisitRecord) -> Jelly:
    """방문 기록의 젤리 12분류. 저장하지 않고 조회할 때마다 정해서 판정 규칙이 바뀌면 예전 기록에도 반영된다."""
    return judge(row.place_name, row.category or "", row.theme)


def out(row: VisitRecord) -> VisitOut:
    return VisitOut(
        id=row.id,
        place_key=row.place_key,
        place_name=row.place_name,
        theme=Theme(row.theme) if row.theme else None,
        category=row.category or "",
        jelly=jelly_of(row),
        visited_on=row.visited_on,
        stayed_minutes=row.stayed_minutes,
        status=row.status,
        mood=row.mood,
    )


def _apply(row: VisitRecord, body: VisitIn) -> None:
    row.place_key = body.place_key
    row.place_name = body.place_name.strip()
    row.theme = body.theme.value if body.theme else None
    row.category = body.category.strip()
    row.visited_on = body.visited_on
    row.stayed_minutes = body.stayed_minutes
    row.status = body.status
    row.mood = body.mood


async def upsert(db: AsyncSession, user: User, visit_id: uuid.UUID, body: VisitIn) -> VisitRecord:
    row = await _owned(db, VisitRecord, visit_id, user)
    if row is None:
        row = VisitRecord(id=visit_id, user_id=user.id)
        db.add(row)
    _apply(row, body)
    return row


@router.put("/visits/{visit_id}", response_model=VisitOut)
async def put_visit(visit_id: uuid.UUID, body: VisitIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    row = await upsert(db, user, visit_id, body)
    await db.commit()
    return out(row)


@router.post("/visits/bulk")
async def bulk_visits(body: BulkBody, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> dict:
    """차 안 기기에 쌓인 방문 기록을 한꺼번에 올린다(처음 연결할 때). id가 같으면 덮어써서 여러 번 보내도 안전하다."""
    for item in body.visits:
        await upsert(db, user, item.id or uuid.uuid4(), item)
    await db.commit()
    return {"saved": len(body.visits)}


@router.get("/visits", response_model=list[VisitOut])
async def list_visits(
    status: Status | None = None, limit: int = Query(50, ge=1, le=500), user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
):
    stmt = select(VisitRecord).where(VisitRecord.user_id == user.id)
    if status:
        stmt = stmt.where(VisitRecord.status == status)
    rows = await db.scalars(stmt.order_by(VisitRecord.visited_on.desc(), VisitRecord.created_at.desc()).limit(limit))
    return [out(r) for r in rows]


@router.patch("/visits/{visit_id}", response_model=VisitOut)
async def patch_visit(visit_id: uuid.UUID, body: VisitPatch, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """방문 확인(확정·안 갔어요)과 아이 기분을 고른다."""
    row = await _owned(db, VisitRecord, visit_id, user)
    if row is None:
        raise HTTPException(404, "방문 기록을 찾지 못했어요")
    if body.status:
        row.status = body.status
    if body.mood:
        row.mood = body.mood
    await db.commit()
    return out(row)


@router.delete("/visits/{visit_id}", status_code=204)
async def delete_visit(visit_id: uuid.UUID, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    row = await _owned(db, VisitRecord, visit_id, user)
    if row is None:
        raise HTTPException(404, "방문 기록을 찾지 못했어요")
    await db.delete(row)
    await db.commit()


@router.delete("/visits", status_code=204)
async def delete_all_visits(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    """내 방문 기록 전체 삭제. 방문 기록에서 나온 자주 가는 케어 장소와 진행 중 코스(#218)도 함께 지운다."""
    await db.execute(delete(VisitRecord).where(VisitRecord.user_id == user.id))
    await db.execute(delete(CarePlace).where(CarePlace.user_id == user.id))
    await db.execute(delete(CurrentTrip).where(CurrentTrip.user_id == user.id))
    await db.commit()


async def confirmed_summary(db: AsyncSession, user: User, limit: int = 200) -> list[VisitSummary]:
    rows = await db.scalars(
        select(VisitRecord)
        .where(VisitRecord.user_id == user.id, VisitRecord.status == "confirmed")
        .order_by(VisitRecord.visited_on.desc(), VisitRecord.created_at.desc())
        .limit(limit)
    )
    return [VisitSummary(place_key=r.place_key, theme=Theme(r.theme) if r.theme else None, date=r.visited_on.isoformat()) for r in rows]


@router.get("/visits/summary", response_model=list[VisitSummary])
async def visits_summary(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """확정된 방문의 요약. `POST /course/suggest`의 `visits`(PR #90)에 그대로 넣을 수 있는 모양이다."""
    return await confirmed_summary(db, user)


class MapPlace(BaseModel):
    place_key: str
    place_name: str
    visits: int
    last_visited_on: date
    last_mood: Mood | None = None


class MapTheme(BaseModel):
    theme: Theme
    label: str
    visits: int
    places: list[MapPlace]


class MapJelly(BaseModel):
    jelly: Jelly
    label: str = Field(description="분류 이름 (예: 자연·숲)")
    character: str = Field(description="젤리 캐릭터 (예: 곰)")
    stage: str = Field(description="none / drop(방울 1~3) / puff(몽글 4~9) / born(탄생 10+)")
    stage_name: str = Field(description="곰 방울 / 곰 몽글 / 푸근한 숲 곰형")
    experiences: int = Field(description="10분 이상 머문 확정 방문 수 (머문 시간을 모르면 셈)")
    first_on: date = Field(description="처음 경험한 날")
    places: list[MapPlace]


class ExperienceMap(BaseModel):
    visit_count: int
    place_count: int
    themes: list[MapTheme] = Field(description="방문이 있는 분야. 방문이 많은 순")
    empty_themes: list[Theme] = Field(description="아직 가 보지 않은 분야('빈 가지')")
    this_month_visits: int
    new_themes_this_month: list[Theme]
    pending_count: int = Field(description="다녀왔는지 확인을 기다리는 방문 수")
    jellies: list[MapJelly] = Field(default_factory=list, description="젤리 12분류별 경험 (#186). 경험이 많은 순")
    empty_jellies: list[Jelly] = Field(default_factory=list, description="아직 경험이 없는 분류 ('비어 있는 별')")


def _map_places(visits_by_place: dict[str, list[VisitRecord]]) -> list[MapPlace]:
    items = []
    for visits in visits_by_place.values():
        last = max(visits, key=lambda v: (v.visited_on, v.created_at))
        items.append(MapPlace(place_key=last.place_key, place_name=last.place_name, visits=len(visits), last_visited_on=last.visited_on, last_mood=last.mood))
    items.sort(key=lambda p: (-p.visits, -p.last_visited_on.toordinal()))
    return items


def is_experience(row: VisitRecord) -> bool:
    return row.status == "confirmed" and (row.stayed_minutes is None or row.stayed_minutes >= jelly_rules.MIN_STAY_MINUTES)


def build_jellies(rows: list[VisitRecord]) -> tuple[list[MapJelly], list[Jelly]]:
    """젤리 12분류로 묶는다. 같은 장소는 장소 키(없으면 이름)로 묶는다."""
    by_kind: dict[Jelly, dict[str, list[VisitRecord]]] = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if is_experience(r):
            by_kind[jelly_of(r)][r.place_key or r.place_name].append(r)
    result = []
    for kind, places in by_kind.items():
        count = sum(len(v) for v in places.values())
        label, character, _ = jelly_rules.LABELS[kind]
        result.append(
            MapJelly(jelly=kind, label=label, character=character, stage=jelly_rules.stage(count),
                     stage_name=jelly_rules.stage_name(kind, count), experiences=count,
                     first_on=min(v.visited_on for vs in places.values() for v in vs), places=_map_places(places))
        )
    order = list(Jelly)
    result.sort(key=lambda j: (-j.experiences, order.index(j.jelly)))
    return result, [k for k in Jelly if k not in by_kind]


def build_map(rows: list[VisitRecord], today: date) -> ExperienceMap:
    """확정된 방문으로 경험맵을 만든다. 같은 장소는 장소 키(없으면 이름)로 묶는다."""
    confirmed = [r for r in rows if r.status == "confirmed"]
    by_theme: dict[str, dict[str, list[VisitRecord]]] = defaultdict(lambda: defaultdict(list))
    first_seen: dict[str, date] = {}
    for r in sorted(confirmed, key=lambda r: r.visited_on):
        theme = r.theme or "other"
        by_theme[theme][r.place_key or r.place_name].append(r)
        first_seen.setdefault(theme, r.visited_on)
    themes = []
    for theme, places in by_theme.items():
        if theme == "other":
            continue  # 분야를 모르는 방문은 장소 수·방문 수에만 센다
        items = _map_places(places)
        themes.append(MapTheme(theme=Theme(theme), label=THEME_LABELS[theme], visits=sum(p.visits for p in items), places=items))
    themes.sort(key=lambda t: -t.visits)
    seen = {t.theme for t in themes}
    month = (today.year, today.month)
    jellies, empty_jellies = build_jellies(rows)
    return ExperienceMap(
        jellies=jellies,
        empty_jellies=empty_jellies,
        visit_count=len(confirmed),
        place_count=len({(r.place_key or r.place_name) for r in confirmed}),
        themes=themes,
        empty_themes=[t for t in Theme if t not in seen],
        this_month_visits=sum((r.visited_on.year, r.visited_on.month) == month for r in confirmed),
        new_themes_this_month=[Theme(t) for t, d in first_seen.items() if t != "other" and (d.year, d.month) == month],
        pending_count=sum(r.status == "pending" for r in rows),
    )


@router.get("/experience-map", response_model=ExperienceMap)
async def experience_map(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    rows = list(await db.scalars(select(VisitRecord).where(VisitRecord.user_id == user.id).limit(5000)))
    return build_map(rows, today_kst())

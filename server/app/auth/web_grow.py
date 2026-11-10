"""폰 웹 그로우(경험맵·장소·방문 확인)와 추천 코스 받기. 방문 기록은 서버에 저장한다(사용자 결정)."""

import logging
import uuid
from datetime import date, datetime, timedelta

import httpx
from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app import family, visits
from app.auth.deps import get_db, optional_web_user
from app.auth.web import _login_redirect, templates
from app.config import get_settings
from app.course import jelly as jelly_rules
from app.course.planner import NoCourseError
from app.models import User, VisitRecord
from app.schemas import CourseRequest, Jelly, LatLng, Theme, TripConditions

log = logging.getLogger("web")
router = APIRouter(prefix="/m", include_in_schema=False)

_ROLES = {"meal": "식사", "outing": "나들이", "cafe": "카페", "home": "귀가"}
_AMENITIES = {"stroller": "유모차", "nursing_room": "수유실", "kids_chair": "아기의자", "kids_zone": "키즈존", "parking": "주차"}
_NEEDS = [("nursing_room", "수유실"), ("stroller", "유모차 길"), ("kids_chair", "아기의자"), ("parking", "주차")]
_MINUTES = [(20, "20분"), (40, "40분"), (60, "60분")]


def _since(days: int, today: date) -> str:
    if days < 1:
        return "오늘"
    if days == 1:
        return "어제"
    if days < 7:
        return f"{days}일 전"
    if days < 60:
        return f"{days // 7}주 전"
    return f"{days // 30}개월 전"


def _place_views(places: list[visits.MapPlace], today: date) -> list[dict]:
    return [
        {
            "name": p.place_name,
            "visits": p.visits,
            "since": _since((today - p.last_visited_on).days, today),
            "last": p.last_visited_on.strftime("%m/%d"),
            "mood": visits.MOOD_LABELS.get(p.last_mood or ""),
        }
        for p in places
    ]


async def _all_visits(db: AsyncSession, user: User) -> list[VisitRecord]:
    return list(await db.scalars(select(VisitRecord).where(VisitRecord.user_id == user.id).limit(5000)))


def _pending(rows: list[VisitRecord]) -> list[VisitRecord]:
    return sorted((r for r in rows if r.status == "pending"), key=lambda r: r.visited_on, reverse=True)


def _pending_view(r: VisitRecord, today: date) -> dict:
    return {"id": r.id, "name": r.place_name, "date": r.visited_on.strftime("%m/%d"), "since": _since((today - r.visited_on).days, today)}


# G01 경험 우주 (젤리 B안 12분류, #186)


def _course_link(kind: Jelly) -> str:
    """이 분류로 코스 받기: 5분야로 옮길 수 있으면 그 분야, 아니면 조건 없이."""
    theme = jelly_rules.TO_THEME.get(kind)
    return f"/m/course?theme={theme.value}" if theme else "/m/course"


@router.get("/grow", response_class=HTMLResponse)
async def grow_page(request: Request, notice: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/grow")
    today = family.today_kst()
    rows = await _all_visits(db, user)
    emap = visits.build_map(rows, today)
    children = await family.list_children(db, user)
    stars = [
        {
            "key": j.jelly.value,
            "label": j.label,
            "stage_name": j.stage_name,
            "experiences": j.experiences,
            "places": _place_views(j.places, today)[:4],
            "more": max(0, len(j.places) - 4),
        }
        for j in emap.jellies
    ]
    empty = [
        {"key": k.value, "label": jelly_rules.LABELS[k][0], "character": jelly_rules.LABELS[k][1], "href": _course_link(k)}
        for k in emap.empty_jellies
    ]
    context = {
        "title": "그로우",
        "tab": "grow",
        "notice": "방문을 기록했어요." if notice == "saved" else ("기록하지 않았어요." if notice == "dismissed" else ""),
        "who": f"{children[0].nickname}의 경험 우주" if children else "우리 가족의 경험 우주",
        "map": emap,
        "stars": stars,
        "experience_count": sum(j.experiences for j in emap.jellies),
        "empty": empty,
        "pending": [_pending_view(r, today) for r in _pending(rows)],
        "new_labels": [j.label for j in emap.jellies if (j.first_on.year, j.first_on.month) == (today.year, today.month)],
        "has_visits": emap.visit_count > 0,
    }
    return templates.TemplateResponse(request, "grow.html", context)


# G02 분류·장소


@router.get("/grow/jelly/{kind}", response_class=HTMLResponse)
async def grow_jelly(request: Request, kind: str, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect(f"/m/grow/jelly/{kind}")
    emap = visits.build_map(await _all_visits(db, user), family.today_kst())
    found = next((j for j in emap.jellies if j.jelly.value == kind), None)
    if found is None:
        return RedirectResponse("/m/grow", status_code=303)
    context = {
        "title": found.label,
        "label": found.label,
        "stage_name": found.stage_name,
        "experiences": found.experiences,
        "places": _place_views(found.places, family.today_kst()),
        "course_href": _course_link(found.jelly),
    }
    return templates.TemplateResponse(request, "grow_theme.html", context)


@router.get("/grow/theme/{theme}")
async def grow_theme(theme: str) -> RedirectResponse:
    """예전 5분야 주소. 경험 우주로 보낸다."""
    return RedirectResponse("/m/grow", status_code=303)


# G03 방문 확인


async def _owned_visit(db: AsyncSession, user: User, visit_id: uuid.UUID) -> VisitRecord | None:
    row = await db.get(VisitRecord, visit_id)
    return row if row is not None and row.user_id == user.id else None


@router.get("/grow/visit/{visit_id}", response_class=HTMLResponse)
async def visit_confirm_page(request: Request, visit_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect(f"/m/grow/visit/{visit_id}")
    row = await _owned_visit(db, user, visit_id)
    if row is None or row.status != "pending":
        return RedirectResponse("/m/grow", status_code=303)
    context = {
        "title": "방문 확인",
        "v": _pending_view(row, family.today_kst()),
        "stayed": f"{row.stayed_minutes // 60}시간 {row.stayed_minutes % 60}분".replace("0시간 ", "").replace(" 0분", "") if row.stayed_minutes else "",
        "moods": list(visits.MOOD_LABELS.items()),
    }
    return templates.TemplateResponse(request, "visit_confirm.html", context)


@router.post("/grow/visit/{visit_id}")
async def visit_confirm(
    visit_id: uuid.UUID,
    action: str = Form("confirm"),
    mood: str = Form(""),
    user: User | None = Depends(optional_web_user),
    db: AsyncSession = Depends(get_db),
):
    if user is None:
        return _login_redirect(f"/m/grow/visit/{visit_id}")
    row = await _owned_visit(db, user, visit_id)
    if row is not None and row.status == "pending":
        if action == "dismiss":
            row.status = "dismissed"
        else:
            row.status = "confirmed"
            row.mood = mood if mood in visits.MOOD_LABELS else None
        await db.commit()
    return RedirectResponse(f"/m/grow?notice={'dismissed' if action == 'dismiss' else 'saved'}", status_code=303)


# H03 추천 코스 받기 · H04 코스 상세 (일정 저장은 하지 않는다)


def _make_planner(request: Request):
    """main.get_planner를 쓴다 (테스트의 dependency_overrides도 따른다). 순환 import를 피하려고 함수 안에서 가져온다."""
    from app.main import app as main_app
    from app.main import get_planner

    override = main_app.dependency_overrides.get(get_planner)
    return override() if override else get_planner(request, get_settings())


def _option_view(option) -> dict:
    stops = []
    for s in option.course.stops:
        stops.append(
            {
                "time": s.arrive_at,
                "role": _ROLES.get(s.role.value, s.role.value),
                "name": "우리 집" if s.role.value == "home" else s.place.name,
                "stay": s.stay_minutes,
                "chips": [label for key, label in _AMENITIES.items() if s.place.amenities.get(key) == "available"],
                "parking": len(s.parking),
                "home": s.role.value == "home",
            }
        )
    hours, mins = divmod(option.total_minutes, 60)
    return {
        "name": option.name,
        "summary": option.summary,
        "total": f"{hours}시간 {mins}분" if hours and mins else (f"{hours}시간" if hours else f"{mins}분"),
        "first_drive": option.first_drive_minutes,
        "indoor": option.indoor,
        "departure": option.course.departure_at,
        "return_at": option.course.return_at,
        "stops": stops,
        "warnings": option.course.warnings,
    }


@router.get("/course", response_class=HTMLResponse)
async def course_page(
    request: Request,
    lat: float | None = None,
    lng: float | None = None,
    theme: str = "",
    minutes: int | None = None,
    user: User | None = Depends(optional_web_user),
    db: AsyncSession = Depends(get_db),
):
    if user is None:
        return _login_redirect("/m/course")
    children = await family.list_children(db, user)
    needs = [n for n in request.query_params.getlist("need") if n in dict(_NEEDS)]
    indoor = request.query_params.get("indoor") == "1"
    theme = theme if theme in visits.THEME_LABELS else ""
    # 아이 정보로 처음 값을 정한다: 멀미가 있으면 이동 시간을 짧게, 유모차를 쓰면 유모차 길을 미리 고른다 (조건을 보낸 뒤에는 사용자가 고른 그대로)
    hints = []
    if minutes is None:
        sick = [c.nickname for c in children if c.motion_sickness]
        minutes = 20 if sick else 40
        if sick:
            hints.append(f"{family.subject(sick[0])} 멀미가 있어 편도 이동 시간을 20분으로 시작했어요. 바꿔도 돼요.")
    if lat is None and not needs and any(c.stroller_use for c in children):
        needs = ["stroller"]
        hints.append("유모차를 쓰는 가족이라 「유모차 길」을 미리 골라 두었어요.")
    context = {
        "hints": hints,
        "title": "추천 코스 받기",
        "lat": lat,
        "lng": lng,
        "needs": needs,
        "need_choices": _NEEDS,
        "indoor": indoor,
        "minutes": minutes,
        "minute_choices": _MINUTES,
        "theme": theme,
        "theme_label": visits.THEME_LABELS.get(theme, ""),
        "options": [],
        "error": "",
    }
    if lat is None or lng is None:
        return templates.TemplateResponse(request, "course.html", context)

    members = await family.list_members(db, user)
    try:
        conditions = TripConditions(
            child_age_months=[family.age_months(c.birth_month) for c in children],
            adults=sum(1 for m in members if m.is_default) or None,
            themes=[Theme(theme)] if theme else [],
            indoor_only=indoor,
            max_drive_minutes=minutes if minutes in dict(_MINUTES) else 40,
            needs=needs,
        )
        req = CourseRequest(
            conditions=conditions,
            origin=LatLng(latitude=lat, longitude=lng),
            visits=await visits.confirmed_summary(db, user),
        )
    except ValidationError:
        context["error"] = "위치나 조건을 다시 확인해 주세요."
        return templates.TemplateResponse(request, "course.html", context, status_code=400)
    try:
        planner = _make_planner(request)
        result = await run_in_threadpool(planner.suggest, req)
        context["options"] = [_option_view(o) for o in result.options]
    except HTTPException as e:
        context["error"] = "코스 추천 서버 설정이 아직 없어요. 잠시 후 다시 시도해 주세요." if e.status_code == 503 else str(e.detail)
    except NoCourseError:
        context["error"] = "조건에 맞는 코스를 찾지 못했어요. 이동 시간을 늘리거나 꼭 필요한 것을 줄여 보세요."
    except httpx.HTTPError as e:
        log.warning("웹 코스 추천 외부 API 오류: %r", e)
        context["error"] = "외부 장소 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
    return templates.TemplateResponse(request, "course.html", context, status_code=200 if not context["error"] else 502)

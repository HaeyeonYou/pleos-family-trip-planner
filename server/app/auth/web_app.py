"""폰 웹 로그인 후 화면: 홈·케어·그로우·MY와 공조 프리셋. 로그인·가입·차 승인은 web.py."""

import uuid
from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import ValidationError
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app import family, feedings, presets
from app.auth import service
from app.auth.deps import WEB_COOKIE, get_db, optional_web_user
from app.auth.web import _login_redirect, templates
from app.db import utcnow
from app.models import Child, FeedingLog, OutingMember, User, VisitRecord

router = APIRouter(prefix="/m", include_in_schema=False)

KST = timezone(timedelta(hours=9))
_DAYS = ["일", "월", "화", "수", "목", "금", "토"]


def _greeting(hour: int) -> str:
    if 5 <= hour < 11:
        return "좋은 아침이에요"
    if 11 <= hour < 17:
        return "좋은 오후예요"
    if 17 <= hour < 22:
        return "좋은 저녁이에요"
    return "편안한 밤이에요"


def _week(today) -> list[dict]:
    """오늘이 든 주(일~토). 일정은 서버에 저장하지 않아 날짜만 보여 준다."""
    sunday = today - timedelta(days=(today.weekday() + 1) % 7)
    days = [sunday + timedelta(days=i) for i in range(7)]
    return [{"label": _DAYS[i], "day": d.day, "today": d == today} for i, d in enumerate(days)]


def _ago(moment: datetime, now: datetime) -> str:
    seconds = max(0, int((now - moment).total_seconds()))
    if seconds < 60:
        return "방금 전"
    if seconds < 3600:
        return f"{seconds // 60}분 전"
    if seconds < 86400:
        return f"{seconds // 3600}시간 전"
    return f"{seconds // 86400}일 전"


def _hm(moment: datetime) -> str:
    return moment.astimezone(KST).strftime("%H:%M")


def _duration(minutes: int) -> str:
    hours, mins = divmod(abs(minutes), 60)
    return f"{hours}시간 {mins}분" if hours and mins else (f"{hours}시간" if hours else f"{mins}분")


# 공조 프리셋 (#62, 슬롯 3개)

_SEATS = [("driver_seat_heat", "운전석 열선"), ("passenger_seat_heat", "조수석 열선"), ("steering_wheel_heat", "핸들 열선")]
_TOGGLES = [("power", "공조 켜기"), ("ac", "에어컨"), ("air_cleaner", "공기청정")]
_VOLUMES = [("", "그대로"), ("10", "최소"), ("30", "낮게"), ("50", "보통"), ("70", "크게")]


def _summary(s: presets.ClimateSettings) -> str:
    parts = [f"{s.temperature:g}℃", f"풍량 {s.fan_speed}단"]
    if s.media_volume is not None:
        parts.append(f"음량 {s.media_volume}%")
    parts += [f"{label} {getattr(s, key)}단" for key, label in _SEATS if getattr(s, key)]
    parts += [label for key, label in _TOGGLES[1:] if getattr(s, key)]
    return " · ".join(parts)


def _preset_cards(states: list[presets.PresetState]) -> list[dict]:
    cards = []
    for st in states:
        if not st.sent:
            status, tone = "보내지 않음", "gray"
        elif st.pending:
            status, tone = "차 대기 중", "warn"
        else:
            status, tone = "차에서 적용함", "ok"
        cards.append(
            {"slot": st.slot, "label": st.label, "when": st.when, "name": st.settings.name, "summary": _summary(st.settings), "status": status, "tone": tone, "sent": st.sent}
        )
    return cards


def _preset_status(state: presets.PresetState) -> str:
    if not state.sent:
        return ""
    if state.pending:
        return "차로 보냈어요. 시동 후 차 앱을 켜면 확인할 수 있어요."
    return "차에서 적용했어요."


def _car_views(sessions) -> list[dict]:
    now = utcnow()
    return [{"id": c.id, "name": c.device_name or "Pleos Connect 차량", "last_used": _ago(c.last_used_at, now)} for c in sessions]


# 수유

_KIND_FOR_FEEDING_TYPE = {"breast": "breast", "formula": "formula", "mixed": "formula", "solid": "solid", "toddler": "solid"}


async def _feeding_card(db: AsyncSession, user: User) -> dict:
    """홈·케어의 수유 카드. 기록이 없으면 정직한 빈 상태."""
    status = await feedings.status_for(db, user)
    card = {
        "has_last": status.last_fed_at is not None,
        "interval_text": f"{status.interval_minutes / 60:g}시간 간격",
        "nickname": status.nickname,
    }
    if status.last_fed_at:
        left = status.minutes_left
        total = status.interval_minutes
        card |= {
            "last": _hm(status.last_fed_at),
            "next": _hm(status.next_due_at),
            "left_text": f"{_duration(left)} 남음" if left >= 0 else f"{_duration(left)} 지났어요",
            "overdue": status.overdue,
            "progress": max(0, min(100, round((1 - left / total) * 100))),
        }
    return card


# 홈


async def _home_visits(db: AsyncSession, user: User, today: date) -> dict:
    """홈의 '다녀온 곳 확인하기'와 '다시 가볼까요?'. 방문 기록이 없으면 둘 다 비운다."""
    rows = list(await db.scalars(select(VisitRecord).where(VisitRecord.user_id == user.id).limit(5000)))
    pending = sorted((r for r in rows if r.status == "pending"), key=lambda r: r.visited_on, reverse=True)
    confirmed = [r for r in rows if r.status == "confirmed"]
    revisit = None
    if confirmed:
        # 많이 다녀온 곳 중 2주 넘게 안 간 곳 하나
        groups: dict[str, list[VisitRecord]] = {}
        for r in confirmed:
            groups.setdefault(r.place_key or r.place_name, []).append(r)
        old = [v for v in groups.values() if (today - max(x.visited_on for x in v)).days >= 14]
        if old:
            best = max(old, key=lambda v: (len(v), max(x.visited_on for x in v).toordinal()))
            last = max(best, key=lambda x: x.visited_on)
            days = (today - last.visited_on).days
            revisit = {"name": last.place_name, "visits": len(best), "theme": last.theme or "", "since": f"{days // 7}주 전" if days < 60 else f"{days // 30}개월 전"}
    views = [{"id": r.id, "name": r.place_name, "since": "어제" if (today - r.visited_on).days == 1 else f"{(today - r.visited_on).days}일 전" if (today - r.visited_on).days > 1 else "오늘"} for r in pending]
    return {"pending_visits": views, "revisit": revisit}


@router.get("/", response_class=HTMLResponse)
async def home(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/")
    now = datetime.now(KST)
    sent = [c for c in _preset_cards(await presets.states(db, user)) if c["sent"]]
    context = {
        "title": "홈",
        "tab": "home",
        "user": user,
        "greeting": _greeting(now.hour),
        "week": _week(now.date()),
        "presets": sent,
        "cars": _car_views(await service.car_sessions(db, user)),
        "feeding": await _feeding_card(db, user),
    }
    context |= await _home_visits(db, user, now.date())
    return templates.TemplateResponse(request, "home.html", context)


# 케어


@router.get("/care", response_class=HTMLResponse)
async def care_page(request: Request, notice: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/care")
    context = {
        "notice": "수유 시각을 기록했어요." if notice == "fed" else "",
        "title": "케어",
        "tab": "care",
        "feeding": await _feeding_card(db, user),
        "presets": _preset_cards(await presets.states(db, user)),
    }
    return templates.TemplateResponse(request, "care.html", context)


async def _feed_context(db: AsyncSession, user: User, **extra) -> dict:
    day_start = datetime.now(KST).replace(hour=0, minute=0, second=0, microsecond=0)
    rows = await db.scalars(
        select(FeedingLog).where(FeedingLog.user_id == user.id, FeedingLog.fed_at >= day_start).order_by(FeedingLog.fed_at.desc())
    )
    today = [
        {"id": r.id, "time": _hm(r.fed_at), "kind": feedings.KIND_LABELS[r.kind], "amount": r.amount_ml}
        for r in rows
    ]
    children = await family.list_children(db, user)
    default_kind = _KIND_FOR_FEEDING_TYPE.get(children[0].feeding_type or "", "formula") if children else "formula"
    now = datetime.now(KST)
    context = {
        "title": "수유 기록",
        "user": user,
        "now": now.strftime("%H:%M"),
        "today": today,
        "kinds": list(feedings.KIND_LABELS.items()),
        "kind": default_kind,
        "feeding": await _feeding_card(db, user),
    }
    return context | extra


@router.get("/care/feed", response_class=HTMLResponse)
async def feed_page(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/care/feed")
    return templates.TemplateResponse(request, "feed.html", await _feed_context(db, user))


@router.post("/care/feed", response_class=HTMLResponse)
async def feed_save(
    request: Request,
    mode: str = Form("now"),
    time: str = Form(""),
    kind: str = Form(""),
    amount: str = Form(""),
    user: User | None = Depends(optional_web_user),
    db: AsyncSession = Depends(get_db),
):
    """「지금 수유했어요」(mode=now)와 「시간 선택」 저장(mode=time)."""
    if user is None:
        return _login_redirect("/m/care/feed")
    now = datetime.now(KST)
    fed_at = now
    error = ""
    if mode == "time":
        try:
            hour, minute = (int(x) for x in time.split(":"))
            fed_at = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
            if fed_at > now + timedelta(minutes=10):
                fed_at -= timedelta(days=1)  # 지금보다 늦은 시각이면 어제 기록으로 본다
        except ValueError:
            error = "수유한 시각을 다시 확인해 주세요"
    if not kind:
        children = await family.list_children(db, user)
        kind = _KIND_FOR_FEEDING_TYPE.get(children[0].feeding_type or "", "formula") if children else "formula"
    if not error:
        try:
            body = feedings.FeedingIn(fed_at=fed_at, kind=kind, amount_ml=int(amount) if amount.strip() else None, source="web")
            await feedings.record(db, user, uuid.uuid4(), body)
        except (ValidationError, ValueError):
            error = "입력한 값을 다시 확인해 주세요"
    if error:
        context = await _feed_context(db, user, error=error, kind=kind)
        return templates.TemplateResponse(request, "feed.html", context, status_code=400)
    return RedirectResponse("/m/care?notice=fed", status_code=303)


@router.post("/care/feed/{feeding_id}/delete")
async def feed_delete(feeding_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/care/feed")
    row = await db.get(FeedingLog, feeding_id)
    if row is not None and row.user_id == user.id:
        await db.delete(row)
        await db.commit()
    return RedirectResponse("/m/care/feed", status_code=303)


# 공조 프리셋 편집


def _preset_page_context(slot: str, settings: dict, state: presets.PresetState, **extra) -> dict:
    meta = presets.SLOTS[slot]
    context = {
        "title": meta["label"],
        "slot": slot,
        "meta": meta,
        "s": settings,
        "seats": _SEATS,
        "toggles": _TOGGLES,
        "volumes": _VOLUMES,
        "volume": "" if settings.get("media_volume") is None else str(settings["media_volume"]),
        "status": _preset_status(state),
        # 차에서 적용하면 '적용됨' 표시를 바꾸려고 주기적으로 확인한다. 보낸 직후 화면은 POST 결과라 GET 주소로 다시 연다.
        "sync_poll": True,
        "reload_to": f"/m/preset?slot={slot}",
    }
    return context | extra


async def _preset_advice(db: AsyncSession, user: User, slot: str) -> list[str]:
    """아이 케어 정보로 정한 권장값 안내 (C03). 더위·추위는 온도 ±1℃, 소리 예민은 낮잠 음량."""
    children = await family.list_children(db, user)
    if not children:
        return []
    child, defaults, advice = children[0], presets.SLOTS[slot]["defaults"], []
    shift = {"hot": -1, "cold": 1}.get(child.heat_sensitivity or "", 0)
    if shift:
        trait = "더위를 타는" if shift < 0 else "추위를 타는"
        advice.append(f"{family.topic(child.nickname)} {trait} 편이라 {defaults.temperature + shift:g}℃를 권장해요.")
    if slot == "nap" and child.noise_sensitivity == "sensitive":
        advice.append(f"{family.topic(child.nickname)} 소리에 예민해서 미디어 음량을 「최소」로 권장해요.")
    return advice


async def _slot_state(db: AsyncSession, user: User, slot: str) -> presets.PresetState:
    return next(st for st in await presets.states(db, user) if st.slot == slot)


@router.get("/preset", response_class=HTMLResponse)
async def preset_page(
    request: Request, slot: str = "boarding", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)
):
    if user is None:
        return _login_redirect(f"/m/preset?slot={slot}" if slot in presets.SLOTS and slot != "boarding" else "/m/preset")
    slot = slot if slot in presets.SLOTS else "boarding"
    state = await _slot_state(db, user, slot)
    context = _preset_page_context(slot, state.settings.model_dump(), state, advice=await _preset_advice(db, user, slot))
    return templates.TemplateResponse(request, "preset.html", context)


@router.post("/preset", response_class=HTMLResponse)
async def send_preset(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/preset")
    form = await request.form()
    slot = form.get("slot") if form.get("slot") in presets.SLOTS else "boarding"
    values = {key: form.get(key) or 0 for key, _ in _SEATS} | {"name": form.get("name") or presets.SLOTS[slot]["defaults"].name}
    values |= {"temperature": form.get("temperature"), "fan_speed": form.get("fan_speed"), "media_volume": form.get("media_volume") or None}
    # 체크박스는 꺼져 있으면 아예 오지 않는다.
    values |= {key: key in form for key, _ in _TOGGLES}
    try:
        settings = presets.ClimateSettings(**values)
    except ValidationError:
        state = await _slot_state(db, user, slot)
        context = _preset_page_context(slot, state.settings.model_dump(), state, status="값을 다시 확인해 주세요", error=True)
        return templates.TemplateResponse(request, "preset.html", context, status_code=400)
    await presets.save(db, user, settings, slot)
    state = await _slot_state(db, user, slot)
    context = _preset_page_context(slot, settings.model_dump(), state, advice=await _preset_advice(db, user, slot))
    return templates.TemplateResponse(request, "preset.html", context)


# MY


def _child_view(c) -> dict:
    out = family.child_out(c)
    bits = []
    if out.feeding_type:
        bits.append(f"{family.FEEDING_LABELS[out.feeding_type].split(' ')[0]} 수유")
    if out.feed_interval_minutes:
        bits.append(f"{out.feed_interval_minutes / 60:g}시간 간격")
    if out.nap_count is not None:
        bits.append(f"낮잠 {out.nap_count}회")
    return {"id": out.id, "nickname": out.nickname, "age": f"{out.age_months}개월", "born": out.birth_date.strftime("%Y.%m.%d") if out.birth_date else out.birth_month.replace("-", "."), "detail": " · ".join(bits)}


async def _my_context(db: AsyncSession, user: User, notice: str = "", **extra) -> dict:
    joined = user.created_at.astimezone(KST).strftime("%Y.%m.%d") if user.created_at.tzinfo else user.created_at.strftime("%Y.%m.%d")
    members = await family.list_members(db, user)
    return {
        "title": "MY",
        "tab": "my",
        "user": user,
        "cars": _car_views(await service.car_sessions(db, user)),
        "joined": joined,
        "notice": notice,
        "children": [_child_view(c) for c in await family.list_children(db, user)],
        "members": [m for m in members if m.is_default],
        "frequent": [m for m in members if not m.is_default],
        **extra,
    }


_NOTICES = {
    "disconnected": "차 연결을 해제했어요. 차 화면은 곧 로그아웃돼요.",
    "child_saved": "아이 정보를 저장했어요.",
    "child_deleted": "아이 정보를 지웠어요.",
    "member_saved": "멤버를 저장했어요.",
    "member_error": "멤버를 저장하지 못했어요. 호칭을 20자 안으로 적어 주세요(12명까지).",
    "cleared": "기록을 지웠어요.",
}


@router.get("/my", response_class=HTMLResponse)
async def my_page(request: Request, notice: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/my")
    return templates.TemplateResponse(request, "my.html", await _my_context(db, user, _NOTICES.get(notice, "")))


@router.post("/my/cars/{session_id}/disconnect")
async def disconnect_car(session_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/my")
    await service.disconnect_car(db, user, session_id)
    return RedirectResponse("/m/my?notice=disconnected", status_code=303)


# 아이 정보 (M01-1)

_FEEDING_CHOICES = list(family.FEEDING_LABELS.items())
_INTERVALS = [(120, "2시간"), (150, "2.5시간"), (180, "3시간"), (240, "4시간")]


def _tri(value) -> str:
    return "" if value is None else ("1" if value else "0")


def _child_values(child=None) -> dict:
    """폼에 채울 값(모두 글자). 아이가 없으면 빈 값."""
    if child is None:
        return {"nickname": "", "birth_month": "", "birth_date": "", "temperament": [], "feeding_type": "", "feed_interval_minutes": "", "nap_count": "",
                "nap_times": "", "nap_in_car": "", "heat_sensitivity": "", "motion_sickness": "", "noise_sensitivity": "", "stroller_use": "", "rear_facing_seat": ""}
    out = family.child_out(child)
    return {
        "nickname": out.nickname,
        "birth_month": out.birth_month,
        "birth_date": out.birth_date.isoformat() if out.birth_date else "",
        "temperament": list(out.temperament),
        "feeding_type": out.feeding_type or "",
        "feed_interval_minutes": out.feed_interval_minutes or "",
        "nap_count": "" if out.nap_count is None else out.nap_count,
        "nap_times": out.nap_times or "",
        "nap_in_car": _tri(out.nap_in_car),
        "heat_sensitivity": out.heat_sensitivity or "",
        "motion_sickness": _tri(out.motion_sickness),
        "noise_sensitivity": out.noise_sensitivity or "",
        "stroller_use": _tri(out.stroller_use),
        "rear_facing_seat": _tri(out.rear_facing_seat),
    }


def _child_choices() -> dict:
    return {
        "tags": list(family.TEMPERAMENTS.items()),
        "feeding_choices": _FEEDING_CHOICES,
        "intervals": _INTERVALS,
        "naps": [0, 1, 2, 3],
        "heat_choices": list(family.HEAT_LABELS.items()),
        "noise_choices": list(family.NOISE_LABELS.items()),
        "max_month": datetime.now(KST).strftime("%Y-%m"),
        "max_date": datetime.now(KST).strftime("%Y-%m-%d"),
    }


def _child_form_context(child=None, **extra) -> dict:
    values = _child_values(child)
    context = {
        "title": "아이 정보" if child is None else f"{values['nickname']} 케어 정보",
        "v": values,
        "child_id": child.id if child is not None else None,
    } | _child_choices()
    return context | extra


def parse_child_form(form, base: "family.ChildOut | None") -> "family.ChildIn":
    """폼 → ChildIn. 폼에 `sections`(basic·care·extra)가 있으면 그 칸만 바꾸고 나머지는 base 값을 그대로 둔다 (온보딩이 두 단계로 나눠 받는다)."""
    sections = set(form.getlist("sections")) or {"basic", "care", "extra"}
    data = base.model_dump(exclude={"id", "age_months"}) if base is not None else {}

    def number(key: str):
        value = (form.get(key) or "").strip()
        return int(value) if value else None

    def tri(key: str):
        return {"1": True, "0": False}.get(form.get(key) or "")

    if "basic" in sections:
        # 생년월일(일까지)이 기본이고, 날짜를 안 고른 예전 아이는 숨은 칸의 태어난 달을 그대로 보낸다
        birth_date = (form.get("birth_date") or "").strip() or None
        data |= {
            "nickname": form.get("nickname") or "",
            "birth_date": birth_date,
            "birth_month": None if birth_date else (form.get("birth_month") or None),
            "temperament": [t for t in form.getlist("temperament") if t in family.TEMPERAMENTS],
        }
    if "care" in sections:
        data |= {
            "feeding_type": form.get("feeding_type") or None,
            "feed_interval_minutes": number("feed_interval_minutes"),
            "nap_count": number("nap_count"),
            "nap_times": form.get("nap_times") or None,
            "nap_in_car": tri("nap_in_car"),
        }
    if "extra" in sections:
        data |= {
            "heat_sensitivity": form.get("heat_sensitivity") or None,
            "motion_sickness": tri("motion_sickness"),
            "noise_sensitivity": form.get("noise_sensitivity") or None,
            "stroller_use": tri("stroller_use"),
            "rear_facing_seat": tri("rear_facing_seat"),
        }
    return family.ChildIn(**data)


def form_values(form, fallback: dict) -> dict:
    """오류로 다시 보여 줄 때 사용자가 입력한 값을 그대로 채운다."""
    values = {k: (form.get(k) or "") if k != "temperament" else form.getlist(k) for k in fallback}
    for key in ("nap_count",):
        values[key] = int(values[key]) if str(values[key]).isdigit() else ""
    if str(values["feed_interval_minutes"]).isdigit():
        values["feed_interval_minutes"] = int(values["feed_interval_minutes"])
    return values


async def _owned_child(db: AsyncSession, user: User, child_id: uuid.UUID):
    child = await db.get(Child, child_id)
    return child if child is not None and child.user_id == user.id else None


@router.get("/my/children/new", response_class=HTMLResponse)
async def child_new(request: Request, user: User | None = Depends(optional_web_user)):
    if user is None:
        return _login_redirect("/m/my/children/new")
    return templates.TemplateResponse(request, "child_form.html", _child_form_context())


@router.get("/my/children/{child_id}", response_class=HTMLResponse)
async def child_edit(request: Request, child_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect(f"/m/my/children/{child_id}")
    child = await _owned_child(db, user, child_id)
    if child is None:
        return RedirectResponse("/m/my", status_code=303)
    return templates.TemplateResponse(request, "child_form.html", _child_form_context(child))


async def _save_child(request: Request, db: AsyncSession, user: User, child_id: uuid.UUID, existing):
    form = await request.form()
    try:
        body = parse_child_form(form, family.child_out(existing) if existing is not None else None)
        await family.save_child(db, user, child_id, body)
    except (ValidationError, ValueError):
        context = _child_form_context(existing, error="입력한 값을 다시 확인해 주세요. 생년월일은 오늘까지의 날짜로 골라 주세요.")
        context["v"] = form_values(form, context["v"])
        return templates.TemplateResponse(request, "child_form.html", context, status_code=400)
    except HTTPException as e:  # 아이 수 제한
        return templates.TemplateResponse(request, "child_form.html", _child_form_context(existing, error=e.detail), status_code=400)
    return RedirectResponse("/m/my?notice=child_saved", status_code=303)


@router.post("/my/children/new", response_class=HTMLResponse)
async def child_create(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/my/children/new")
    return await _save_child(request, db, user, uuid.uuid4(), None)


@router.post("/my/children/{child_id}", response_class=HTMLResponse)
async def child_update(request: Request, child_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect(f"/m/my/children/{child_id}")
    child = await _owned_child(db, user, child_id)
    if child is None:
        return RedirectResponse("/m/my", status_code=303)
    return await _save_child(request, db, user, child_id, child)


@router.post("/my/children/{child_id}/delete")
async def child_delete(child_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/my")
    child = await _owned_child(db, user, child_id)
    if child is not None:
        await db.delete(child)
        await db.commit()
    return RedirectResponse("/m/my?notice=child_deleted", status_code=303)


# 기본 외출 멤버


@router.post("/my/members")
async def member_add(
    label: str = Form(""),
    is_driver: str = Form(""),
    is_default: str = Form("1"),
    user: User | None = Depends(optional_web_user),
    db: AsyncSession = Depends(get_db),
):
    if user is None:
        return _login_redirect("/m/my")
    try:
        body = family.MemberIn(label=label, is_driver=bool(is_driver), is_default=is_default == "1")
        await family.save_member(db, user, uuid.uuid4(), body)
    except (ValidationError, HTTPException):
        return RedirectResponse("/m/my?notice=member_error", status_code=303)
    return RedirectResponse("/m/my?notice=member_saved", status_code=303)


@router.post("/my/members/{member_id}/delete")
async def member_delete(member_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/my")
    row = await db.get(OutingMember, member_id)
    if row is not None and row.user_id == user.id:
        await db.delete(row)
        await db.commit()
    return RedirectResponse("/m/my?notice=member_saved", status_code=303)


# 기록 삭제


_CLEARABLE = {
    "visits": ("방문 기록", VisitRecord, "경험맵에 쌓인 방문 기록이 모두 지워져요."),
    "feedings": ("수유 기록", FeedingLog, "수유 시각 기록이 모두 지워져요."),
}


@router.get("/my/clear/{what}", response_class=HTMLResponse)
async def clear_page(request: Request, what: str, user: User | None = Depends(optional_web_user)):
    if user is None:
        return _login_redirect(f"/m/my/clear/{what}")
    if what not in _CLEARABLE:
        return RedirectResponse("/m/my", status_code=303)
    label, _, text = _CLEARABLE[what]
    return templates.TemplateResponse(request, "clear_data.html", {"title": f"{label} 삭제", "what": what, "label": label, "text": text})


@router.post("/my/clear/{what}")
async def clear_data(what: str, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect(f"/m/my/clear/{what}")
    if what in _CLEARABLE:
        model = _CLEARABLE[what][1]
        await db.execute(delete(model).where(model.user_id == user.id))
        await db.commit()
    return RedirectResponse("/m/my?notice=cleared", status_code=303)


# 계정 삭제


@router.get("/my/delete", response_class=HTMLResponse)
async def delete_account_page(request: Request, user: User | None = Depends(optional_web_user)):
    if user is None:
        return _login_redirect("/m/my/delete")
    return templates.TemplateResponse(request, "delete_account.html", {"title": "계정 삭제", "user": user})


@router.post("/my/delete", response_class=HTMLResponse)
async def delete_account(
    request: Request, password: str = Form(...), user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)
):
    if user is None:
        return _login_redirect("/m/my/delete")
    try:
        await service.delete_account(db, user, password)
    except service.AuthError as e:
        context = {"title": "계정 삭제", "user": user, "error": str(e)}
        return templates.TemplateResponse(request, "delete_account.html", context, status_code=400)
    response = RedirectResponse("/m/login", status_code=303)
    response.delete_cookie(WEB_COOKIE)
    return response

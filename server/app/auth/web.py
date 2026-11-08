"""폰 브라우저로 여는 모바일 웹 (차 QR → 회원가입·로그인 → 차 로그인 승인). 로그인 상태는 쿠키 세션."""

import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.ext.asyncio import AsyncSession

from pydantic import ValidationError

from app import presets
from app.auth import service
from app.auth.deps import WEB_COOKIE, get_db, optional_web_user
from app.config import Settings, get_settings
from app.db import utcnow
from app.models import User

router = APIRouter(prefix="/m", include_in_schema=False)
templates = Jinja2Templates(directory=Path(__file__).resolve().parents[1] / "templates")


def _safe_next(next_url: str) -> str:
    # 다른 사이트로 넘기지 않게 우리 경로만 허용한다.
    return next_url if next_url.startswith("/m/") else "/m/"


def _login_redirect(next_url: str) -> RedirectResponse:
    return RedirectResponse(f"/m/login?next={quote(next_url)}", status_code=303)


def _with_session(response: RedirectResponse, token: str, settings: Settings) -> RedirectResponse:
    response.set_cookie(
        WEB_COOKIE, token, max_age=settings.session_days * 86400, httponly=True, samesite="lax", secure=settings.secure_cookies
    )
    return response


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


def _preset_view(preset) -> dict | None:
    """홈·케어 카드에 보여 줄 프리셋 요약. 값은 서버에 저장된 그대로다."""
    if preset is None:
        return None
    s = presets.ClimateSettings(**preset.settings)
    parts = [f"{s.temperature:g}℃", f"풍량 {s.fan_speed}단"]
    parts += [f"{label} {getattr(s, key)}단" for key, label in _SEATS if getattr(s, key)]
    parts += [label for key, label in _TOGGLES[1:] if getattr(s, key)]
    waiting = preset.applied_at is None
    return {
        "name": s.name,
        "summary": " · ".join(parts),
        "status": "차 대기 중" if waiting else "차에서 적용함",
        "tone": "warn" if waiting else "ok",
        "detail": _preset_status(preset),
    }


def _car_views(sessions) -> list[dict]:
    now = utcnow()
    return [{"id": c.id, "name": c.device_name or "Pleos Connect 차량", "last_used": _ago(c.last_used_at, now)} for c in sessions]


@router.get("/", response_class=HTMLResponse)
async def home(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/")
    now = datetime.now(KST)
    context = {
        "title": "홈",
        "tab": "home",
        "user": user,
        "greeting": _greeting(now.hour),
        "week": _week(now.date()),
        "preset": _preset_view(await presets.current(db, user)),
        "cars": _car_views(await service.car_sessions(db, user)),
    }
    return templates.TemplateResponse(request, "home.html", context)


@router.get("/care", response_class=HTMLResponse)
async def care_page(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/care")
    context = {"title": "케어", "tab": "care", "preset": _preset_view(await presets.current(db, user))}
    return templates.TemplateResponse(request, "care.html", context)


@router.get("/grow", response_class=HTMLResponse)
async def grow_page(request: Request, user: User | None = Depends(optional_web_user)):
    if user is None:
        return _login_redirect("/m/grow")
    return templates.TemplateResponse(request, "grow.html", {"title": "그로우", "tab": "grow"})


def _my_context(user: User, cars: list, notice: str = "") -> dict:
    joined = user.created_at.astimezone(KST).strftime("%Y.%m.%d") if user.created_at.tzinfo else user.created_at.strftime("%Y.%m.%d")
    return {"title": "MY", "tab": "my", "user": user, "cars": cars, "joined": joined, "notice": notice}


@router.get("/my", response_class=HTMLResponse)
async def my_page(
    request: Request, notice: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)
):
    if user is None:
        return _login_redirect("/m/my")
    notices = {"disconnected": "차 연결을 해제했어요. 차 화면은 곧 로그아웃돼요."}
    context = _my_context(user, _car_views(await service.car_sessions(db, user)), notices.get(notice, ""))
    return templates.TemplateResponse(request, "my.html", context)


@router.post("/my/cars/{session_id}/disconnect")
async def disconnect_car(
    session_id: uuid.UUID, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)
):
    if user is None:
        return _login_redirect("/m/my")
    await service.disconnect_car(db, user, session_id)
    return RedirectResponse("/m/my?notice=disconnected", status_code=303)


@router.get("/my/delete", response_class=HTMLResponse)
async def delete_account_page(request: Request, user: User | None = Depends(optional_web_user)):
    if user is None:
        return _login_redirect("/m/my/delete")
    return templates.TemplateResponse(request, "delete_account.html", {"title": "계정 삭제", "user": user})


@router.post("/my/delete", response_class=HTMLResponse)
async def delete_account(
    request: Request,
    password: str = Form(...),
    user: User | None = Depends(optional_web_user),
    db: AsyncSession = Depends(get_db),
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


@router.get("/login", response_class=HTMLResponse)
async def login_page(request: Request, next: str = "/m/"):
    # /m/care(케어 탭)와 헷갈리지 않게 경로를 정확히 비교한다.
    lead = "차 화면의 QR로 들어오셨다면 로그인하면 차도 같이 로그인돼요." if next.split("?")[0] == "/m/car" else ""
    return templates.TemplateResponse(request, "login.html", {"title": "로그인", "next": next, "lead": lead, "login_id": ""})


@router.post("/login", response_class=HTMLResponse)
async def login(
    request: Request,
    login_id: str = Form(...),
    password: str = Form(...),
    next: str = Form("/m/"),
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
):
    try:
        user = await service.authenticate(db, login_id, password)
    except service.AuthError as e:
        context = {"title": "로그인", "next": next, "lead": "", "login_id": login_id, "error": str(e)}
        return templates.TemplateResponse(request, "login.html", context, status_code=400)
    token = await service.create_session(db, settings, user, "web", request.headers.get("user-agent", "")[:60])
    return _with_session(RedirectResponse(_safe_next(next), status_code=303), token, settings)


@router.get("/signup", response_class=HTMLResponse)
async def signup_page(request: Request, next: str = "/m/"):
    return templates.TemplateResponse(request, "signup.html", {"title": "회원가입", "next": next, "login_id": "", "display_name": ""})


@router.post("/signup", response_class=HTMLResponse)
async def signup(
    request: Request,
    login_id: str = Form(...),
    password: str = Form(...),
    display_name: str = Form(""),
    next: str = Form("/m/"),
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
):
    try:
        user = await service.signup(db, login_id, password, display_name)
    except service.AuthError as e:
        context = {"title": "회원가입", "next": next, "login_id": login_id, "display_name": display_name, "error": str(e)}
        return templates.TemplateResponse(request, "signup.html", context, status_code=400)
    token = await service.create_session(db, settings, user, "web", request.headers.get("user-agent", "")[:60])
    return _with_session(RedirectResponse(_safe_next(next), status_code=303), token, settings)


@router.get("/logout")
async def logout(request: Request, next: str = "/m/", db: AsyncSession = Depends(get_db)):
    token = request.cookies.get(WEB_COOKIE)
    if token:
        await service.revoke(db, token)
    response = _login_redirect(_safe_next(next))
    response.delete_cookie(WEB_COOKIE)
    return response


@router.get("/car", response_class=HTMLResponse)
async def car_page(request: Request, code: str, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    """차 화면 QR이 여는 주소. 로그인 전이면 로그인(또는 가입) 후 이 화면으로 돌아온다."""
    next_url = f"/m/car?code={quote(code)}"
    if user is None:
        return _login_redirect(next_url)
    context = {"title": "차에서 로그인", "user": user, "code": code, "next": next_url}
    try:
        login = await service.pending_car_login(db, code)
        context["device"] = login.device_name
    except service.AuthError as e:
        context["error"] = str(e)
    return templates.TemplateResponse(request, "car_approve.html", context)


@router.post("/car", response_class=HTMLResponse)
async def approve_car(
    request: Request, code: str = Form(...), user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)
):
    if user is None:
        return _login_redirect(f"/m/car?code={quote(code)}")
    try:
        await service.approve_car_login(db, code, user)
    except service.AuthError as e:
        return templates.TemplateResponse(request, "message.html", {"title": "차에서 로그인", "message": str(e)}, status_code=400)
    message = "차에서 로그인했어요. 차 화면을 확인해 주세요."
    return templates.TemplateResponse(request, "message.html", {"title": "로그인 완료", "message": message, "link": "/m/", "link_text": "처음으로"})


# 공조 프리셋 (#62)

_SEATS = [("driver_seat_heat", "운전석 열선"), ("passenger_seat_heat", "조수석 열선"), ("steering_wheel_heat", "핸들 열선")]
_TOGGLES = [("power", "공조 켜기"), ("ac", "에어컨"), ("air_cleaner", "공기청정")]


def _preset_status(preset) -> str:
    if preset is None:
        return ""
    if preset.applied_at is None:
        return "차로 보냈어요. 시동 후 차 앱을 켜면 확인할 수 있어요."
    return "차에서 적용했어요."


@router.get("/preset", response_class=HTMLResponse)
async def preset_page(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/preset")
    preset = await presets.current(db, user)
    settings = presets.ClimateSettings(**preset.settings) if preset else presets.ClimateSettings()
    context = {"title": "차 공조 미리 설정", "s": settings.model_dump(), "seats": _SEATS, "toggles": _TOGGLES, "status": _preset_status(preset)}
    return templates.TemplateResponse(request, "preset.html", context)


@router.post("/preset", response_class=HTMLResponse)
async def send_preset(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    if user is None:
        return _login_redirect("/m/preset")
    form = await request.form()
    values = {key: form.get(key) for key, _ in _SEATS} | {"name": form.get("name") or "우리 가족 설정"}
    values |= {"temperature": form.get("temperature"), "fan_speed": form.get("fan_speed")}
    # 체크박스는 꺼져 있으면 아예 오지 않는다.
    values |= {key: key in form for key, _ in _TOGGLES}
    context = {"title": "차 공조 미리 설정", "seats": _SEATS, "toggles": _TOGGLES}
    try:
        settings = presets.ClimateSettings(**values)
    except ValidationError:
        context |= {"s": presets.ClimateSettings().model_dump(), "status": "값을 다시 확인해 주세요", "error": True}
        return templates.TemplateResponse(request, "preset.html", context, status_code=400)
    await presets.save(db, user, settings)
    context |= {"s": settings.model_dump(), "status": "차로 보냈어요. 시동 후 차 앱을 켜면 확인할 수 있어요."}
    return templates.TemplateResponse(request, "preset.html", context)

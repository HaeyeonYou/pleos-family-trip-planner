"""폰 브라우저로 여는 모바일 웹 (차 QR → 회원가입·로그인 → 차 로그인 승인). 로그인 상태는 쿠키 세션."""

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


@router.get("/", response_class=HTMLResponse)
async def home(request: Request, user: User | None = Depends(optional_web_user)):
    if user is None:
        return _login_redirect("/m/")
    return templates.TemplateResponse(request, "home.html", {"title": "오늘, 어디 가지?", "user": user})


@router.get("/login", response_class=HTMLResponse)
async def login_page(request: Request, next: str = "/m/"):
    lead = "차 화면의 QR로 들어오셨다면 로그인하면 차도 같이 로그인돼요." if next.startswith("/m/car") else ""
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

"""폰 브라우저로 여는 모바일 웹 (차 QR → 회원가입·로그인 → 차 로그인 승인). 로그인 상태는 쿠키 세션."""

from pathlib import Path
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.ext.asyncio import AsyncSession

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

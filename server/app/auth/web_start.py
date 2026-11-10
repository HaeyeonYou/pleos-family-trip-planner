"""온보딩 (Figma S01~S03): 차 화면 QR로 들어와 → 가입 → 아이 정보 1/2 → 케어 정보 2/2 → 차 연결 → 홈. 모든 단계는 건너뛸 수 있다."""

import re
import uuid
from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import ValidationError
from sqlalchemy.ext.asyncio import AsyncSession

from app import family
from app.auth import service
from app.auth.deps import get_db, optional_web_user
from app.auth.web import _login_redirect, templates
from app.auth.web_app import _child_choices, _child_form_context, _child_values, form_values, parse_child_form
from app.models import User

router = APIRouter(prefix="/m", include_in_schema=False)

_CODE = re.compile(r"^[A-Za-z0-9_-]{4,16}$")


def clean_code(code: str | None) -> str:
    """QR 코드는 주소에 다시 넣기 때문에 모양이 맞는 것만 받는다."""
    return code if code and _CODE.match(code) else ""


def with_code(path: str, code: str) -> str:
    return f"{path}?code={quote(code)}" if code else path


async def _device(db: AsyncSession, code: str) -> str | None:
    """QR로 요청한 차 이름. 코드가 없거나 만료·사용된 것이면 None."""
    if not code:
        return None
    try:
        return (await service.pending_car_login(db, code)).device_name or "차"
    except service.AuthError:
        return None


@router.get("/start", response_class=HTMLResponse)
async def start_page(request: Request, code: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    """S01 QR 랜딩. 이미 로그인했으면 바로 차 연결(또는 홈)으로 보낸다."""
    code = clean_code(code)
    if user is not None:
        return RedirectResponse(with_code("/m/car", code) if code else "/m/", status_code=303)
    context = {
        "title": "시작하기",
        "device": await _device(db, code),
        "signup_next": with_code("/m/start/child", code),
        "login_next": with_code("/m/car", code) if code else "/m/",
    }
    return templates.TemplateResponse(request, "start.html", context)


@router.get("/privacy", response_class=HTMLResponse)
async def privacy_page(request: Request):
    return templates.TemplateResponse(request, "privacy.html", {"title": "개인정보 수집·이용 안내"})


async def _first_child(db: AsyncSession, user: User):
    children = await family.list_children(db, user)
    return children[0] if children else None


def _step_context(step: int, child, code: str, **extra) -> dict:
    name = child.nickname if child is not None else ""
    context = {
        "title": "아이 정보" if step == 1 else "케어 정보",
        "step": step,
        "code": code,
        "child_name": name,
        # 건너뛰면 다음 단계로 (마지막 단계는 차 연결 또는 홈)
        "skip_to": with_code("/m/start/care", code) if step == 1 else (with_code("/m/start/car", code) if code else "/m/"),
    } | _child_choices()
    context["v"] = _child_values(child)
    return context | extra


@router.get("/start/child", response_class=HTMLResponse)
async def child_step(request: Request, code: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    code = clean_code(code)
    if user is None:
        return _login_redirect(with_code("/m/start/child", code))
    return templates.TemplateResponse(request, "onboard_child.html", _step_context(1, await _first_child(db, user), code))


@router.post("/start/child", response_class=HTMLResponse)
async def child_step_save(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    form = await request.form()
    code = clean_code(form.get("code"))
    if user is None:
        return _login_redirect(with_code("/m/start/child", code))
    existing = await _first_child(db, user)
    try:
        body = parse_child_form(form, family.child_out(existing) if existing is not None else None)
        await family.save_child(db, user, existing.id if existing is not None else uuid.uuid4(), body)
    except (ValidationError, ValueError, HTTPException):
        context = _step_context(1, existing, code, error="이름과 생년월일을 다시 확인해 주세요. 생년월일은 오늘까지의 날짜로 골라 주세요.")
        context["v"] = form_values(form, context["v"])
        return templates.TemplateResponse(request, "onboard_child.html", context, status_code=400)
    return RedirectResponse(with_code("/m/start/care", code), status_code=303)


@router.get("/start/care", response_class=HTMLResponse)
async def care_step(request: Request, code: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    code = clean_code(code)
    if user is None:
        return _login_redirect(with_code("/m/start/care", code))
    child = await _first_child(db, user)
    if child is None:  # 아이 정보부터
        return RedirectResponse(with_code("/m/start/child", code), status_code=303)
    return templates.TemplateResponse(request, "onboard_care.html", _step_context(2, child, code))


@router.post("/start/care", response_class=HTMLResponse)
async def care_step_save(request: Request, user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    form = await request.form()
    code = clean_code(form.get("code"))
    if user is None:
        return _login_redirect(with_code("/m/start/care", code))
    child = await _first_child(db, user)
    if child is None:
        return RedirectResponse(with_code("/m/start/child", code), status_code=303)
    try:
        await family.save_child(db, user, child.id, parse_child_form(form, family.child_out(child)))
    except (ValidationError, ValueError):
        context = _step_context(2, child, code, error="입력한 값을 다시 확인해 주세요.")
        context["v"] = form_values(form, context["v"])
        return templates.TemplateResponse(request, "onboard_care.html", context, status_code=400)
    return RedirectResponse(with_code("/m/start/car", code) if code else "/m/", status_code=303)


@router.get("/start/car", response_class=HTMLResponse)
async def car_step(request: Request, code: str = "", user: User | None = Depends(optional_web_user), db: AsyncSession = Depends(get_db)):
    """S03 차 연결. QR로 들어왔다면 그 차를 이 계정에 연결한다."""
    code = clean_code(code)
    if user is None:
        return _login_redirect(with_code("/m/start/car", code))
    device = await _device(db, code)
    if device is None:
        return RedirectResponse("/m/", status_code=303)
    return templates.TemplateResponse(request, "onboard_car.html", {"title": "차와 연결", "device": device, "code": code})

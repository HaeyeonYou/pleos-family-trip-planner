import logging
import time
import uuid
from contextlib import asynccontextmanager
from functools import lru_cache
from pathlib import Path

import httpx
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.staticfiles import StaticFiles

from app import care, care_places, family, feedings, places, presets, sync, trip, visits, voice, weather
from app.course import starts
from app.auth import api as auth_api
from app.auth import web as auth_web
from app.auth import web_app, web_grow, web_start
from app.config import Settings, get_settings
from app.db import Database
from app.logs import setup_logging
from app.course.planner import CoursePlanner, NoCourseError
from app.sources.parking import DbParkingSource
from app.parsing import get_parser
from app.schemas import (
    CourseRequest,
    CourseResponse,
    FuelRequest,
    FuelStation,
    ParseRequest,
    ParseResponse,
    ScheduleRequest,
    SuggestResponse,
)
from app.sources.kakao import KakaoClient
from app.sources.public_data import PublicData
from app.sources.tour import TourApiClient

setup_logging()
log = logging.getLogger("app")



@asynccontextmanager
async def lifespan(app: FastAPI):
    # 연결 풀은 서버 프로세스 하나에 하나. 요청마다 풀에서 연결을 빌려 쓴다.
    app.state.db = Database(get_settings())
    yield
    await app.state.db.close()


app = FastAPI(title="오늘, 어디 가지? API", version="0.1.0", lifespan=lifespan)
app.include_router(auth_api.router)
app.include_router(auth_web.router)
app.include_router(web_app.router)
app.include_router(web_grow.router)
app.include_router(web_start.router)
app.include_router(presets.router)
app.include_router(weather.router)
app.include_router(starts.router)
app.include_router(care.router)
app.include_router(voice.router)
app.include_router(places.router)
app.include_router(family.router)
app.include_router(feedings.router)
app.include_router(visits.router)
app.include_router(care_places.router)
app.include_router(trip.router)
app.include_router(sync.router)
app.mount("/m/assets", StaticFiles(directory=Path(__file__).parent / "static"), name="m-assets")  # 폰 웹 CSS


@app.middleware("http")
async def log_requests(request: Request, call_next):
    """요청마다 한 줄. 같은 요청의 다른 로그와 맞춰 보도록 짧은 id를 붙인다."""
    rid = uuid.uuid4().hex[:6]
    started = time.perf_counter()
    log.info("[%s] → %s %s", rid, request.method, request.url.path)
    response = await call_next(request)
    log.info("[%s] ← %s %s (%.0fms)", rid, response.status_code, request.url.path, (time.perf_counter() - started) * 1000)
    return response


def _conditions_summary(c) -> str:
    """로그용 조건 요약. 원문 문장은 남기지 않는다."""
    parts = []
    if c.child_age_months:
        parts.append("아이 " + ",".join(f"{m}개월" for m in c.child_age_months))
    elif c.child_ages:
        parts.append("아이 " + ",".join(f"{a}살" for a in c.child_ages))
    for name, value in (("테마", c.themes), ("편의", c.needs), ("관심", c.interests)):
        if value:
            parts.append(f"{name} " + ",".join(getattr(v, "value", v) for v in value))
    if c.with_pet:
        parts.append("반려견")
    if c.indoor_only:
        parts.append("실내만")
    if c.return_by_hour is not None:
        parts.append(f"{c.return_by_hour}시 귀가")
    return " · ".join(parts) or "조건 없음"


@app.get("/health")
def health(settings: Settings = Depends(get_settings)) -> dict:
    return {
        "status": "ok",
        "parser": settings.parser,
        "demo_mode": settings.demo_mode,
        "keys": {
            "data_go_kr": bool(settings.data_go_kr_key),
            "kakao": bool(settings.kakao_rest_api_key),
            "kma": bool(settings.kma_api_hub_key),
            "anthropic": bool(settings.anthropic_api_key),
        },
    }


@app.post("/parse", response_model=ParseResponse)
def parse(req: ParseRequest, settings: Settings = Depends(get_settings)) -> ParseResponse:
    parser = get_parser(settings)
    conditions = parser.parse(req.text)
    used = getattr(parser, "last_used", parser.name)
    log.info("해석(%s, %d자): %s", used, len(req.text), _conditions_summary(conditions))
    return ParseResponse(conditions=conditions, parser=used)


@lru_cache
def _sources(data_go_kr_key: str, kakao_key: str) -> tuple[TourApiClient, KakaoClient]:
    # 캐시를 요청 사이에 유지하려고 클라이언트를 재사용한다.
    return TourApiClient(data_go_kr_key), KakaoClient(kakao_key)


@lru_cache
def _public(data_go_kr_key: str) -> PublicData:
    return PublicData(data_go_kr_key)


def get_planner(request: Request, settings: Settings = Depends(get_settings)) -> CoursePlanner:
    if not settings.data_go_kr_key or not settings.kakao_rest_api_key:
        raise HTTPException(503, "DATA_GO_KR_KEY, KAKAO_REST_API_KEY가 필요합니다")
    tour, kakao = _sources(settings.data_go_kr_key, settings.kakao_rest_api_key)
    return CoursePlanner(tour, kakao, public=_public(settings.data_go_kr_key), parking=DbParkingSource(request.app.state.db))


@app.post("/course", response_model=CourseResponse)
def course(req: CourseRequest, planner: CoursePlanner = Depends(get_planner)) -> CourseResponse:
    log.info("코스 요청: %s · 출발 %.3f,%.3f", _conditions_summary(req.conditions), req.origin.latitude, req.origin.longitude)
    try:
        return planner.plan(req)
    except NoCourseError as e:
        raise HTTPException(404, str(e)) from e
    except httpx.HTTPError as e:
        log.warning("외부 API 오류: %r", e)
        raise HTTPException(502, f"외부 API 오류: {type(e).__name__}") from e


@app.post("/course/schedule", response_model=CourseResponse)
def schedule(req: ScheduleRequest, planner: CoursePlanner = Depends(get_planner)) -> CourseResponse:
    """장소를 바꾸거나 순서를 고친 코스의 시간표를 다시 계산한다."""
    log.info("재계산 요청: 장소 %d곳, 근처 후보=%s", len(req.stops), req.suggest_nearby)
    try:
        return planner.reschedule(req)
    except NoCourseError as e:
        raise HTTPException(404, str(e)) from e
    except httpx.HTTPError as e:
        log.warning("외부 API 오류: %r", e)
        raise HTTPException(502, f"외부 API 오류: {type(e).__name__}") from e


@app.post("/course/fuel", response_model=FuelStation)
def fuel_station(req: FuelRequest, planner: CoursePlanner = Depends(get_planner)) -> FuelStation:
    """홈 연료 타일 [주유소 찾기] (#215): 근처(다음 지점이 있으면 그 길에서 덜 돌아가는) 주유소·충전소 1곳. 없으면 404."""
    station = planner.fuel_station(req)
    if station is None:
        raise HTTPException(404, "근처에서 충전소를 찾지 못했어요" if req.ev else "근처에서 주유소를 찾지 못했어요")
    log.info("주유소 찾기(ev=%s): %s · %dm · 경로 위=%s", req.ev, station.place.name, station.distance_m, station.on_route)
    return station


@app.post("/course/suggest", response_model=SuggestResponse)
def suggest(req: CourseRequest, planner: CoursePlanner = Depends(get_planner)) -> SuggestResponse:
    """홈 추천: 성격이 다른 코스 후보를 최대 3개 만든다."""
    log.info("추천 요청: %s · 출발 %.3f,%.3f", _conditions_summary(req.conditions), req.origin.latitude, req.origin.longitude)
    try:
        return SuggestResponse(options=planner.suggest(req))
    except NoCourseError as e:
        raise HTTPException(404, str(e)) from e
    except httpx.HTTPError as e:
        log.warning("외부 API 오류: %r", e)
        raise HTTPException(502, f"외부 API 오류: {type(e).__name__}") from e

from functools import lru_cache

import httpx
from fastapi import Depends, FastAPI, HTTPException

from app.config import Settings, get_settings
from app.course.planner import CoursePlanner, NoCourseError
from app.parsing import get_parser
from app.schemas import CourseRequest, CourseResponse, ParseRequest, ParseResponse
from app.sources.kakao import KakaoClient
from app.sources.tour import TourApiClient

app = FastAPI(title="오늘, 어디 가지? API", version="0.1.0")


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
    return ParseResponse(conditions=parser.parse(req.text), parser=parser.name)


@lru_cache
def _sources(data_go_kr_key: str, kakao_key: str) -> tuple[TourApiClient, KakaoClient]:
    # 캐시를 요청 사이에 유지하려고 클라이언트를 재사용한다.
    return TourApiClient(data_go_kr_key), KakaoClient(kakao_key)


def get_planner(settings: Settings = Depends(get_settings)) -> CoursePlanner:
    if not settings.data_go_kr_key or not settings.kakao_rest_api_key:
        raise HTTPException(503, "DATA_GO_KR_KEY, KAKAO_REST_API_KEY가 필요합니다")
    tour, kakao = _sources(settings.data_go_kr_key, settings.kakao_rest_api_key)
    return CoursePlanner(tour, kakao)


@app.post("/course", response_model=CourseResponse)
def course(req: CourseRequest, planner: CoursePlanner = Depends(get_planner)) -> CourseResponse:
    try:
        return planner.plan(req)
    except NoCourseError as e:
        raise HTTPException(404, str(e)) from e
    except httpx.HTTPError as e:
        raise HTTPException(502, f"외부 API 오류: {type(e).__name__}") from e

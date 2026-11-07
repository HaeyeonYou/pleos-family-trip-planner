"""급한 케어 찾기: 수유실·기저귀 교환대·잠깐 세울 곳을 현재 위치(와 가는 방향) 기준으로 찾는다.

장소 데이터는 카카오 로컬 검색이다. 카카오에는 '수유실 있음' 같은 편의 정보가 없어서,
수유실·교환대가 있을 가능성이 높은 시설(대형마트·백화점·병원·도서관·육아지원센터 등)을 골라 알려 주고,
화면에는 '시설 안내 확인 필요'로 표시한다. 시설별 확인 정보가 필요하면 TourAPI 무장애 정보(lactationroom)를 붙인다 (#70).
"""

import logging
import math
from typing import Literal, Protocol

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel

from app.config import Settings, get_settings
from app.sources.kakao import CATEGORY_PARKING, KakaoClient, KakaoPlace

log = logging.getLogger("care")
router = APIRouter(prefix="/care")

Kind = Literal["nursing", "diaper", "rest", "restroom"]

# 도시 일반도로 평균 시속 (분 추정용). 길찾기 API를 부르지 않고 대략의 시간만 알린다.
URBAN_KMH = 30.0
# 도로가 직선보다 굽은 정도
ROAD_FACTOR = 1.3
# 가는 방향 직선에서 이만큼(m) 이내로 벗어나면 '경로 위'
ON_ROUTE_DETOUR_M = 1500
# 이 이하로 돌아가는 건 같은 길로 본다 (추가 시간 0분)
SAME_PATH_M = 300


class CarePlace(BaseModel):
    name: str
    address: str
    latitude: float
    longitude: float
    distance_m: int
    drive_minutes: int
    category: str
    # 지금 경로에서 더 가야 하는 시간(분). 가는 방향을 모르면 None.
    detour_minutes: int | None = None
    on_route: bool = False
    note: str = ""
    url: str = ""


class CareNearby(BaseModel):
    kind: Kind
    places: list[CarePlace]


class KakaoSearch(Protocol):
    def by_category(self, code: str, latitude: float, longitude: float, radius_m: int, size: int = 15) -> list[KakaoPlace]: ...

    def by_keyword(self, query: str, latitude: float, longitude: float, radius_m: int, size: int = 15) -> list[KakaoPlace]: ...


# 시설 종류 → 안내 문구. 키워드가 카테고리명에 들어 있으면 그 시설로 본다.
NURSING_FACILITIES = {
    "백화점": "백화점", "대형마트": "대형마트", "이마트": "대형마트", "롯데마트": "대형마트", "홈플러스": "대형마트",
    "코스트코": "대형마트", "아울렛": "아울렛", "쇼핑몰": "쇼핑몰", "복합쇼핑": "쇼핑몰",
    "육아종합지원센터": "육아종합지원센터", "장난감도서관": "장난감도서관", "공동육아": "공동육아방",
    "도서관": "도서관", "병원": "병원", "박물관": "박물관", "미술관": "미술관", "문화센터": "문화센터",
    "주민센터": "주민센터", "구청": "구청", "시청": "시청", "고속도로휴게소": "휴게소", "휴게소": "휴게소",
}
# 작은 가게는 수유실이 없다.
SMALL_SHOP_WORDS = ("슈퍼", "익스프레스", "프레시", "편의점", "메가마트")


def _haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def _minutes(distance_m: float) -> int:
    return max(1, round(distance_m * ROAD_FACTOR / 1000 / URBAN_KMH * 60))


def _facility(place: KakaoPlace) -> str | None:
    text = f"{place.category} {place.name}"
    if any(w in place.name for w in SMALL_SHOP_WORDS):
        return None
    for key, label in NURSING_FACILITIES.items():
        if key in text:
            return label
    return None


def _is_rest_candidate(place: KakaoPlace) -> bool:
    """안전하게 세울 수 있는 곳: 주차장과 공원. 장례·묘지·노상 주차는 뺀다."""
    text = f"{place.category} {place.name}"
    if any(w in text for w in ("장례", "묘지", "추모", "납골", "노상", "라이프")):
        return False
    return "주차장" in text or "공원" in place.category


def _candidates(kind: Kind, src: KakaoSearch, lat: float, lng: float, radius_m: int) -> list[tuple[KakaoPlace, str, str]]:
    """(장소, 종류 이름, 안내 문구)"""
    found: list[tuple[KakaoPlace, str, str]] = []
    if kind == "rest":
        for p in src.by_category(CATEGORY_PARKING, lat, lng, radius_m, size=15):
            if _is_rest_candidate(p):
                found.append((p, "주차장", "주차장 · 안전하게 세울 수 있어요"))
        for p in src.by_keyword("공원", lat, lng, radius_m, size=15):
            if _is_rest_candidate(p):
                found.append((p, "공원", "공원 · 주변에 세울 곳을 확인하세요"))
        return found
    if kind == "restroom":
        for q in ("공중화장실", "개방화장실"):
            for p in src.by_keyword(q, lat, lng, radius_m, size=15):
                if "화장실" in p.name or "화장실" in p.category:
                    found.append((p, "화장실", "화장실"))
        return found
    queries = ["수유실", "육아종합지원센터"] if kind == "nursing" else ["백화점", "대형마트", "육아종합지원센터"]
    for q in queries:
        for p in src.by_keyword(q, lat, lng, radius_m, size=15):
            label = _facility(p)
            if label is None:
                continue
            note = f"{label} · 수유실 안내 확인 필요" if kind == "nursing" else f"{label} · 기저귀 교환대 안내 확인 필요"
            found.append((p, label, note))
    return found


def find_nearby(
    kind: Kind,
    src: KakaoSearch,
    lat: float,
    lng: float,
    to_lat: float | None = None,
    to_lng: float | None = None,
    radius_m: int = 10000,
    limit: int = 2,
) -> list[CarePlace]:
    """반경 안에서 후보를 모아 중복을 없애고, 가는 방향이 있으면 돌아가는 길이 짧은 순, 없으면 가까운 순으로 고른다."""
    seen: set[str] = set()
    scored: list[tuple[float, CarePlace]] = []
    heading = to_lat is not None and to_lng is not None
    base = _haversine_m(lat, lng, to_lat, to_lng) if heading else 0.0
    for place, label, note in _candidates(kind, src, lat, lng, radius_m):
        if place.place_id in seen:
            continue
        seen.add(place.place_id)
        # 카카오 거리는 검색 기준점 직선거리. 반환값이 0이면 직접 계산한다.
        dist = place.distance_m or _haversine_m(lat, lng, place.latitude, place.longitude)
        if dist > radius_m:
            continue
        detour_m = None
        if heading:
            via = dist + _haversine_m(place.latitude, place.longitude, to_lat, to_lng)
            detour_m = max(0.0, via - base)
        item = CarePlace(
            name=place.name,
            address=place.address,
            latitude=place.latitude,
            longitude=place.longitude,
            distance_m=round(dist),
            drive_minutes=_minutes(dist),
            category=label,
            detour_minutes=None if detour_m is None else _minutes(detour_m) if detour_m > SAME_PATH_M else 0,
            on_route=detour_m is not None and detour_m <= ON_ROUTE_DETOUR_M,
            note=note,
            url=place.url,
        )
        scored.append((detour_m if detour_m is not None else dist, item))
    scored.sort(key=lambda s: s[0])
    return [item for _, item in scored[:limit]]


_client: KakaoClient | None = None


def get_kakao(settings: Settings = Depends(get_settings)) -> KakaoSearch:
    global _client
    if not settings.kakao_rest_api_key:
        raise HTTPException(503, "KAKAO_REST_API_KEY가 필요합니다")
    if _client is None:
        _client = KakaoClient(settings.kakao_rest_api_key)  # 캐시를 요청 사이에 유지한다
    return _client


@router.get("/nearby", response_model=CareNearby)
def nearby(
    kind: Kind,
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    to_lat: float | None = Query(None, ge=-90, le=90, description="가는 곳 위도 (있으면 경로 위 우선)"),
    to_lng: float | None = Query(None, ge=-180, le=180),
    radius_m: int = Query(10000, ge=500, le=20000),
    limit: int = Query(2, ge=1, le=5),
    src: KakaoSearch = Depends(get_kakao),
) -> CareNearby:
    log.info("케어 찾기: %s · %.4f,%.4f · 방향=%s", kind, lat, lng, to_lat is not None)
    try:
        return CareNearby(kind=kind, places=find_nearby(kind, src, lat, lng, to_lat, to_lng, radius_m, limit))
    except httpx.HTTPError as e:
        log.warning("외부 API 오류: %r", e)
        raise HTTPException(502, f"외부 API 오류: {type(e).__name__}") from e

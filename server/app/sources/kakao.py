"""카카오 로컬(장소 검색)과 카카오모빌리티(다중 경유지 길찾기)."""

from dataclasses import dataclass

import httpx

from app.logs import api_log_hooks
from app.sources.cache import TtlCache

LOCAL_URL = "https://dapi.kakao.com/v2/local/search"
NAVI_URL = "https://apis-navi.kakaomobility.com/v1/waypoints/directions"

CATEGORY_RESTAURANT = "FD6"
CATEGORY_CAFE = "CE7"
CATEGORY_PARKING = "PK6"


@dataclass(frozen=True)
class KakaoPlace:
    place_id: str
    name: str
    category: str
    latitude: float
    longitude: float
    address: str
    distance_m: float
    url: str


@dataclass(frozen=True)
class Leg:
    distance_m: int
    duration_s: int
    # 지도에 그릴 경로 좌표 (위도, 경도). 추정 구간은 출발·도착 두 점뿐.
    path: tuple[tuple[float, float], ...] = ()


# 지도 미리보기용이라 구간마다 이 정도 점이면 충분하다 (응답 크기 절약).
MAX_PATH_POINTS = 120


class KakaoClient:
    def __init__(self, rest_api_key: str, http: httpx.Client | None = None):
        self._headers = {"Authorization": f"KakaoAK {rest_api_key}"}
        self._http = http or httpx.Client(timeout=10, event_hooks=api_log_hooks("kakao"))
        self._cache = TtlCache(ttl_seconds=3600, name="카카오 장소검색")

    def by_category(self, code: str, latitude: float, longitude: float, radius_m: int, size: int = 15) -> list[KakaoPlace]:
        params = {"category_group_code": code, "radius": min(radius_m, 20000), "size": size}
        return self._search("category", params, latitude, longitude)

    def by_keyword(self, query: str, latitude: float, longitude: float, radius_m: int, size: int = 15) -> list[KakaoPlace]:
        params = {"query": query, "radius": min(radius_m, 20000), "size": size}
        return self._search("keyword", params, latitude, longitude)

    def route(self, points: list[tuple[float, float]]) -> list[Leg]:
        """(위도, 경도) 순서대로 지나는 경로의 구간별 거리·시간. 경유지는 최대 30개."""
        to_xy = lambda p: {"x": p[1], "y": p[0]}  # noqa: E731
        body = {
            "origin": to_xy(points[0]),
            "destination": to_xy(points[-1]),
            "waypoints": [to_xy(p) | {"name": f"w{i}"} for i, p in enumerate(points[1:-1])],
            "priority": "RECOMMEND",
            # 지도에 실제 길을 그리려고 도로 좌표(vertexes)까지 받는다.
            "summary": False,
        }
        res = self._http.post(NAVI_URL, json=body, headers=self._headers)
        res.raise_for_status()
        route = res.json()["routes"][0]
        if route["result_code"] != 0:
            raise ValueError(f"route failed: {route.get('result_msg')}")
        return [Leg(distance_m=s["distance"], duration_s=s["duration"], path=_section_path(s)) for s in route["sections"]]

    def _search(self, kind: str, params: dict, latitude: float, longitude: float) -> list[KakaoPlace]:
        params = params | {"x": longitude, "y": latitude, "sort": "distance"}
        key = (kind, tuple(sorted(params.items())))

        def load() -> list[KakaoPlace]:
            res = self._http.get(f"{LOCAL_URL}/{kind}.json", params=params, headers=self._headers)
            res.raise_for_status()
            return [_to_place(d) for d in res.json()["documents"]]

        return self._cache.get_or_load(key, load)


def _to_place(doc: dict) -> KakaoPlace:
    return KakaoPlace(
        place_id=doc["id"],
        name=doc["place_name"],
        category=doc.get("category_name", ""),
        latitude=float(doc["y"]),
        longitude=float(doc["x"]),
        address=doc.get("road_address_name") or doc.get("address_name", ""),
        distance_m=float(doc.get("distance") or 0),
        url=doc.get("place_url", ""),
    )


def _section_path(section: dict) -> tuple[tuple[float, float], ...]:
    """구간의 도로 좌표 [x1, y1, x2, y2, ...]를 (위도, 경도) 점으로, 너무 많으면 고르게 줄인다."""
    points = [
        (v[i + 1], v[i])
        for road in section.get("roads", [])
        for v in [road.get("vertexes", [])]
        for i in range(0, len(v) - 1, 2)
    ]
    if len(points) <= MAX_PATH_POINTS:
        return tuple(points)
    step = len(points) / (MAX_PATH_POINTS - 1)
    thinned = [points[round(i * step)] for i in range(MAX_PATH_POINTS - 1)] + [points[-1]]
    return tuple(thinned)

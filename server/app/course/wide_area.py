"""20km를 넘는 반경의 나들이 후보 모으기 (#250).

카카오 로컬 검색과 TourAPI 위치기반 목록은 반경을 최대 20km까지만 받는다. 홈 반경은 5~50km(Figma H01-2)라
20km를 넘으면 검색을 여러 중심점으로 나눈다.

- 출발지 중심 20km 한 번 + 출발지에서 (20km + 반경)/2 떨어진 고리 위 중심점 N개(각 20km).
  N은 반경 35km까지 6개, 그 위는 8개 → 50km에서도 고리 사이 빈틈이 거의 없다(고리 바깥 끝 일부만 빠짐).
- 모은 결과는 id로 겹침을 빼고, 출발지 기준 거리로 다시 재서 반경 밖은 버린 뒤 가까운 순으로 돌려준다.
- 중심점별 호출은 스레드로 동시에 보낸다(응답 시간은 가장 느린 한 번 수준). 각 클라이언트의 메모리 캐시가 중심점마다 따로 걸린다.

호출 수: 반경 20km 이하 1회, 35km 이하 7회, 그 위 9회 (검색 하나당). 코스 하나에서 TourAPI 목록 3종(관광지·문화시설·레포츠)과
카카오 놀이 키워드 2개·관심사 키워드 최대 3개가 이 방식을 쓴다.
"""

import dataclasses
import math
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from typing import TypeVar

API_MAX_RADIUS_M = 20_000  # 카카오 로컬·TourAPI 위치기반 목록의 최대 반경
RING_SMALL = 6  # 반경 35km 이하
RING_LARGE = 8
RING_LARGE_FROM_M = 35_000
EARTH_RADIUS_M = 6_371_000

T = TypeVar("T")


def search_centers(latitude: float, longitude: float, radius_m: int) -> list[tuple[float, float, int]]:
    """(위도, 경도, 검색 반경) 목록. 첫 항목은 늘 출발지다."""
    if radius_m <= API_MAX_RADIUS_M:
        return [(latitude, longitude, radius_m)]
    ring_m = (API_MAX_RADIUS_M + radius_m) / 2
    count = RING_LARGE if radius_m > RING_LARGE_FROM_M else RING_SMALL
    centers = [(latitude, longitude, API_MAX_RADIUS_M)]
    for i in range(count):
        lat, lng = _offset(latitude, longitude, ring_m, 2 * math.pi * i / count)
        centers.append((round(lat, 5), round(lng, 5), API_MAX_RADIUS_M))
    return centers


def gather(
    latitude: float,
    longitude: float,
    radius_m: int,
    fetch: Callable[[float, float, int], list[T]],
    key: Callable[[T], str],
) -> list[T]:
    """[fetch]를 중심점마다 불러 합친다. 반경 20km 이하는 한 번만 부르고 결과를 그대로 준다.

    항목은 latitude·longitude·distance_m을 가진 frozen dataclass(KakaoPlace, TourPlace)여야 한다.
    중심점 하나가 실패하면 그 중심점만 빠진다. 출발지 검색이 실패하면 예외를 그대로 올린다(지금과 같은 동작).
    """
    centers = search_centers(latitude, longitude, radius_m)
    if len(centers) == 1:
        return fetch(*centers[0])
    with ThreadPoolExecutor(max_workers=len(centers)) as pool:
        futures = [pool.submit(fetch, *c) for c in centers]
        first = futures[0].result()
        rest = []
        for f in futures[1:]:
            try:
                rest.append(f.result())
            except Exception:  # noqa: BLE001 - 바깥 중심점 하나의 장애가 후보 전체를 막지 않는다
                rest.append([])
    seen: set[str] = set()
    merged: list[T] = []
    for item in [x for batch in [first, *rest] for x in batch]:
        k = key(item)
        if k in seen:
            continue
        seen.add(k)
        d = _haversine_m(latitude, longitude, item.latitude, item.longitude)  # type: ignore[attr-defined]
        if d <= radius_m:
            merged.append(dataclasses.replace(item, distance_m=d))  # type: ignore[type-var]
    merged.sort(key=lambda x: x.distance_m)  # type: ignore[attr-defined]
    return merged


def _offset(lat: float, lng: float, distance_m: float, bearing: float) -> tuple[float, float]:
    """출발점에서 [bearing](라디안, 북쪽 0·시계 방향)으로 [distance_m] 떨어진 점."""
    d = distance_m / EARTH_RADIUS_M
    p1, l1 = math.radians(lat), math.radians(lng)
    p2 = math.asin(math.sin(p1) * math.cos(d) + math.cos(p1) * math.sin(d) * math.cos(bearing))
    l2 = l1 + math.atan2(math.sin(bearing) * math.sin(d) * math.cos(p1), math.cos(d) - math.sin(p1) * math.sin(p2))
    return math.degrees(p2), math.degrees(l2)


def _haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    a, b, c, d = map(math.radians, (lat1, lng1, lat2, lng2))
    h = math.sin((c - a) / 2) ** 2 + math.cos(a) * math.cos(c) * math.sin((d - b) / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))

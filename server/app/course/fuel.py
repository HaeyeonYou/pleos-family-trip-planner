"""주행 가능 거리 판정과 주유소·충전소 고르기 (#215, 지시서 1111-home-fuel).

외부 API는 부르지 않는다. 검색은 planner가 카카오 장소 검색으로 하고(QP3), 여기서는 순수 함수로 판정·선택만 한다.
앱 `vehicle/FuelRange.kt`와 기준이 같아야 한다.
"""

import math
from dataclasses import dataclass

# QP2 "부족" 기준 [제안, 디자이너 답 대기 2026-11-11]: 코스 총 거리(귀가 포함) × 1.3보다 주행 가능 거리가 짧으면 부족.
# 답이 오면 이 값 하나(앱은 FuelRange.kt의 FUEL_MARGIN)만 바꾼다.
FUEL_MARGIN = 1.3

CATEGORY_FUEL = "OL7"  # 카카오 장소 분류: 주유소·충전소
EV_KEYWORD = "전기차충전소"  # 카카오 OL7에는 전기차 충전소가 거의 없어 키워드로 찾는다
SEARCH_RADIUS_M = 5_000
ON_ROUTE_DETOUR_M = 500  # 돌아가는 거리가 이보다 짧으면 "경로 위"
FUEL_STAY_MINUTES = 10
CHARGE_STAY_MINUTES = 30
ROAD_FACTOR = 1.4  # 직선거리 → 도로 거리 어림 (planner._estimate_leg와 같음)
METERS_PER_DRIVE_MINUTE = 500


@dataclass(frozen=True)
class StationCandidate:
    """검색 결과를 판정에 필요한 값만 담는다."""

    key: str
    name: str
    category: str
    latitude: float
    longitude: float


@dataclass(frozen=True)
class StationPick:
    candidate: StationCandidate
    distance_m: int  # 출발지에서 도로 거리 어림
    drive_minutes: int
    detour_m: int  # 출발지 → 주유소 → 다음 지점이 바로 가는 것보다 늘어나는 거리. 다음 지점이 없으면 출발지에서의 거리
    on_route: bool


def needs_refuel(course_m: float, range_m: float | None, margin: float = FUEL_MARGIN) -> bool:
    """코스(귀가 포함)를 여유 있게 돌기에 주행 가능 거리가 모자란지. 값을 모르면 판정하지 않는다(False)."""
    if range_m is None or range_m <= 0 or course_m <= 0:
        return False
    return range_m < course_m * margin


def is_station(category: str, name: str, ev: bool) -> bool:
    """전기차면 전기차 충전소만, 아니면 주유소만 (LPG 충전소·세차장 등은 뺀다)."""
    text = f"{category} {name}"
    if ev:
        return "충전" in text and ("전기" in text or "EV" in text.upper())
    return "주유소" in text


def pick_station(
    origin: tuple[float, float],
    toward: tuple[float, float] | None,
    candidates: list[StationCandidate],
    ev: bool,
) -> StationPick | None:
    """돌아가는 거리가 가장 짧은 곳 1곳. 다음 지점(toward)이 없으면 가장 가까운 곳."""
    best: StationPick | None = None
    for c in candidates:
        if not is_station(c.category, c.name, ev):
            continue
        at = (c.latitude, c.longitude)
        to_station = road_m(origin, at)
        detour = to_station + road_m(at, toward) - road_m(origin, toward) if toward else to_station
        pick = StationPick(
            candidate=c,
            distance_m=round(to_station),
            drive_minutes=max(1, math.ceil(to_station / METERS_PER_DRIVE_MINUTE)),
            detour_m=round(max(0.0, detour)),
            on_route=toward is not None and detour <= ON_ROUTE_DETOUR_M,
        )
        if best is None or (pick.detour_m, pick.distance_m) < (best.detour_m, best.distance_m):
            best = pick
    return best


def stay_minutes(ev: bool) -> int:
    return CHARGE_STAY_MINUTES if ev else FUEL_STAY_MINUTES


def road_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, (*a, *b))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h)) * ROAD_FACTOR

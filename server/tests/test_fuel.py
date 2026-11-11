"""주행 가능 거리 판정과 주유소·충전소 경유지 (#215).

판정·선택 규칙은 순수 함수로, 코스 넣기와 /course/fuel은 저장해 둔 실제 카카오·TourAPI 응답으로 확인한다.
"""

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.course import fuel
from app.course.planner import CoursePlanner
from app.main import app, get_planner
from app.sources.kakao import KakaoClient
from app.sources.tour import TourApiClient
from tests.api_cache import caching_transport
from tests.test_real_api_data import FIXED_NOW, GANGNAM

settings = get_settings()
http = httpx.Client(transport=caching_transport(httpx), timeout=15)


def _key(name: str) -> str:
    return getattr(settings, name) or "cached"


@pytest.fixture(scope="module")
def client():
    tour = TourApiClient(_key("data_go_kr_key"), http=http)
    kakao = KakaoClient(_key("kakao_rest_api_key"), http=http)
    app.dependency_overrides[get_planner] = lambda: CoursePlanner(tour, kakao, now=FIXED_NOW)
    yield TestClient(app)
    app.dependency_overrides.clear()


# 판정 (QP2 제안: 코스 × 1.3)


def test_needs_refuel_below_80km():
    assert fuel.needs_refuel(10_000, 79_999)  # 짧은 코스라도 80km 미만이면 부족
    assert not fuel.needs_refuel(10_000, 80_000)  # 80km면 충분


def test_needs_refuel_uses_margin_for_long_course():
    assert fuel.needs_refuel(100_000, 120_000)  # 100km × 1.3 = 130km > 120km
    assert not fuel.needs_refuel(100_000, 131_000)
    assert not fuel.needs_refuel(100_000, 130_000)  # 같으면 충분


def test_needs_refuel_without_value_is_not_judged():
    assert not fuel.needs_refuel(40_000, None)
    assert not fuel.needs_refuel(40_000, 0)
    assert not fuel.needs_refuel(0, 10_000)


def test_margin_is_one_constant():
    assert fuel.needs_refuel(100_000, 115_000, margin=1.2)
    assert not fuel.needs_refuel(100_000, 115_000, margin=1.1)
    assert fuel.LOW_RANGE_M == 80_000


# 고르기


def _c(key, name, category, lat, lng):
    return fuel.StationCandidate(key=key, name=name, category=category, latitude=lat, longitude=lng)


GAS = "교통,수송 > 자동차 > 주유,가스 > 주유소 > GS칼텍스"
LPG = "교통,수송 > 자동차 > 주유,가스 > LPG충전소 > SK LPG충전소"
EV = "교통,수송 > 자동차 > 전기차 충전소"


def test_is_station_by_fuel_kind():
    assert fuel.is_station(GAS, "서광주유소", ev=False)
    assert not fuel.is_station(LPG, "진양가스충전소", ev=False)
    assert not fuel.is_station(GAS, "서광주유소", ev=True)
    assert fuel.is_station(EV, "아라타워 전기차충전소", ev=True)
    assert not fuel.is_station(LPG, "진양가스충전소", ev=True)


def test_pick_prefers_on_the_way_over_nearest():
    origin = (37.5000, 127.0000)
    toward = (37.5500, 127.0000)  # 북쪽으로 약 5.5km
    behind = _c("1", "뒤쪽주유소", GAS, 37.4950, 127.0000)  # 남쪽 550m (가깝지만 반대 방향)
    ahead = _c("2", "앞쪽주유소", GAS, 37.5100, 127.0005)  # 북쪽 1.1km, 길 위
    pick = fuel.pick_station(origin, toward, [behind, ahead], ev=False)
    assert pick is not None and pick.candidate.key == "2"
    assert pick.on_route and pick.detour_m <= fuel.ON_ROUTE_DETOUR_M
    assert pick.distance_m > 0 and pick.drive_minutes >= 1


def test_pick_without_toward_is_nearest_and_not_on_route():
    origin = (37.5000, 127.0000)
    near = _c("1", "가까운주유소", GAS, 37.5010, 127.0000)
    far = _c("2", "먼주유소", GAS, 37.5100, 127.0000)
    pick = fuel.pick_station(origin, None, [far, near], ev=False)
    assert pick is not None and pick.candidate.key == "1" and not pick.on_route


def test_pick_skips_wrong_kind():
    origin = (37.5, 127.0)
    assert fuel.pick_station(origin, None, [_c("1", "진양가스충전소", LPG, 37.501, 127.0)], ev=False) is None


# 실제 응답으로 코스에 넣기


BASE = {"conditions": {"child_ages": [4], "themes": ["experience"]}, "origin": {"latitude": GANGNAM[0], "longitude": GANGNAM[1]},
        "departure_time": "10:30", "seed": 1}


def test_course_without_range_has_no_fuel_stop(client):
    res = client.post("/course", json=BASE)
    assert res.status_code == 200, res.text
    assert all(s["role"] != "fuel" for s in res.json()["stops"])


def test_course_with_enough_range_has_no_fuel_stop(client):
    res = client.post("/course", json=BASE | {"range_m": 500_000})
    assert all(s["role"] != "fuel" for s in res.json()["stops"])


def test_short_range_puts_gas_station_first(client):
    plain = client.post("/course", json=BASE).json()
    res = client.post("/course", json=BASE | {"range_m": 1_000})
    assert res.status_code == 200, res.text
    course = res.json()
    first = course["stops"][0]
    assert first["role"] == "fuel" and "주유소" in first["place"]["category"]
    assert first["stay_minutes"] == fuel.FUEL_STAY_MINUTES
    # 나머지 순서는 그대로, 시간표는 앞으로만 흐른다
    assert [s["place"]["name"] for s in course["stops"][1:]] == [s["place"]["name"] for s in plain["stops"]]
    times = [s["arrive_at"] for s in course["stops"]]
    assert times == sorted(times)
    assert any("주유소" in w for w in course["warnings"])


def test_short_range_ev_puts_charger_with_longer_stay(client):
    res = client.post("/course", json=BASE | {"range_m": 1_000, "ev": True})
    first = res.json()["stops"][0]
    assert first["role"] == "fuel" and "전기차 충전소" in first["place"]["category"]
    assert first["stay_minutes"] == fuel.CHARGE_STAY_MINUTES


def test_suggest_summary_hides_fuel_stop(client):
    res = client.post("/course/suggest", json=BASE | {"range_m": 1_000})
    assert res.status_code == 200, res.text
    for option in res.json()["options"]:
        assert option["course"]["stops"][0]["role"] == "fuel"
        assert option["course"]["stops"][0]["place"]["name"] not in option["summary"]


def test_reschedule_keeps_fuel_stop(client):
    course = client.post("/course", json=BASE | {"range_m": 1_000}).json()
    stops = [{"role": s["role"], "place": s["place"], "stay_minutes": s["stay_minutes"]} for s in course["stops"][:-1]]
    res = client.post("/course/schedule", json={"conditions": BASE["conditions"], "origin": BASE["origin"], "departure_time": "10:30", "stops": stops})
    assert res.status_code == 200, res.text
    assert res.json()["stops"][0]["role"] == "fuel"


def test_fuel_endpoint_on_the_way(client):
    res = client.post("/course/fuel", json={"origin": BASE["origin"], "toward": {"latitude": 37.5172, "longitude": 127.0473}})
    assert res.status_code == 200, res.text
    body = res.json()
    assert "주유소" in body["place"]["category"]
    assert 0 < body["distance_m"] <= fuel.SEARCH_RADIUS_M * fuel.ROAD_FACTOR
    assert body["drive_minutes"] >= 1 and isinstance(body["on_route"], bool)


def test_fuel_endpoint_ev(client):
    res = client.post("/course/fuel", json={"origin": BASE["origin"], "ev": True})
    assert res.status_code == 200, res.text
    assert "전기차 충전소" in res.json()["place"]["category"]

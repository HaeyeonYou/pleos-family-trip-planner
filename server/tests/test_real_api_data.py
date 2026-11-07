"""실제 외부 API 데이터로 응답 형식과 코스 생성을 검증한다.

처음 실행할 때 실제로 호출해 tests/api_cache/에 저장하고, 이후에는 저장본을 쓴다
(TourAPI 하루 1,000건 한도, 같은 호출 반복 방지). 데이터를 새로 받으려면:
    API_CACHE=refresh .venv/bin/python -m pytest tests/test_real_api_data.py
저장본이 갱신되면 날마다 데이터가 바뀌므로 특정 장소 대신 구조와 규칙을 확인한다.
"""

import re

import httpx
import httpx2
import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.course import classify
from app.course.planner import CoursePlanner
from app.main import app, get_planner
from app.parsing.claude import ClaudeParser
from app.schemas import Theme
from app.sources.kakao import CATEGORY_CAFE, CATEGORY_RESTAURANT, KakaoClient
from app.sources.tour import CONTENT_TYPE_ATTRACTION, PET, TourApiClient
from tests.api_cache import caching_transport

GANGNAM = (37.4979, 127.0276)
SUWON = (37.2636, 127.0286)
HHMM = re.compile(r"^\d{2}:\d{2}$")

settings = get_settings()
http = httpx.Client(transport=caching_transport(httpx), timeout=15)
anthropic_http = httpx2.Client(transport=caching_transport(httpx2), timeout=30)


def key(name: str) -> str:
    # 저장본으로 돌릴 때는 키가 필요 없다 (캐시 키에서 API 키를 뺀다).
    return getattr(settings, name) or "cached"


@pytest.fixture(scope="module")
def tour() -> TourApiClient:
    return TourApiClient(key("data_go_kr_key"), http=http)


@pytest.fixture(scope="module")
def kakao() -> KakaoClient:
    return KakaoClient(key("kakao_rest_api_key"), http=http)


@pytest.fixture(scope="module")
def client(tour, kakao):
    app.dependency_overrides[get_planner] = lambda: CoursePlanner(tour, kakao)
    yield TestClient(app)
    app.dependency_overrides.clear()


# 외부 API 응답 형식


def test_tour_nearby_returns_places_with_coordinates(tour):
    places = tour.nearby(*GANGNAM, radius_m=5000, content_type_id=CONTENT_TYPE_ATTRACTION, rows=10)
    assert places, "강남역 5km 안 관광지가 하나도 없음"
    p = places[0]
    assert p.content_id and p.title
    assert 37 < p.latitude < 38 and 126 < p.longitude < 128
    assert p.cat1 and p.cat3, "서비스분류 코드가 비어 있음 (분류 규칙이 동작하지 않음)"
    assert [x.distance_m for x in places] == sorted(x.distance_m for x in places), "거리순 정렬이 아님"


def test_tour_pet_service_responds(tour):
    places = tour.nearby(*GANGNAM, radius_m=10000, service=PET, rows=5)
    assert isinstance(places, list)


def test_tour_amenity_fields_exist(tour):
    # 무장애 정보가 있는 장소를 하나 골라 영유아가족 필드 4개가 오는지 본다.
    accessible = tour.nearby(*GANGNAM, radius_m=5000, service="KorWithService2", rows=5)
    assert accessible
    amenities = tour.amenities(accessible[0].content_id)
    assert set(amenities) == {"stroller", "nursing_room", "kids_chair", "infant_etc"}


def test_kakao_category_and_keyword_search(kakao):
    for code in (CATEGORY_RESTAURANT, CATEGORY_CAFE):
        found = kakao.by_category(code, *GANGNAM, radius_m=1000, size=5)
        assert found and all(p.name and p.category for p in found)
    kids = kakao.by_keyword("키즈카페", *GANGNAM, radius_m=5000, size=5)
    assert any("키즈카페" in p.category or "키즈카페" in p.name for p in kids)


def test_shopping_mall_is_play_outing(kakao):
    # 경험 우주 '쇼핑·놀이' 분야: 복합쇼핑몰은 실내 놀이 나들이로 분류한다.
    malls = kakao.by_keyword("복합쇼핑몰", *SUWON, radius_m=10000, size=3)
    assert malls
    assert all(classify.kakao_outing_kind(p) == (Theme.PLAY, True) for p in malls)


def test_kakao_multi_waypoint_route(kakao):
    legs = kakao.route([GANGNAM, (37.5115, 127.0595), (37.5172, 127.0473), GANGNAM])
    assert len(legs) == 3
    assert all(leg.distance_m > 0 and leg.duration_s > 0 for leg in legs)


# 서버 엔드포인트 (실제 데이터로 코스 생성)


@pytest.mark.parametrize(
    "conditions, origin",
    [
        ({"child_ages": [4], "themes": ["experience"]}, GANGNAM),
        ({"child_ages": [0], "indoor_only": True, "needs": ["nursing_room"]}, GANGNAM),
        ({"child_ages": [5], "with_pet": True}, GANGNAM),
        ({"child_ages": [6], "themes": ["nature"], "max_drive_minutes": 60}, SUWON),
        ({"child_ages": [5], "interests": ["공룡", "기차"]}, GANGNAM),
    ],
    ids=["체험", "영아실내수유실", "반려동물", "수원자연", "관심사"],
)
def test_course_with_real_data(client, conditions, origin):
    res = client.post(
        "/course",
        json={"conditions": conditions, "origin": {"latitude": origin[0], "longitude": origin[1]}, "departure_time": "10:30", "seed": 1},
    )
    assert res.status_code == 200, res.text
    course = res.json()
    roles = [s["role"] for s in course["stops"]]
    assert "outing" in roles and roles[-1] == "home"
    assert course["route_source"] == "kakao", "카카오 길찾기 실패로 추정값 사용"
    # 지도에 그릴 실제 도로 좌표가 온다 (장소 수보다 훨씬 많은 점)
    assert len(course["path"]) > len(course["stops"]) * 3

    # 시간표는 출발 시각부터 앞으로만 흐른다
    times = [s["arrive_at"] for s in course["stops"]]
    assert all(HHMM.match(t) for t in times)
    assert times == sorted(times) and times[0] >= "10:30"

    outing = next(s for s in course["stops"] if s["role"] == "outing")
    if conditions.get("indoor_only"):
        assert outing["place"]["source"] == "kakao" or outing["place"]["category"].startswith("A0206")
    for place in course["alternatives"].get("outing", []):
        assert place["name"] != outing["place"]["name"]


def test_schedule_after_swapping_outing_with_real_route(client):
    """코스를 만든 뒤 나들이 장소를 교체 후보로 바꾸면 실제 길찾기로 시간표를 다시 계산한다."""
    req = {"conditions": {"child_ages": [4]}, "origin": {"latitude": GANGNAM[0], "longitude": GANGNAM[1]}, "departure_time": "10:30", "seed": 1}
    course = client.post("/course", json=req).json()
    alternatives = course["alternatives"].get("outing", [])
    assert alternatives, "나들이 교체 후보가 없음"
    stops = [{"role": s["role"], "place": s["place"]} for s in course["stops"] if s["role"] != "home"]
    i = next(i for i, s in enumerate(stops) if s["role"] == "outing")
    stops[i]["place"] = alternatives[0]

    res = client.post(
        "/course/schedule",
        json={**req, "departure_time": course["departure_at"], "stops": stops, "suggest_nearby": True},
    )
    assert res.status_code == 200, res.text
    new = res.json()
    assert new["route_source"] == "kakao"
    assert [s["place"]["name"] for s in new["stops"][:-1]] == [s["place"]["name"] for s in stops]
    times = [s["arrive_at"] for s in new["stops"]]
    assert times == sorted(times) and times[0] >= "10:30"
    # 바뀐 나들이 장소 근처 식사 후보를 새로 찾는다 (코스에 있는 곳은 제외)
    in_course = {s["place"]["source_id"] for s in stops}
    assert new["alternatives"].get("meal"), "새 나들이 장소 근처 식당 후보가 없음"
    assert all(p["source_id"] not in in_course for p in new["alternatives"]["meal"])


def test_parse_endpoint_rule(client):
    res = client.post("/parse", json={"text": "공룡 좋아하는 5살, 수유실 있는 곳, 6시 전 귀가"})
    assert res.status_code == 200
    c = res.json()["conditions"]
    assert c["child_ages"] == [5] and c["return_by_hour"] == 18
    assert "공룡" in c["interests"] and "nursing_room" in c["needs"]


def test_claude_parser_matches_rule_parser():
    parser = ClaudeParser(key("anthropic_api_key"), settings.claude_model, http_client=anthropic_http)
    c = parser.parse("공룡이랑 기차 좋아하는 5살 아들, 6시 전에는 집에 와야 해")
    assert c.child_ages == [5]
    assert c.return_by_hour == 18
    assert set(c.interests) >= {"공룡", "기차"}

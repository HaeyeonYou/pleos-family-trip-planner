"""급한 케어 찾기: 실제 카카오 응답(저장본)으로 형식을, 가짜 데이터로 정렬 규칙을 확인한다."""

import httpx
import pytest
from fastapi.testclient import TestClient

from app.care import find_nearby, get_kakao
from app.main import app
from app.sources.kakao import KakaoClient, KakaoPlace
from tests.api_cache import caching_transport
from tests.test_real_api_data import key, settings

GURI = (37.5943, 127.1296)
GANGDONG = (37.5400, 127.1370)


def kp(pid, name, category, lat, lng, dist=0.0):
    return KakaoPlace(place_id=pid, name=name, category=category, latitude=lat, longitude=lng, address="서울", distance_m=dist, url="")


class FakeSearch:
    def __init__(self, keyword=None, category=None):
        self.keyword = keyword or {}
        self.category = category or {}

    def by_keyword(self, query, latitude, longitude, radius_m, size=15):
        return self.keyword.get(query, [])

    def by_category(self, code, latitude, longitude, radius_m, size=15):
        return self.category.get(code, [])


def test_nursing_picks_facilities_and_drops_small_shops_and_far_places():
    src = FakeSearch(keyword={
        "수유실": [
            kp("1", "롯데마트 천호점", "쇼핑 > 대형마트 > 롯데마트", 37.54, 127.14, 800),
            kp("2", "GS더프레시 강동역점", "쇼핑 > 슈퍼마켓", 37.54, 127.14, 100),
            kp("3", "먼 병원", "의료 > 종합병원", 37.7, 127.3, 15000),
            kp("4", "강동도서관", "교육 > 국공립도서관", 37.55, 127.14, 500),
        ],
        "육아종합지원센터": [kp("1", "롯데마트 천호점", "쇼핑 > 대형마트 > 롯데마트", 37.54, 127.14, 800)],
    })
    places = find_nearby("nursing", src, *GANGDONG)
    assert [p.name for p in places] == ["강동도서관", "롯데마트 천호점"]  # 중복·작은 가게·반경 밖 제외, 가까운 순
    assert places[0].drive_minutes >= 1 and "수유실 안내 확인 필요" in places[0].note


def test_with_destination_prefers_small_detour_and_marks_on_route():
    # 북쪽 도착지로 간다. 남쪽 마트가 더 가깝지만 돌아가야 하므로 북쪽 마트가 먼저다.
    src = FakeSearch(keyword={"백화점": [], "대형마트": [
        kp("s", "남쪽마트", "쇼핑 > 대형마트", 37.50, 127.00, 3336),
        kp("n", "북쪽마트", "쇼핑 > 대형마트", 37.56, 127.00, 3336),
    ], "육아종합지원센터": []})
    places = find_nearby("diaper", src, 37.53, 127.00, to_lat=37.60, to_lng=127.00)
    assert [p.name for p in places] == ["북쪽마트", "남쪽마트"]
    assert places[0].on_route and places[0].detour_minutes == 0
    assert not places[1].on_route and places[1].detour_minutes > 0


def test_rest_returns_parking_and_parks_but_not_funeral_or_roadside():
    src = FakeSearch(
        category={"PK6": [kp("p", "천호 공영주차장", "교통 > 주차장 > 공영주차장", 37.5, 127.1, 300), kp("x", "노상 주차장", "교통 > 주차장", 37.5, 127.1, 100)]},
        keyword={"공원": [kp("g", "성심어린이공원", "여행 > 공원 > 어린이공원", 37.5, 127.1, 200), kp("f", "용인공원라이프 강동지점", "서비스 > 장례", 37.5, 127.1, 100)]},
    )
    names = [p.name for p in find_nearby("rest", src, *GANGDONG, limit=5)]
    assert names == ["성심어린이공원", "천호 공영주차장"]


@pytest.fixture(scope="module")
def real_client():
    http = httpx.Client(transport=caching_transport(httpx), timeout=15)
    kakao = KakaoClient(key("kakao_rest_api_key"), http=http)
    app.dependency_overrides[get_kakao] = lambda: kakao
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.pop(get_kakao, None)


@pytest.mark.parametrize("kind", ["nursing", "diaper", "rest"])
def test_real_kakao_data_gives_places_with_fields(real_client, kind):
    res = real_client.get("/care/nearby", params={"kind": kind, "lat": GANGDONG[0], "lng": GANGDONG[1], "to_lat": GURI[0], "to_lng": GURI[1]})
    assert res.status_code == 200
    places = res.json()["places"]
    assert 1 <= len(places) <= 2
    for p in places:
        assert p["name"] and p["distance_m"] <= 10000 and p["drive_minutes"] >= 1 and p["note"]
        assert p["detour_minutes"] is not None


def test_bad_kind_is_rejected(real_client):
    assert real_client.get("/care/nearby", params={"kind": "x", "lat": 37, "lng": 127}).status_code == 422

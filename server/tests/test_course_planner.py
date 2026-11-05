from datetime import datetime

import pytest

from app.course.planner import KST, CoursePlanner, NoCourseError
from app.schemas import Amenity, CourseRequest, LatLng, StopRole, Theme, TripConditions
from app.sources.kakao import CATEGORY_CAFE, CATEGORY_RESTAURANT, KakaoPlace, Leg
from app.sources.tour import CONTENT_TYPE_ATTRACTION, CONTENT_TYPE_CULTURE, PET, TourPlace

ORIGIN = LatLng(latitude=37.4979, longitude=127.0276)
NOW = datetime(2026, 11, 7, 10, 3, tzinfo=KST)


def tour(cid, title, cat3, dist, type_id=CONTENT_TYPE_ATTRACTION, image=""):
    return TourPlace(
        content_id=cid, content_type_id=type_id, title=title, latitude=37.5, longitude=127.03,
        address="서울", cat1=cat3[:3], cat2=cat3[:5], cat3=cat3, distance_m=dist, image_url=image,
    )


def kakao(pid, name, category, dist=100.0):
    return KakaoPlace(
        place_id=pid, name=name, category=category, latitude=37.501, longitude=127.031,
        address="서울", distance_m=dist, url="",
    )


class FakeTour:
    def __init__(self, places=None, pet_places=None, amenities=None):
        self.places = places or {}
        self.pet_places = pet_places or []
        self._amenities = amenities or {}
        self.amenity_calls = 0

    def nearby(self, latitude, longitude, radius_m, content_type_id=None, service="KorService2", rows=60):
        if service == PET:
            return self.pet_places
        return self.places.get(content_type_id, [])

    def amenities(self, content_id):
        self.amenity_calls += 1
        return self._amenities.get(content_id, {})


class FakeKakao:
    def __init__(self, restaurants=None, cafes=None, play=None, legs_minutes=None, route_fails=False):
        self.restaurants = restaurants if restaurants is not None else [kakao("r1", "한식당", "음식점 > 한식")]
        self.cafes = cafes if cafes is not None else [kakao("c1", "카페", "음식점 > 카페")]
        self.play = play or []
        self.legs_minutes = legs_minutes
        self.route_fails = route_fails

    def by_category(self, code, latitude, longitude, radius_m, size=15):
        return {CATEGORY_RESTAURANT: self.restaurants, CATEGORY_CAFE: self.cafes}[code]

    def by_keyword(self, query, latitude, longitude, radius_m, size=15):
        return self.play if query == "키즈카페" else []

    def route(self, points):
        if self.route_fails:
            raise RuntimeError("down")
        minutes = self.legs_minutes or [20] * (len(points) - 1)
        return [Leg(distance_m=m * 500, duration_s=m * 60) for m in minutes]


def plan(conditions=None, tour_src=None, kakao_src=None, **req):
    planner = CoursePlanner(tour_src or FakeTour(), kakao_src or FakeKakao(), now=NOW)
    return planner.plan(CourseRequest(conditions=conditions or TripConditions(), origin=ORIGIN, **req))


PARK = tour("p1", "역삼공원", "A02020700", 800)
MUSEUM = tour("m1", "어린이박물관", "A02060100", 3000, type_id=CONTENT_TYPE_CULTURE)
FARM = tour("f1", "체험농장", "A02030200", 5000)


def test_builds_meal_outing_cafe_home_with_timetable():
    res = plan(tour_src=FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}), departure_time="10:30")
    assert [s.role for s in res.stops] == [StopRole.MEAL, StopRole.OUTING, StopRole.CAFE, StopRole.HOME]
    assert res.stops[1].place.name == "역삼공원"
    # 10:30 출발, 구간마다 20분, 식사 60, 나들이 120, 카페 40
    assert [s.arrive_at for s in res.stops] == ["10:50", "12:10", "14:30", "15:30"]
    assert res.return_at == "15:30"
    assert res.total_drive_minutes == 80
    assert res.route_source == "kakao"


def test_theme_match_beats_distance():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK, FARM]})
    res = plan(TripConditions(themes=[Theme.EXPERIENCE]), tour_src=src)
    assert res.stops[1].place.name == "체험농장"
    assert res.alternatives[StopRole.OUTING][0].name == "역삼공원"


def test_indoor_only_keeps_culture_and_kids_cafe():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK], CONTENT_TYPE_CULTURE: [MUSEUM]})
    res = plan(TripConditions(indoor_only=True), tour_src=src)
    assert res.stops[1].place.name == "어린이박물관"
    assert all(p.name != "역삼공원" for p in res.alternatives[StopRole.OUTING])


def test_kids_cafe_from_kakao_is_a_play_candidate():
    play = [kakao("k1", "키즈카페A", "가정,생활 > 유아 > 놀이시설 > 키즈카페", dist=500)]
    res = plan(TripConditions(themes=[Theme.PLAY]), kakao_src=FakeKakao(play=play))
    assert res.stops[1].place.name == "키즈카페A"
    assert res.stops[1].place.source == "kakao"


def test_needs_prefer_place_with_nursing_room():
    near = tour("a", "가까운공원", "A02020700", 500)
    far = tour("b", "수유실공원", "A02020700", 4000)
    src = FakeTour(
        {CONTENT_TYPE_ATTRACTION: [near, far]},
        amenities={"b": {"nursing_room": "1층 수유실", "stroller": ""}},
    )
    res = plan(TripConditions(needs=["nursing_room"]), tour_src=src)
    outing = res.stops[1].place
    assert outing.name == "수유실공원"
    assert outing.amenities["nursing_room"] == Amenity.AVAILABLE
    assert outing.amenities["stroller"] == Amenity.UNKNOWN  # 빈 값은 '없음'이 아니라 '정보 없음'
    assert outing.amenity_notes["nursing_room"] == "1층 수유실"


def test_pet_uses_pet_service_only():
    pet_park = tour("pp", "반려견공원", "A02020700", 1000)
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}, pet_places=[pet_park])
    res = plan(TripConditions(with_pet=True), tour_src=src)
    assert res.stops[1].place.name == "반려견공원"
    assert res.alternatives[StopRole.OUTING] == []


def test_unsuitable_places_are_excluded():
    tomb = tour("t", "선릉 왕릉", "A02010100", 100)  # 역사관광지: 테마 없음
    bar = kakao("b", "호프집", "음식점 > 술집 > 호프")
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [tomb, PARK]})
    res = plan(tour_src=src, kakao_src=FakeKakao(restaurants=[bar, kakao("r", "국수집", "음식점 > 한식 > 국수")]))
    assert res.stops[1].place.name == "역삼공원"
    assert res.stops[0].place.name == "국수집"


def test_drops_cafe_to_meet_return_time():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]})
    res = plan(TripConditions(return_by_hour=15), tour_src=src, departure_time="10:30")
    assert [s.role for s in res.stops] == [StopRole.MEAL, StopRole.OUTING, StopRole.HOME]
    assert res.return_at == "14:30"
    assert any("카페를 뺐어요" in w for w in res.warnings)


def test_warns_when_return_time_cannot_be_met():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]})
    res = plan(TripConditions(return_by_hour=12), tour_src=src, departure_time="10:30")
    assert any("귀가하기 어려워요" in w for w in res.warnings)


def test_falls_back_to_estimate_when_route_fails():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]})
    res = plan(tour_src=src, kakao_src=FakeKakao(route_fails=True))
    assert res.route_source == "estimate"
    assert all(s.drive_minutes >= 0 for s in res.stops)


def test_infant_shortens_outing_and_default_departure_rounds_up():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]})
    res = plan(TripConditions(child_ages=[1]), tour_src=src)
    assert res.stops[1].stay_minutes == 90
    assert res.stops[0].arrive_at == "10:30"  # 10:03 → 10:10 출발 + 20분


def test_amenity_lookups_are_limited():
    many = [tour(str(i), f"공원{i}", "A02020700", 100 * i) for i in range(20)]
    src = FakeTour({CONTENT_TYPE_ATTRACTION: many})
    plan(tour_src=src)
    assert src.amenity_calls == 6


def test_no_candidates_raises():
    with pytest.raises(NoCourseError):
        plan(tour_src=FakeTour())


def test_streets_halls_and_snack_shops_are_excluded():
    street = tour("s", "가로수길", "A02030400", 100)
    hall = tour("h", "컨벤션홀", "A02060400", 200, type_id=CONTENT_TYPE_CULTURE)
    bakery = kakao("bk", "빵집", "음식점 > 간식 > 제과,베이커리")
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [street, PARK], CONTENT_TYPE_CULTURE: [hall, MUSEUM]})
    res = plan(tour_src=src, kakao_src=FakeKakao(restaurants=[bakery, kakao("r", "한식당", "음식점 > 한식")]))
    names = [res.stops[1].place.name] + [p.name for p in res.alternatives[StopRole.OUTING]]
    assert "가로수길" not in names and "컨벤션홀" not in names
    assert res.stops[0].place.name == "한식당"


class InterestKakao(FakeKakao):
    def __init__(self, keyword_results, **kw):
        super().__init__(**kw)
        self.keyword_results = keyword_results
        self.queries = []

    def by_keyword(self, query, latitude, longitude, radius_m, size=15):
        self.queries.append(query)
        return self.keyword_results.get(query, [])


def test_interest_matches_place_name():
    dino = tour("d", "공룡테마 자연사박물관", "A02060100", 6000, type_id=CONTENT_TYPE_CULTURE)
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK], CONTENT_TYPE_CULTURE: [dino]})
    res = plan(TripConditions(interests=["공룡"]), tour_src=src)
    assert res.stops[1].place.name == "공룡테마 자연사박물관"


def test_interest_search_adds_kakao_outing_places_only():
    train = kakao("t1", "철도박물관", "문화,예술 > 문화시설 > 박물관", dist=4000)
    shop = kakao("t2", "기차 장난감 가게", "쇼핑 > 완구", dist=100)
    kk = InterestKakao({"기차": [train, shop]})
    res = plan(TripConditions(interests=["기차", "공룡", "우주"]), tour_src=FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}), kakao_src=kk)
    assert res.stops[1].place.name == "철도박물관"
    names = [p.name for p in res.alternatives[StopRole.OUTING]]
    assert "기차 장난감 가게" not in names
    assert [q for q in kk.queries if q not in ("키즈카페", "실내놀이터")] == ["기차", "공룡"]  # 최대 2개

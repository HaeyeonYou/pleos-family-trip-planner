"""코스에 주차장 붙이기: 정렬 규칙(순수 함수), 조건 parking의 대체 경로, 재계산·추천 일관성, DB 연동.

DB 연동 테스트의 데이터는 tests/data/parking_sample.csv(실제 데이터와 같은 열 형식의 샘플)를 pleos_test에 넣어 쓴다.
"""

import httpx

from datetime import datetime

from app.course.parking_rank import is_open_at, rank, sort_score
from app.course.planner import KST, CoursePlanner
from app.main import app, get_planner
from app.schemas import Amenity, CourseRequest, LatLng, Place, ScheduleRequest, ScheduleStop, StopRole, TripConditions
from app.sources.parking import DbParkingSource, ParkingPlace
from app.sources.tour import CONTENT_TYPE_ATTRACTION
from tests.test_course_planner import ORIGIN, FakeKakao, FakeTour, tour
from tests.test_parking import SAMPLE, load_sample, run

NOW = datetime(2026, 11, 9, 10, 3, tzinfo=KST)  # 월요일
SEOUL_PLAZA = (37.5663, 126.9779)  # 샘플 서울광장 공영주차장 위치


def lot(name="주차장", dist=100, kind="공영", fee="30분 400원", open_="00:00", close="24:00", capacity=50) -> ParkingPlace:
    return ParkingPlace(
        name=name, kind=kind, lot_type="노외", address="서울", distance_m=dist, capacity=capacity, fee_summary=fee,
        hours="평일", open_time=open_, close_time=close, latitude=37.5, longitude=127.0,
    )


# ---- 정렬 규칙 (순수 함수) ----

def test_open_at_handles_unknown_and_overnight():
    assert is_open_at("09:00", "18:00", "12:00") is True
    assert is_open_at("09:00", "18:00", "19:00") is False
    assert is_open_at("00:00", "24:00", "23:50") is True
    assert is_open_at("22:00", "06:00", "01:00") is True  # 자정을 넘기는 운영
    assert is_open_at("22:00", "06:00", "12:00") is False
    assert is_open_at(None, None, "12:00") is None  # 모르면 단정하지 않는다


def test_closed_at_arrival_goes_last_and_is_marked():
    lots = [lot("닫음", 50, open_="09:00", close="18:00"), lot("열림", 400)]
    res = rank(lots, "20:00", infant=False)
    assert [p.name for p in res] == ["열림", "닫음"]
    assert [p.open_at_arrival for p in res] == [True, False]


def test_unknown_hours_not_treated_as_closed():
    res = rank([lot("모름", 100, open_=None, close=None), lot("먼곳", 450)], "20:00", infant=False)
    assert res[0].name == "모름" and res[0].open_at_arrival is None


def test_free_and_public_get_bonus():
    private = lot("민영", 100, kind="민영")
    public = lot("공영", 150, kind="공영")
    free_private = lot("무료민영", 200, kind="민영", fee="무료")
    assert [p.name for p in rank([private, public, free_private], "12:00", False)] == ["무료민영", "공영", "민영"]
    assert rank([free_private], "12:00", False)[0].free is True


def test_infant_prefers_closer():
    near_private = lot("가까운민영", 100, kind="민영", fee="30분 1,000원")
    far_free_public = lot("먼무료공영", 300, kind="공영", fee="무료")
    assert rank([near_private, far_free_public], "12:00", infant=False)[0].name == "먼무료공영"
    assert rank([near_private, far_free_public], "12:00", infant=True)[0].name == "가까운민영"
    assert sort_score(near_private, True, True) < sort_score(far_free_public, True, True)


def test_rank_limits_to_three_and_estimates_walk():
    res = rank([lot(f"p{i}", 100 * (i + 1)) for i in range(6)], "12:00", False)
    assert len(res) == 3
    assert res[0].walk_minutes == 1 and res[2].walk_minutes == 4  # 300m ≈ 4분


# ---- planner 연결 ----

class FakeParking:
    def __init__(self, by_name: dict[str, list[ParkingPlace]] | None = None, fail=False):
        self.by_name = by_name or {}
        self.calls = 0
        self.fail = fail

    def near(self, latitude, longitude, radius_m, limit, today):
        self.calls += 1
        if self.fail:
            raise RuntimeError("down")
        return self.by_name.get("*", [])


PARK = tour("p1", "역삼공원", "A02020700", 800)


def make(parking, tour_src):
    return CoursePlanner(tour_src, FakeKakao(), now=NOW, parking=parking)


def test_plan_attaches_parking_to_outing_meal_cafe_not_home():
    p = make(FakeParking({"*": [lot("A", 120), lot("B", 300)]}), FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}))
    course = p.plan(CourseRequest(conditions=TripConditions(), origin=ORIGIN, departure_time="10:30"))
    assert [bool(s.parking) for s in course.stops] == [True, True, True, False]
    assert course.stops[1].parking[0].name == "A"


def test_parking_failure_does_not_break_course():
    p = make(FakeParking(fail=True), FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}))
    course = p.plan(CourseRequest(conditions=TripConditions(), origin=ORIGIN))
    assert all(s.parking == [] for s in course.stops)


def test_needs_parking_uses_table_before_tourapi():

    class CountingTour(FakeTour):
        parking_calls = 0

        def parking(self, content_id, content_type_id):
            CountingTour.parking_calls += 1
            return "주차 가능"

    src = CountingTour({CONTENT_TYPE_ATTRACTION: [PARK]})
    p = make(FakeParking({"*": [lot("A", 120)]}), src)
    course = p.plan(CourseRequest(conditions=TripConditions(needs=["parking"]), origin=ORIGIN))
    assert CountingTour.parking_calls == 0  # 외부 API를 부르지 않았다
    assert course.stops[1].place.amenities["parking"] == Amenity.AVAILABLE


def test_needs_parking_falls_back_to_tourapi_when_table_empty():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}, parking={"p1": "주차 가능 30대"})
    p = make(FakeParking({}), src)
    course = p.plan(CourseRequest(conditions=TripConditions(needs=["parking"]), origin=ORIGIN))
    outing = course.stops[1]
    assert outing.place.amenities["parking"] == Amenity.AVAILABLE
    assert outing.place.amenity_notes["parking"] == "주차 가능 30대"
    assert outing.parking == []  # 표에 없으면 '없다'고 하지 않고 비운다


def test_reschedule_and_suggest_carry_parking():
    p = make(FakeParking({"*": [lot("A", 120)]}), FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}))
    place = Place(name="역삼공원", latitude=37.5, longitude=127.03, source="tour", source_id="p1")
    res = p.reschedule(ScheduleRequest(conditions=TripConditions(), origin=LatLng(latitude=37.4979, longitude=127.0276),
                                       stops=[ScheduleStop(role=StopRole.OUTING, place=place)], departure_time="10:30"))
    assert res.stops[0].parking[0].name == "A" and res.stops[-1].parking == []
    options = p.suggest(CourseRequest(conditions=TripConditions(), origin=ORIGIN))
    assert options and options[0].course.stops[1].parking[0].name == "A"


# ---- 실제 DB(pleos_test) 연동 ----

def test_db_source_through_schedule_endpoint():
    place = {"name": "서울광장", "latitude": SEOUL_PLAZA[0], "longitude": SEOUL_PLAZA[1], "source": "tour", "source_id": "x"}
    body = {
        "conditions": {"child_age_months": [18]},
        "origin": {"latitude": 37.55, "longitude": 126.97},
        "departure_time": "10:30",
        "stops": [{"role": "outing", "place": place}],
    }

    async def scenario(db):
        await load_sample(SAMPLE)
        app.dependency_overrides[get_planner] = lambda: CoursePlanner(
            FakeTour(), FakeKakao(), now=NOW, parking=DbParkingSource(app.state.db)
        )
        try:
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                r = await client.post("/course/schedule", json=body)
        finally:
            app.dependency_overrides.pop(get_planner, None)
        assert r.status_code == 200, r.text
        lots = r.json()["stops"][0]["parking"]
        assert 1 <= len(lots) <= 3
        assert lots[0]["distance_m"] <= 500 and lots[0]["name"].startswith("[샘플]")
        assert {"kind", "capacity", "fee_summary", "hours", "walk_minutes", "open_at_arrival", "latitude", "longitude"} <= lots[0].keys()
        assert r.json()["stops"][-1]["parking"] == []

    run(scenario)

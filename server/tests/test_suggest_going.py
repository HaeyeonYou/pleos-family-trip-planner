"""홈 추천 3개, '지금 N대 가는 중' 집계, 기상청 격자 변환."""

import dataclasses

from app.course.planner import KST, CoursePlanner
from app.schemas import CourseRequest, LatLng, TripConditions
from app.sources.tour import CONTENT_TYPE_ATTRACTION, CONTENT_TYPE_CULTURE
from app.weather import to_grid
from tests.test_auth import run
from tests.test_course_planner import FakeKakao, FakeTour, FARM, MUSEUM, NOW, ORIGIN, PARK, kakao


def suggest(conditions=None, tour_src=None, kakao_src=None):
    planner = CoursePlanner(tour_src or FakeTour(), kakao_src or FakeKakao(), now=NOW)
    return planner.suggest(CourseRequest(conditions=conditions or TripConditions(), origin=ORIGIN, departure_time="10:30"))


def test_suggest_gives_varied_options_with_summary_and_times():
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [PARK, FARM], CONTENT_TYPE_CULTURE: [MUSEUM]})
    play = [kakao("k1", "키즈카페A", "가정,생활 > 유아 > 놀이시설 > 키즈카페", dist=500)]
    options = suggest(tour_src=src, kakao_src=FakeKakao(play=play))
    assert len(options) == 3
    assert len({o.place_key for o in options}) == 3  # 나들이 장소가 서로 다르다
    assert len({o.name.split(" · ")[0] for o in options}) == 3  # 성격도 다르다
    first = options[0]
    assert first.summary == "한식당 → " + first.course.stops[1].place.name + " → 카페"
    assert first.first_drive_minutes == 20
    assert first.total_minutes == 20 * 4 + 60 + 120 + 40  # 이동 4구간 + 식사·나들이·카페
    assert first.place_key.startswith(("tour:", "kakao:"))


def test_suggest_fills_with_same_kind_when_few_kinds():
    parks = [PARK] + [dataclasses.replace(PARK, content_id=f"p{i}", title=f"공원{i}", distance_m=900 + i) for i in range(3)]
    options = suggest(tour_src=FakeTour({CONTENT_TYPE_ATTRACTION: parks}))
    assert len(options) == 3
    assert len({o.place_key for o in options}) == 3


def test_kma_grid_conversion_matches_known_points():
    assert to_grid(37.5665, 126.9780) == (60, 127)  # 서울 시청
    assert to_grid(37.4979, 127.0276) == (61, 125)  # 강남


def test_going_counts_only_recent_starts_per_place():
    async def scenario(client):
        for _ in range(3):
            assert (await client.post("/course/starts", json={"place_key": "tour:1"})).status_code == 204
        await client.post("/course/starts", json={"place_key": "kakao:9"})
        res = await client.get("/course/going", params={"keys": "tour:1,kakao:9,tour:none"})
        assert res.json() == {"tour:1": 3, "kakao:9": 1, "tour:none": 0}

        # 1시간 지난 시작은 세지 않는다
        from datetime import timedelta
        from sqlalchemy import update
        from app.db import utcnow
        from app.main import app
        from app.models import CourseStart

        async with app.state.db.sessions() as db:
            await db.execute(update(CourseStart).where(CourseStart.place_key == "kakao:9").values(started_at=utcnow() - timedelta(minutes=61)))
            await db.commit()
        res = await client.get("/course/going", params={"keys": "tour:1,kakao:9"})
        assert res.json() == {"tour:1": 3, "kakao:9": 0}
        assert (await client.post("/course/starts", json={"place_key": ""})).status_code == 422

    run(scenario)


def test_parking_need_prefers_place_with_parking_info():
    near = PARK
    far = dataclasses.replace(PARK, content_id="far", title="주차장있는공원", distance_m=4000)
    src = FakeTour({CONTENT_TYPE_ATTRACTION: [near, far]}, parking={"far": "무료 주차 50대", near.content_id: "주차 불가"})
    options = suggest(TripConditions(needs=["parking"]), tour_src=src)
    outing = options[0].course.stops[1].place
    assert outing.name == "주차장있는공원"
    assert outing.amenities["parking"].value == "available" and outing.amenity_notes["parking"] == "무료 주차 50대"


def test_kids_zone_need_prefers_play_facility():
    play = [kakao("k1", "키즈카페A", "가정,생활 > 유아 > 놀이시설 > 키즈카페", dist=3000)]
    options = suggest(TripConditions(needs=["kids_zone"]), tour_src=FakeTour({CONTENT_TYPE_ATTRACTION: [PARK]}), kakao_src=FakeKakao(play=play))
    outing = options[0].course.stops[1].place
    assert outing.name == "키즈카페A" and outing.amenities["kids_zone"].value == "available"

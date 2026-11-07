"""점수 다양화: 가중 선택, 시드 재현, 방문 이력, 다른 코스 보기(제외·순환), 식사·카페 선택."""

import dataclasses
import random
from datetime import date

from app.course import ranking
from app.course.planner import CoursePlanner
from app.schemas import CourseRequest, StopRole, Theme, TripConditions, VisitSummary
from app.sources.tour import CONTENT_TYPE_ATTRACTION
from tests.test_course_planner import NOW, ORIGIN, PARK, FakeKakao, FakeTour, kakao

PARKS = [dataclasses.replace(PARK, content_id=f"p{i}", title=f"공원{i}", distance_m=500 + 300 * i) for i in range(8)]


def req(**kw):
    return CourseRequest(conditions=kw.pop("conditions", TripConditions()), origin=ORIGIN, departure_time="10:30", **kw)


def outing_of(course):
    return next(s.place for s in course.stops if s.role == StopRole.OUTING)


def planner(kakao_src=None):
    return CoursePlanner(FakeTour({CONTENT_TYPE_ATTRACTION: PARKS}), kakao_src or FakeKakao(), now=NOW)


def test_same_seed_same_result_and_other_seeds_vary():
    results = {seed: outing_of(planner().plan(req(seed=seed))).source_id for seed in range(30)}
    assert [outing_of(planner().plan(req(seed=s))).source_id for s in (3, 3)] == [results[3]] * 2
    assert len(set(results.values())) >= 3  # 같은 조건이어도 시드에 따라 후보가 달라진다


def test_weighted_order_is_pure_and_keeps_strong_leader():
    items = [("a", 10.0), ("b", 9.5), ("c", 9.0), ("far", 1.0)]
    first = ranking.weighted_order(items, lambda x: x[1], random.Random(1))
    assert first == ranking.weighted_order(items, lambda x: x[1], random.Random(1))
    assert first[-1][0] == "far"  # 점수 차가 큰 후보는 풀에 들지 못한다
    leaders = {ranking.weighted_order(items, lambda x: x[1], random.Random(s))[0][0] for s in range(50)}
    assert leaders == {"a", "b", "c"}
    solo = [("x", 1.0)]
    assert ranking.weighted_order(solo, lambda x: x[1], random.Random(0)) == solo


def test_exclude_keys_removes_shown_places_then_cycles():
    seen: list[str] = []
    p = planner()
    for _ in range(8):
        course = p.plan(req(exclude_place_keys=seen))
        key = "tour:" + outing_of(course).source_id
        assert key not in seen and not course.warnings
        seen.append(key)
    assert len(set(seen)) == 8
    # 8곳을 모두 보여 줬으면 처음부터 다시, 안내 문구가 붙는다
    again = p.plan(req(exclude_place_keys=seen))
    assert again.warnings and "처음부터" in again.warnings[0]


def test_visit_adjustment_penalizes_recent_place_and_rewards_new_theme():
    today = date(2026, 11, 7)
    recent = [VisitSummary(place_key="tour:p1", theme=Theme.NATURE, date="2026-11-03")]
    assert ranking.visit_adjustment("tour:p1", Theme.NATURE, recent, today) < -5
    assert ranking.visit_adjustment("tour:p9", Theme.EXHIBITION, recent, today) == 1.0  # 안 가 본 분야 가점
    assert ranking.visit_adjustment("tour:p9", Theme.NATURE, recent, today) == -0.5
    old = [VisitSummary(place_key="tour:p1", theme=Theme.NATURE, date="2026-05-01")]
    assert ranking.visit_adjustment("tour:p1", Theme.NATURE, old, today) == 0.0  # 오래된 방문은 영향 없음
    assert ranking.visit_adjustment("tour:p1", Theme.NATURE, [], today) == 0.0


def test_recently_visited_place_is_not_picked():
    p = planner()
    visits = [VisitSummary(place_key=f"tour:p{i}", theme=Theme.NATURE, date="2026-11-05") for i in range(3)]
    for seed in range(30):
        assert outing_of(p.plan(req(seed=seed, visits=visits))).source_id not in {"p0", "p1", "p2"}


def test_meal_is_not_always_the_nearest():
    meals = [kakao(f"r{i}", f"식당{i}", "음식점 > 한식", dist=100 + 80 * i) for i in range(6)]
    p = planner(FakeKakao(restaurants=meals))
    picked = {next(s.place.source_id for s in p.plan(req(seed=seed)).stops if s.role == StopRole.MEAL) for seed in range(40)}
    assert len(picked) >= 3 and "r0" in picked


def test_cafe_is_not_always_the_nearest():
    cafes = [kakao(f"c{i}", f"카페{i}", "음식점 > 카페", dist=100 + 80 * i) for i in range(6)]
    p = planner(FakeKakao(cafes=cafes))
    picked = {next(s.place.source_id for s in p.plan(req(seed=seed)).stops if s.role == StopRole.CAFE) for seed in range(40)}
    assert len(picked) >= 3


def test_kids_friendly_meal_is_favored():
    meals = [kakao("r0", "일반식당", "음식점 > 양식", dist=200), kakao("r1", "키즈 패밀리레스토랑", "음식점 > 패밀리레스토랑", dist=1200)]
    p = planner(FakeKakao(restaurants=meals))
    wins = sum(next(s.place.source_id for s in p.plan(req(seed=s)).stops if s.role == StopRole.MEAL) == "r1" for s in range(40))
    assert wins > 25


def test_suggest_varies_by_seed_and_keeps_distinct_places():
    p = planner()
    sets = set()
    for seed in range(10):
        keys = [o.place_key for o in p.suggest(req(seed=seed))]
        assert len(set(keys)) == 3
        sets.add(tuple(keys))
    assert len(sets) >= 3


def test_festival_today_is_offered_with_period_and_bonus():
    import dataclasses as dc

    fest = dc.replace(PARK, content_id="fe1", title="가을 가족 축제", distance_m=4000, event_start="20261101", event_end="20261110")
    beer = dc.replace(fest, content_id="fe2", title="옥토버페스트")
    src = FakeTour({CONTENT_TYPE_ATTRACTION: PARKS})
    src.festival_places = [fest, beer]
    p = CoursePlanner(src, FakeKakao(), now=NOW)
    shown = set()
    for seed in range(40):
        place = outing_of(p.plan(req(seed=seed)))
        shown.add(place.source_id)
        if place.source_id == "fe1":
            assert place.event_period == "2026-11-01~2026-11-10" and place.category == "행사·축제"
    assert "fe1" in shown and "fe2" not in shown  # 주류 행사는 뺀다
    ranked = p._rank_outings(ORIGIN, TripConditions(), [], NOW.date())
    assert next(c for c in ranked if c.place.source_id == "fe1").festival


def test_night_events_and_pubs_are_excluded():
    from app.course import classify

    night = dataclasses.replace(PARK, title="남산 나이트 페스티벌")
    assert not classify.event_is_suitable(night)
    assert classify.event_is_suitable(dataclasses.replace(PARK, title="가을 가족 축제"))
    assert not classify.restaurant_is_suitable(kakao("r9", "고래맥주창고", "음식점 > 한식"))
    assert classify.restaurant_is_suitable(kakao("r8", "돈까스집", "음식점 > 일식"))


def test_adult_shows_are_excluded_unless_family_words():
    from app.course import classify

    assert not classify.event_is_suitable(dataclasses.replace(PARK, title="쿼드, 연극의 질문들"))
    assert classify.event_is_suitable(dataclasses.replace(PARK, title="어린이 인형극 연극제"))

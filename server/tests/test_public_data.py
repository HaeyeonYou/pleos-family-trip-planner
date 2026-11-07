"""공공데이터포털 후보: 사전 적재한 실제 데이터와 실제 응답 저장본으로 검증한다."""

from datetime import date, datetime, timedelta

import httpx
import pytest

from app.config import get_settings
from app.course import classify
from app.course.planner import KST, CoursePlanner
from app.schemas import CourseRequest, LatLng, StopRole, TripConditions
from app.sources.public_data import PublicData, _parse_events
from tests.api_cache import caching_transport
from tests.test_course_planner import FakeKakao, FakeTour

GANGNAM = (37.4979, 127.0276)
TODAY = date(2026, 11, 8)
NOW = datetime(2026, 11, 8, 10, 0, tzinfo=KST)

settings = get_settings()
http = httpx.Client(transport=caching_transport(httpx), timeout=20)


@pytest.fixture(scope="module")
def public() -> PublicData:
    return PublicData(settings.data_go_kr_key or "cached", http=http)


def test_playgrounds_are_public_parks_with_coordinates(public):
    places = public.playgrounds(*GANGNAM, 5_000)
    assert len(places) >= 5
    assert all(p.group in ("도시공원", "자연휴양림") and p.distance_m <= 5_000 for p in places)
    assert [p.distance_m for p in places] == sorted(p.distance_m for p in places)
    assert not any("주택" in p.group or "어린이집" in p.group for p in places)


def test_culture_facilities_are_museums_and_libraries(public):
    places = public.facilities(*GANGNAM, 8_000)
    groups = {p.group for p in places}
    assert groups <= {"박물관", "미술관", "도서관", "공연장", "문화원"} or groups & {"박물관", "도서관", "미술관"}
    assert len(places) >= 5 and all(p.latitude and p.longitude for p in places)


def test_events_today_are_real_and_current(public):
    events = public.events(TODAY, *GANGNAM, 20_000)
    assert events, "수도권 반경 20km에 오늘 전시·공연이 없음"
    for e in events:
        assert e.start <= "20261108" <= e.end and e.distance_m <= 20_000 and e.title and e.realm


def test_event_xml_parsing_skips_items_without_coordinates():
    xml = ("<response><body><items><item><seq>1</seq><title>어린이 전시</title><place>갤러리</place><realmName>전시</realmName>"
           "<startDate>20261101</startDate><endDate>20261130</endDate><gpsX>127.0</gpsX><gpsY>37.5</gpsY></item>"
           "<item><seq>2</seq><title>좌표 없음</title></item></items></body></response>")
    events = _parse_events(xml)
    assert [e.seq for e in events] == ["1"] and events[0].latitude == 37.5


def test_family_event_filter():
    assert classify.culture_event_is_family("어린이 과학 체험전", "서울도서관")
    assert classify.culture_event_is_family("가을 음악회", "가족극장")
    assert not classify.culture_event_is_family("현대미술 개인전", "갤러리")
    assert not classify.culture_event_is_family("성인 와인 클래스 체험", "스튜디오")


def test_facility_theme_excludes_university_museums():
    assert classify.culture_facility_theme("박물관", "국립중앙박물관") is not None
    assert classify.culture_facility_theme("박물관", "한국대학교박물관") is None
    assert classify.culture_facility_theme("공연장", "예술의전당") is None


def test_course_uses_public_candidates_and_dedupes_names(public):
    planner = CoursePlanner(FakeTour(), FakeKakao(), now=NOW, public=public)
    outings = planner._rank_outings(LatLng(latitude=GANGNAM[0], longitude=GANGNAM[1]), TripConditions(), [], TODAY)
    public_places = [c for c in outings if c.place.source == "public"]
    assert len(public_places) >= 10
    names = [classify.normalize_name(c.place.name) for c in outings]
    assert len(names) == len(set(names))  # 같은 이름은 한 번만
    park = next(c for c in public_places if c.place.category in ("도시공원", "자연휴양림"))
    assert park.place.amenities["kids_zone"].value == "available"


def test_public_failure_does_not_break_course():
    class Broken:
        def playgrounds(self, *a):
            raise RuntimeError("down")

        facilities = events = playgrounds

    planner = CoursePlanner(FakeTour(), FakeKakao(play=[]), now=NOW, public=Broken())  # type: ignore[arg-type]
    assert planner._public_candidates(LatLng(latitude=37.5, longitude=127.0), 10_000, [], TripConditions()) == []


def test_course_request_with_public_places_has_all_stops(public):
    planner = CoursePlanner(FakeTour(), FakeKakao(), now=NOW, public=public)
    course = planner.plan(CourseRequest(conditions=TripConditions(), origin=LatLng(latitude=GANGNAM[0], longitude=GANGNAM[1]), seed=3))
    outing = next(s.place for s in course.stops if s.role == StopRole.OUTING)
    assert outing.source == "public" and outing.name


# 기상청 단기예보 (실제 응답 저장본)

from app import weather  # noqa: E402


def test_short_forecast_real_response_gives_rain_outlook():
    now = datetime(2026, 11, 8, 1, 42, tzinfo=KST)
    base = weather._forecast_base(now)
    assert base.strftime("%Y%m%d%H%M") == "202611072300"  # 02시 발표는 02:10부터 열린다
    nx, ny = weather.to_grid(*GANGNAM)
    items = weather._items(http, "getVilageFcst", settings.kma_api_hub_key or "cached",
                           {"base_date": "20261107", "base_time": "2300", "nx": nx, "ny": ny, "numOfRows": 1000})
    assert {"POP", "PTY", "TMP", "SKY"} <= {i["category"] for i in items}
    chance, later = weather.rain_outlook(items, now)
    assert chance is not None and 0 <= chance <= 100
    assert later == (chance >= weather.RAIN_LATER_POP or any(
        i["category"] == "PTY" and i["fcstValue"] != "0" and now.strftime("%Y%m%d%H") <= i["fcstDate"] + i["fcstTime"][:2] <= (now + timedelta(hours=6)).strftime("%Y%m%d%H")
        for i in items))


def test_rain_outlook_rules():
    now = datetime(2026, 11, 8, 10, 0, tzinfo=KST)

    def item(cat, hour, value):
        return {"category": cat, "fcstDate": "20261108", "fcstTime": f"{hour:02d}00", "fcstValue": value}

    dry = [item("POP", 11, "10"), item("POP", 14, "30"), item("PTY", 14, "0")]
    assert weather.rain_outlook(dry, now) == (30, False)
    soon = [item("POP", 12, "70")]
    assert weather.rain_outlook(soon, now) == (70, True)
    assert weather.rain_outlook([item("POP", 20, "90")], now) == (None, False)  # 6시간 밖
    assert weather.rain_outlook([item("POP", 12, "20"), item("PTY", 13, "1")], now) == (20, True)  # 비 예보
    assert weather._forecast_base(datetime(2026, 11, 8, 11, 5, tzinfo=KST)).hour == 8
    assert weather._forecast_base(datetime(2026, 11, 8, 11, 15, tzinfo=KST)).hour == 11


def test_best_playgrounds_prefers_destination_parks(public):
    near = public.playgrounds(*GANGNAM, 8_000)
    best = classify.best_playgrounds(near)
    assert 0 < len(best) <= classify.PLAYGROUND_LIMIT
    assert all(p.facilities >= 3 or any(w in p.name for w in ("근린", "호수", "수목원", "휴양림", "대공원", "체육공원", "생태", "숲")) for p in best)
    assert len(best) < len(near)  # 동네 소공원은 걸러진다

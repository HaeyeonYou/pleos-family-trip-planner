"""식사 → 나들이 → 카페 → 귀가 코스를 만든다.

나들이 장소를 먼저 고르고(코스의 중심), 그 근처에서 식사와 카페를 찾는다.
LLM은 쓰지 않는다. 조건 해석은 /parse가, 여기서는 규칙으로만 검증·조합한다.
"""

import math
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Protocol
from zoneinfo import ZoneInfo

from app import interests
from app.course import classify
from app.schemas import (
    Amenity,
    CourseRequest,
    CourseResponse,
    LatLng,
    Place,
    ScheduleRequest,
    Stop,
    StopRole,
    Theme,
    TripConditions,
)
from app.sources.kakao import CATEGORY_CAFE, CATEGORY_RESTAURANT, KakaoPlace, Leg
from app.sources.tour import CONTENT_TYPE_ATTRACTION, CONTENT_TYPE_CULTURE, PET, TourPlace

KST = ZoneInfo("Asia/Seoul")

DEFAULT_RADIUS_M = 10_000
MAX_RADIUS_M = 20_000
METERS_PER_DRIVE_MINUTE = 500  # 시내 평균 30km/h 가정
NEARBY_MEAL_RADIUS_M = 2_000
NEARBY_CAFE_RADIUS_M = 1_500
AMENITY_LOOKUPS = 6  # 후보 상위 몇 곳만 무장애 상세를 조회한다 (API 한도)
ALTERNATIVES = 3
MAX_INTEREST_SEARCHES = 2  # 관심사 키워드 검색은 앞의 2개까지 (카카오 호출 수)

STAY_MINUTES = {StopRole.MEAL: 60, StopRole.OUTING: 120, StopRole.CAFE: 40}
INFANT_OUTING_MINUTES = 90  # 만 2세 이하는 나들이를 짧게


class TourSource(Protocol):
    def nearby(
        self, latitude: float, longitude: float, radius_m: int, content_type_id: int | None = None,
        service: str = ..., rows: int = ...,
    ) -> list[TourPlace]: ...

    def amenities(self, content_id: str) -> dict[str, str]: ...


class KakaoSource(Protocol):
    def by_category(self, code: str, latitude: float, longitude: float, radius_m: int, size: int = ...) -> list[KakaoPlace]: ...

    def by_keyword(self, query: str, latitude: float, longitude: float, radius_m: int, size: int = ...) -> list[KakaoPlace]: ...

    def route(self, points: list[tuple[float, float]]) -> list[Leg]: ...


class NoCourseError(Exception):
    """조건에 맞는 나들이 장소가 없을 때."""


@dataclass
class _Candidate:
    place: Place
    theme: Theme | None
    indoor: bool
    distance_m: float
    score: float = 0.0


class CoursePlanner:
    def __init__(self, tour: TourSource, kakao: KakaoSource, now: datetime | None = None):
        self._tour = tour
        self._kakao = kakao
        self._now = now

    def plan(self, req: CourseRequest) -> CourseResponse:
        cond = req.conditions
        origin = req.origin
        home = req.home or origin
        warnings: list[str] = []

        outings = self._rank_outings(origin, cond)
        if not outings:
            raise NoCourseError("조건에 맞는 나들이 장소를 찾지 못했어요")
        outing = outings[0].place

        meals = self._nearby_kakao(CATEGORY_RESTAURANT, outing, NEARBY_MEAL_RADIUS_M, classify.restaurant_is_suitable)
        cafes = self._nearby_kakao(CATEGORY_CAFE, outing, NEARBY_CAFE_RADIUS_M, classify.cafe_is_suitable)
        if not meals:
            warnings.append("나들이 장소 근처에서 식당을 찾지 못해 식사를 뺐어요")

        stops = [(StopRole.MEAL, meals[0])] if meals else []
        stops.append((StopRole.OUTING, outing))
        if cafes:
            stops.append((StopRole.CAFE, cafes[0]))

        departure = self._departure(req.departure_time)
        course = self._schedule(origin, home, stops, departure, cond)

        if cond.return_by_hour is not None and self._late(course, departure, cond.return_by_hour):
            if any(role == StopRole.CAFE for role, _ in stops):
                stops = [s for s in stops if s[0] != StopRole.CAFE]
                course = self._schedule(origin, home, stops, departure, cond)
                warnings.append(f"{cond.return_by_hour}시 귀가를 맞추려고 카페를 뺐어요")
            if self._late(course, departure, cond.return_by_hour):
                warnings.append(f"{cond.return_by_hour}시까지 귀가하기 어려워요 (예상 {course.return_at})")

        course.alternatives = {
            StopRole.OUTING: [c.place for c in outings[1 : 1 + ALTERNATIVES]],
            StopRole.MEAL: meals[1 : 1 + ALTERNATIVES],
            StopRole.CAFE: cafes[1 : 1 + ALTERNATIVES],
        }
        course.warnings = warnings
        return course

    def reschedule(self, req: ScheduleRequest) -> CourseResponse:
        """사용자가 바꾼 장소·순서 그대로 이동 시간과 시간표를 다시 계산한다."""
        cond = req.conditions
        departure = self._departure(req.departure_time)
        stops = [(s.role, s.place) for s in req.stops if s.role != StopRole.HOME]
        if not stops:
            raise NoCourseError("방문할 장소가 없어요")
        course = self._schedule(req.origin, req.home or req.origin, stops, departure, cond)
        if cond.return_by_hour is not None and self._late(course, departure, cond.return_by_hour):
            course.warnings = [f"{cond.return_by_hour}시까지 귀가하기 어려워요 (예상 {course.return_at})"]
        return course

    # 후보 수집과 점수

    def _rank_outings(self, origin: LatLng, cond: TripConditions) -> list[_Candidate]:
        radius = DEFAULT_RADIUS_M
        if cond.max_drive_minutes:
            radius = min(MAX_RADIUS_M, cond.max_drive_minutes * METERS_PER_DRIVE_MINUTE)

        candidates = self._tour_candidates(origin, radius, cond.with_pet)
        if not cond.with_pet:
            candidates += self._play_candidates(origin, radius)
            candidates += self._interest_candidates(origin, radius, cond.interests, candidates)
        if cond.indoor_only:
            candidates = [c for c in candidates if c.indoor]

        for c in candidates:
            c.score = _score(c, cond, radius)
        candidates.sort(key=lambda c: c.score, reverse=True)

        # 상위 후보만 육아 편의 정보를 붙이고, 필요 조건이 있으면 그 결과로 다시 정렬한다.
        for c in candidates[:AMENITY_LOOKUPS]:
            if c.place.source == "tour":
                _attach_amenities(c.place, self._tour.amenities(c.place.source_id))
        if cond.needs:
            for c in candidates[:AMENITY_LOOKUPS]:
                c.score += 2 * sum(c.place.amenities.get(n) == Amenity.AVAILABLE for n in cond.needs)
            candidates.sort(key=lambda c: c.score, reverse=True)
        return candidates

    def _tour_candidates(self, origin: LatLng, radius: int, with_pet: bool) -> list[_Candidate]:
        if with_pet:
            places = self._tour.nearby(origin.latitude, origin.longitude, radius, service=PET)
        else:
            places = [
                p
                for type_id in (CONTENT_TYPE_ATTRACTION, CONTENT_TYPE_CULTURE)
                for p in self._tour.nearby(origin.latitude, origin.longitude, radius, content_type_id=type_id)
            ]
        seen: set[str] = set()
        result = []
        for p in places:
            if p.content_id in seen or not classify.tour_is_suitable(p):
                continue
            seen.add(p.content_id)
            result.append(
                _Candidate(
                    place=_tour_place(p),
                    theme=classify.tour_theme(p),
                    indoor=classify.tour_is_indoor(p),
                    distance_m=p.distance_m,
                )
            )
        return result

    def _play_candidates(self, origin: LatLng, radius: int) -> list[_Candidate]:
        places = {
            p.place_id: p
            for word in classify.PLAY_KEYWORDS
            for p in self._kakao.by_keyword(word, origin.latitude, origin.longitude, radius)
        }
        return [
            _Candidate(place=_kakao_place(p), theme=Theme.PLAY, indoor=True, distance_m=p.distance_m)
            for p in places.values()
        ]

    def _interest_candidates(
        self, origin: LatLng, radius: int, wanted: list[str], existing: list[_Candidate]
    ) -> list[_Candidate]:
        """관심사로 카카오 장소를 검색해 나들이 장소 분류에 해당하는 것만 후보로 더한다."""
        seen = {(c.place.source, c.place.source_id) for c in existing}
        result = []
        for interest in wanted[:MAX_INTEREST_SEARCHES]:
            query = interests.search_query(interest)
            for p in self._kakao.by_keyword(query, origin.latitude, origin.longitude, radius):
                kind = classify.kakao_outing_kind(p)
                if kind is None or ("kakao", p.place_id) in seen:
                    continue
                seen.add(("kakao", p.place_id))
                theme, indoor = kind
                result.append(_Candidate(place=_kakao_place(p), theme=theme, indoor=indoor, distance_m=p.distance_m))
        return result

    def _nearby_kakao(self, code: str, center: Place, radius: int, suitable) -> list[Place]:
        found = self._kakao.by_category(code, center.latitude, center.longitude, radius)
        return [_kakao_place(p) for p in found if suitable(p)]

    # 시간표

    def _departure(self, hhmm: str | None) -> datetime:
        now = self._now or datetime.now(KST)
        if hhmm:
            h, m = map(int, hhmm.split(":"))
            return now.replace(hour=h, minute=m, second=0, microsecond=0)
        # 10분 단위로 올림
        rounded = now.replace(second=0, microsecond=0) + timedelta(minutes=(10 - now.minute % 10) % 10)
        return rounded

    def _schedule(
        self,
        origin: LatLng,
        home: LatLng,
        stops: list[tuple[StopRole, Place]],
        departure: datetime,
        cond: TripConditions,
    ) -> CourseResponse:
        points = [(origin.latitude, origin.longitude)]
        points += [(p.latitude, p.longitude) for _, p in stops]
        points.append((home.latitude, home.longitude))
        legs, route_source = self._route(points)

        result: list[Stop] = []
        clock = departure
        for (role, place), leg in zip(stops, legs):
            drive = math.ceil(leg.duration_s / 60)
            clock += timedelta(minutes=drive)
            stay = _stay_minutes(role, cond)
            arrive = clock
            clock += timedelta(minutes=stay)
            result.append(
                Stop(
                    role=role,
                    place=place,
                    arrive_at=arrive.strftime("%H:%M"),
                    depart_at=clock.strftime("%H:%M"),
                    stay_minutes=stay,
                    drive_minutes=drive,
                    drive_distance_m=leg.distance_m,
                )
            )

        last = legs[-1]
        drive_home = math.ceil(last.duration_s / 60)
        clock += timedelta(minutes=drive_home)
        result.append(
            Stop(
                role=StopRole.HOME,
                place=Place(name="귀가", latitude=home.latitude, longitude=home.longitude, source="user", source_id="home"),
                arrive_at=clock.strftime("%H:%M"),
                drive_minutes=drive_home,
                drive_distance_m=last.distance_m,
            )
        )
        return CourseResponse(
            departure_at=departure.strftime("%H:%M"),
            stops=result,
            total_drive_minutes=sum(s.drive_minutes for s in result),
            return_at=clock.strftime("%H:%M"),
            route_source=route_source,
        )

    def _route(self, points: list[tuple[float, float]]) -> tuple[list[Leg], str]:
        try:
            legs = self._kakao.route(points)
            if len(legs) == len(points) - 1:
                return legs, "kakao"
        except Exception:  # noqa: BLE001 - 길찾기 실패 시 추정으로 대체
            pass
        return [_estimate_leg(a, b) for a, b in zip(points, points[1:])], "estimate"

    @staticmethod
    def _late(course: CourseResponse, departure: datetime, return_by_hour: int) -> bool:
        h, m = map(int, course.return_at.split(":"))
        back = departure.replace(hour=h, minute=m)
        if back < departure:  # 자정을 넘긴 경우
            back += timedelta(days=1)
        return back > departure.replace(hour=return_by_hour, minute=0)


def _score(c: _Candidate, cond: TripConditions, radius: int) -> float:
    score = 0.0
    if cond.themes and c.theme in cond.themes:
        score += 3
    if c.place.image_url:
        score += 0.5  # 정보가 충실한 장소를 우선
    score -= 2 * min(c.distance_m / radius, 1)  # 가까울수록 좋음
    if cond.child_ages and min(cond.child_ages) <= 2 and c.indoor:
        score += 0.5  # 영아는 실내가 편하다
    text = f"{c.place.name} {c.place.category}"
    if any(term in text for i in cond.interests for term in interests.terms(i)):
        score += 3  # 아이 관심사와 맞으면 테마 일치만큼 가점
    return score


def _stay_minutes(role: StopRole, cond: TripConditions) -> int:
    if role == StopRole.OUTING and cond.child_ages and min(cond.child_ages) <= 2:
        return INFANT_OUTING_MINUTES
    return STAY_MINUTES[role]


def _attach_amenities(place: Place, raw: dict[str, str]) -> None:
    for name in ("stroller", "nursing_room", "kids_chair"):
        text = raw.get(name, "")
        place.amenities[name] = Amenity.AVAILABLE if text else Amenity.UNKNOWN
        if text:
            place.amenity_notes[name] = text
    if raw.get("infant_etc"):
        place.amenity_notes["infant_etc"] = raw["infant_etc"]


def _tour_place(p: TourPlace) -> Place:
    return Place(
        name=p.title,
        latitude=p.latitude,
        longitude=p.longitude,
        address=p.address,
        category=p.cat3,
        source="tour",
        source_id=p.content_id,
        image_url=p.image_url,
    )


def _kakao_place(p: KakaoPlace) -> Place:
    return Place(
        name=p.name,
        latitude=p.latitude,
        longitude=p.longitude,
        address=p.address,
        category=p.category,
        source="kakao",
        source_id=p.place_id,
    )


def _estimate_leg(a: tuple[float, float], b: tuple[float, float]) -> Leg:
    meters = _haversine_m(a, b) * 1.4  # 도로 우회 계수
    return Leg(distance_m=round(meters), duration_s=round(meters / METERS_PER_DRIVE_MINUTE * 60))


def _haversine_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, (*a, *b))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))

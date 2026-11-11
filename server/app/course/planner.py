"""식사 → 나들이 → 카페 → 귀가 코스를 만든다.

나들이 장소를 먼저 고르고(코스의 중심), 그 근처에서 식사와 카페를 찾는다.
LLM은 쓰지 않는다. 조건 해석은 /parse가, 여기서는 규칙으로만 검증·조합한다.
"""

import logging
import math
import random
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import Protocol
from zoneinfo import ZoneInfo

from app import interests
from app.course import classify, fuel, jelly, parking_rank, ranking, wide_area
from app.schemas import (
    Alternative,
    Amenity,
    CourseOption,
    CourseRequest,
    CourseResponse,
    FuelRequest,
    FuelStation,
    LatLng,
    Place,
    ScheduleRequest,
    Stop,
    StopRole,
    Theme,
    TripConditions,
    VisitSummary,
)
from app.sources.parking import ParkingPlace
from app.sources.public_data import PublicData
from app.sources.kakao import CATEGORY_CAFE, CATEGORY_PARKING, CATEGORY_RESTAURANT, KakaoPlace, Leg
from app.sources.tour import CONTENT_TYPE_ATTRACTION, CONTENT_TYPE_CULTURE, CONTENT_TYPE_LEISURE, PET, TourPlace

KST = ZoneInfo("Asia/Seoul")

log = logging.getLogger("course")

DEFAULT_RADIUS_M = 10_000
MAX_RADIUS_M = 50_000  # 홈 반경 5~50km (Figma H01-2). 20km를 넘으면 wide_area가 여러 중심점으로 나눠 찾는다 (#250)
METERS_PER_DRIVE_MINUTE = 500  # 시내 평균 30km/h 가정
NEARBY_MEAL_RADIUS_M = 2_000
NEARBY_CAFE_RADIUS_M = 1_500
AMENITY_LOOKUPS = 6  # 후보 상위 몇 곳만 무장애 상세를 조회한다 (API 한도)
MIN_PET_CANDIDATES = 5  # 반려견 동반 장소가 이보다 적으면 일반 장소도 후보로 낸다
ALTERNATIVES = 3
PUBLIC_FACILITY_LIMIT = 10  # 박물관·미술관·도서관은 가까운 10곳까지 후보로
SUGGESTIONS = 3  # 홈에 보여 줄 추천 코스 수
MAX_INTEREST_SEARCHES = 3  # 관심사 키워드 검색은 앞의 3개까지 (카카오 호출 수)
DETOUR_RATIO_RANGE = (0.5, 2.0)  # 실제 길찾기/추정 시간 비율 보정의 한계 (이상치 방지)

STAY_MINUTES = {StopRole.MEAL: 60, StopRole.OUTING: 120, StopRole.CAFE: 40, StopRole.FUEL: fuel.FUEL_STAY_MINUTES}
INFANT_OUTING_MINUTES = 90  # 만 2세 이하는 나들이를 짧게


class TourSource(Protocol):
    def nearby(
        self, latitude: float, longitude: float, radius_m: int, content_type_id: int | None = None,
        service: str = ..., rows: int = ...,
    ) -> list[TourPlace]: ...

    def festivals(self, today: date, latitude: float, longitude: float, radius_m: int) -> list[TourPlace]: ...

    def amenities(self, content_id: str) -> dict[str, str]: ...

    def parking(self, content_id: str, content_type_id: int) -> str: ...


class ParkingSource(Protocol):
    def near(self, latitude: float, longitude: float, radius_m: int, limit: int, today: date) -> list[ParkingPlace]: ...


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
    content_type_id: int = 0  # TourAPI 분류 (주차 정보 조회에 쓴다)
    festival: bool = False
    library: bool = False  # 도서관은 목적지로는 약해 점수를 조금 낮춘다
    looked_up: bool = False  # 육아 편의 상세를 이미 조회했는지

    @property
    def key(self) -> str:
        return f"{self.place.source}:{self.place.source_id}"


class CoursePlanner:
    def __init__(
        self,
        tour: TourSource,
        kakao: KakaoSource,
        now: datetime | None = None,
        public: PublicData | None = None,
        parking: ParkingSource | None = None,
    ):
        self._public = public
        self._parking = parking
        self._parking_cache: dict[tuple[float, float, date], list[ParkingPlace]] = {}
        self._tour = tour
        self._kakao = kakao
        self._now = now

    def plan(self, req: CourseRequest) -> CourseResponse:
        rng = _rng(req.seed)
        outings, notes = self._pick_outings(req, rng)
        if not outings:
            log.info("나들이 후보 없음 → 404")
            raise NoCourseError("조건에 맞는 나들이 장소를 찾지 못했어요")
        log.info(
            "나들이 후보 %d곳, 선택 순서: %s",
            len(outings),
            ", ".join(f"{c.place.name}({c.score:.1f}, {c.distance_m / 1000:.1f}km)" for c in outings[:3]),
        )
        course = self._around(req, outings, 0, rng)
        course.warnings = notes + course.warnings
        return course

    def suggest(self, req: CourseRequest) -> list[CourseOption]:
        """성격이 다른 코스 후보를 최대 3개 만든다 (자연·전시·놀이·체험 중 점수가 높은 나들이 장소 기준)."""
        rng = _rng(req.seed)
        outings, notes = self._pick_outings(req, rng)
        if not outings:
            raise NoCourseError("조건에 맞는 나들이 장소를 찾지 못했어요")
        picks = _pick_varied(outings, SUGGESTIONS)
        options = []
        for index, label in picks:
            course = self._around(req, outings, index, rng)
            course.warnings = notes + course.warnings
            options.append(_option(course, outings[index], label))
        log.info("추천 %d개: %s", len(options), " / ".join(o.name for o in options))
        return options

    def _pick_outings(self, req: CourseRequest, rng: random.Random) -> tuple[list[_Candidate], list[str]]:
        """점수 계산 → 이미 보여 준 곳 제외 → 상위 후보 가중 선택 순서. 두 번째 값은 사용자에게 알릴 안내."""
        ranked = self._rank_outings(req.origin, req.conditions, req.visits, self._today())
        notes: list[str] = []
        if req.exclude_place_keys:
            skip = set(req.exclude_place_keys)
            rest = [c for c in ranked if c.key not in skip]
            if rest:
                ranked = rest
            elif ranked:
                # 후보를 모두 보여 준 뒤에는 처음부터 다시 돈다
                notes.append("근처 후보를 모두 보여 드려서 처음부터 다시 추천해요")
        return ranking.weighted_order(ranked, lambda c: c.score, rng), notes

    def _today(self) -> date:
        return (self._now or datetime.now(KST)).date()

    def _around(self, req: CourseRequest, outings: list[_Candidate], index: int, rng: random.Random) -> CourseResponse:
        """고른 나들이 장소 둘레에서 식사·카페를 찾아 시간표를 만든다."""
        cond = req.conditions
        origin = req.origin
        home = req.home or origin
        warnings: list[str] = []
        self._ensure_amenities(outings[index], cond)
        outing = outings[index].place
        others = [c.place for i, c in enumerate(outings) if i != index]

        meals = self._eateries(CATEGORY_RESTAURANT, outing, NEARBY_MEAL_RADIUS_M, classify.restaurant_is_suitable,
                               ranking.MEAL_WORDS, req.visits, rng)
        cafes = self._eateries(CATEGORY_CAFE, outing, NEARBY_CAFE_RADIUS_M, classify.cafe_is_suitable,
                               ranking.CAFE_WORDS, req.visits, rng)
        # 키즈카페처럼 나들이 장소가 식당·카페로도 잡히면 같은 곳을 두 번 넣지 않는다.
        meals = [p for p in meals if p.name != outing.name]
        cafes = [p for p in cafes if p.name != outing.name]
        log.info("근처 식당 %d곳, 카페 %d곳 (거른 뒤)", len(meals), len(cafes))
        if not meals:
            warnings.append("나들이 장소 근처에서 식당을 찾지 못해 식사를 뺐어요")

        stops = [(StopRole.MEAL, meals[0])] if meals else []
        stops.append((StopRole.OUTING, outing))
        if cafes:
            stops.append((StopRole.CAFE, cafes[0]))

        departure = self._departure(req.departure_time)
        course = self._schedule(origin, home, stops, departure, cond)
        stops, course = self._with_fuel(req, stops, course, departure, warnings)

        if cond.return_by_hour is not None and self._late(course, departure, cond.return_by_hour):
            if any(role == StopRole.CAFE for role, _ in stops):
                stops = [s for s in stops if s[0] != StopRole.CAFE]
                course = self._schedule(origin, home, stops, departure, cond, _stays(stops, req.ev))
                warnings.append(f"{cond.return_by_hour}시 귀가를 맞추려고 카페를 뺐어요")
            if self._late(course, departure, cond.return_by_hour):
                warnings.append(f"{cond.return_by_hour}시까지 귀가하기 어려워요 (예상 {course.return_at})")

        course.alternatives = _with_detours(course, origin, {
            StopRole.OUTING: others[:ALTERNATIVES],
            StopRole.MEAL: meals[1 : 1 + ALTERNATIVES],
            StopRole.CAFE: cafes[1 : 1 + ALTERNATIVES],
        })
        course.warnings = warnings
        log.info("코스: %s", describe(course))
        return course

    def _with_fuel(
        self,
        req: CourseRequest,
        stops: list[tuple[StopRole, Place]],
        course: CourseResponse,
        departure: datetime,
        warnings: list[str],
    ) -> tuple[list[tuple[StopRole, Place]], CourseResponse]:
        """주행 가능 거리가 코스(귀가 포함) × 여유율보다 짧으면 출발지 근처 주유소·충전소를 첫 경유지로 넣는다 (QP1).

        다음 지점 가는 길에서 가장 덜 돌아가는 곳을 고른다. 못 찾거나 검색이 실패하면 경고만 남기고 코스는 그대로 준다.
        """
        course_m = sum(s.drive_distance_m for s in course.stops)
        if not stops or not fuel.needs_refuel(course_m, req.range_m):
            return stops, course
        kind = "충전소" if req.ev else "주유소"
        range_km = round((req.range_m or 0) / 1000)
        first = stops[0][1]
        pick = self._fuel_pick(req.origin, LatLng(latitude=first.latitude, longitude=first.longitude), req.ev)
        if pick is None:
            warnings.append(f"주행 가능 거리 {range_km}km로는 코스 {round(course_m / 1000)}km가 빠듯해요. 출발 전에 {kind}에 들러 주세요")
            return stops, course
        stops = [(StopRole.FUEL, pick.place)] + stops
        course = self._schedule(req.origin, req.home or req.origin, stops, departure, req.conditions, _stays(stops, req.ev))
        warnings.append(f"주행 가능 거리 {range_km}km로는 코스 {round(course_m / 1000)}km가 빠듯해 첫 경유지로 {kind}를 넣었어요")
        log.info("연료 부족(%dkm < %dkm × %.1f) → %s 넣음: %s", range_km, round(course_m / 1000), fuel.FUEL_MARGIN, kind, pick.place.name)
        return stops, course

    def fuel_station(self, req: FuelRequest) -> FuelStation | None:
        """홈 연료 타일의 [주유소 찾기]: 출발지 근처(다음 지점이 있으면 그 길에서 덜 돌아가는) 주유소·충전소 1곳."""
        return self._fuel_pick(req.origin, req.toward, req.ev)

    def _fuel_pick(self, origin: LatLng, toward: LatLng | None, ev: bool) -> FuelStation | None:
        try:
            if ev:
                found = self._kakao.by_keyword(fuel.EV_KEYWORD, origin.latitude, origin.longitude, fuel.SEARCH_RADIUS_M)
            else:
                found = self._kakao.by_category(fuel.CATEGORY_FUEL, origin.latitude, origin.longitude, fuel.SEARCH_RADIUS_M)
        except Exception as e:  # noqa: BLE001 - 주유소 검색 실패가 코스 생성을 막지 않는다
            log.warning("주유소·충전소 검색 실패: %s", type(e).__name__)
            return None
        by_key = {p.place_id: p for p in found}
        candidates = [
            fuel.StationCandidate(key=p.place_id, name=p.name, category=p.category, latitude=p.latitude, longitude=p.longitude)
            for p in found
        ]
        pick = fuel.pick_station(
            (origin.latitude, origin.longitude),
            (toward.latitude, toward.longitude) if toward else None,
            candidates,
            ev,
        )
        if pick is None:
            return None
        return FuelStation(
            place=_kakao_place(by_key[pick.candidate.key]),
            distance_m=pick.distance_m,
            drive_minutes=pick.drive_minutes,
            on_route=pick.on_route,
        )

    def reschedule(self, req: ScheduleRequest) -> CourseResponse:
        """사용자가 고친 장소·순서·체류 시간 그대로 이동 시간과 시간표를 다시 계산한다."""
        cond = req.conditions
        departure = self._departure(req.departure_time)
        given = [s for s in req.stops if s.role != StopRole.HOME]
        if not given:
            raise NoCourseError("방문할 장소가 없어요")
        stops = [(s.role, s.place) for s in given]
        stays = [s.stay_minutes for s in given]
        course = self._schedule(req.origin, req.home or req.origin, stops, departure, cond, stays)
        if cond.return_by_hour is not None and self._late(course, departure, cond.return_by_hour):
            course.warnings = [f"{cond.return_by_hour}시까지 귀가하기 어려워요 (예상 {course.return_at})"]
        if req.suggest_nearby:
            found = self._nearby_alternatives(stops, req.origin, cond)
            course.alternatives = _with_detours(course, req.origin, found)
        log.info("재계산: %s", describe(course))
        return course

    def _nearby_alternatives(
        self, stops: list[tuple[StopRole, Place]], origin: LatLng, cond: TripConditions
    ) -> dict[StopRole, list[Place]]:
        """나들이 장소 근처 식사·카페 후보와 다른 나들이 후보. 이미 코스에 있는 곳은 뺀다."""
        outing = next((p for role, p in stops if role == StopRole.OUTING), None)
        if outing is None:
            return {}
        taken = {p.source_id for _, p in stops}
        meals = self._nearby_kakao(CATEGORY_RESTAURANT, outing, NEARBY_MEAL_RADIUS_M, classify.restaurant_is_suitable)
        cafes = self._nearby_kakao(CATEGORY_CAFE, outing, NEARBY_CAFE_RADIUS_M, classify.cafe_is_suitable)
        return {
            StopRole.OUTING: self._outing_alternatives(stops, origin, cond),
            StopRole.MEAL: [p for p in meals if p.source_id not in taken][:ALTERNATIVES],
            StopRole.CAFE: [p for p in cafes if p.source_id not in taken][:ALTERNATIVES],
        }

    def _outing_alternatives(
        self, stops: list[tuple[StopRole, Place]], origin: LatLng, cond: TripConditions
    ) -> list[Place]:
        """재계산 뒤 나들이 교체 후보: 출발지 기준 후보 점수 순으로, 코스에 이미 있는 곳은 뺀다.

        TourAPI·카카오 응답은 메모리 캐시에 있어 직전 /course와 같은 출발지·조건이면 외부 호출이 늘지 않는다.
        후보를 못 가져와도 재계산 결과는 그대로 준다.
        """
        try:
            ranked = self._rank_outings(origin, cond, None, self._today())
        except Exception as e:  # noqa: BLE001 - 후보 조회 장애가 재계산을 막지 않는다
            log.warning("재계산 나들이 후보를 못 가져옴: %s", type(e).__name__)
            return []
        taken = {(p.source, p.source_id) for _, p in stops}
        return [c.place for c in ranked if (c.place.source, c.place.source_id) not in taken][:ALTERNATIVES]

    # 후보 수집과 점수

    def _rank_outings(
        self, origin: LatLng, cond: TripConditions, visits: list[VisitSummary] | None = None, today: date | None = None
    ) -> list[_Candidate]:
        radius = DEFAULT_RADIUS_M
        if cond.max_drive_minutes:
            radius = min(MAX_RADIUS_M, cond.max_drive_minutes * METERS_PER_DRIVE_MINUTE)

        if cond.with_pet:
            candidates = self._pet_candidates(origin, radius, cond)
        else:
            candidates = self._general_candidates(origin, radius, cond)
        if cond.indoor_only:
            candidates = [c for c in candidates if c.indoor]

        for c in candidates:
            c.score = _score(c, cond, radius)
            if visits:
                c.score += ranking.visit_adjustment(c.key, c.theme, visits, today or self._today())
            c.place.theme = c.theme  # 방문 기록에서 경험 분야로 쓴다
            if not c.festival:  # 행사는 만들 때 분류 코드(EV…)로 정했고, category가 "행사·축제"로 바뀌어 다시 정하지 않는다
                c.place.jelly = jelly.judge(c.place.name, c.place.category, c.theme)
        candidates.sort(key=lambda c: c.score, reverse=True)

        # 상위 후보만 육아 편의 정보를 붙이고, 필요 조건이 있으면 그 결과로 다시 정렬한다.
        for c in candidates[:AMENITY_LOOKUPS]:
            self._lookup_amenities(c)
        for c in candidates:
            if c.theme == Theme.PLAY:  # 키즈카페 등 놀이시설은 키즈존(놀이방)이 있는 곳
                c.place.amenities["kids_zone"] = Amenity.AVAILABLE
        if cond.needs:
            if "parking" in cond.needs:
                for c in candidates[:AMENITY_LOOKUPS]:
                    self._lookup_parking(c)
            for c in candidates:
                c.score += 2 * sum(c.place.amenities.get(n) == Amenity.AVAILABLE for n in cond.needs)
            candidates.sort(key=lambda c: c.score, reverse=True)
        return candidates

    def _lookup_amenities(self, c: _Candidate) -> None:
        if c.place.source == "tour" and not c.looked_up:
            c.looked_up = True
            _attach_amenities(c.place, self._tour.amenities(c.place.source_id))

    def _lookup_parking(self, c: _Candidate) -> None:
        """주차 필요 조건. 주차장 표에 가까운 곳이 있으면 그걸 쓰고(외부 API 호출 없음), 없을 때만 TourAPI 정보로 대체한다."""
        if "parking" in c.place.amenities:
            return
        lots = self._lots_near(c.place, self._today())
        if lots:
            c.place.amenities["parking"] = Amenity.AVAILABLE
            c.place.amenity_notes["parking"] = f"근처 주차장 {len(lots)}곳 (가장 가까운 곳 {lots[0].distance_m}m)"
        elif c.place.source == "tour":
            _attach_parking(c.place, self._tour.parking(c.place.source_id, c.content_type_id))

    def _lots_near(self, place: Place, today: date) -> list[ParkingPlace]:
        """장소 둘레 주차장 후보(가까운 순). 같은 요청 안에서 같은 장소는 한 번만 조회한다. 조회 실패는 빈 목록."""
        if self._parking is None:
            return []
        key = (round(place.latitude, 5), round(place.longitude, 5), today)
        if key not in self._parking_cache:
            lots: list[ParkingPlace] = []
            if self._parking is not None:
                try:
                    lots = self._parking.near(
                        place.latitude, place.longitude, parking_rank.PARKING_RADIUS_M, parking_rank.PARKING_FETCH, today
                    )
                except Exception as e:  # noqa: BLE001 - 주차장 조회 실패가 코스 생성을 막지 않는다
                    log.warning("주차장 조회 실패: %s", type(e).__name__)
            if len(lots) < parking_rank.PARKING_PER_STOP:
                lots = lots + self._kakao_lots(place, lots)
            self._parking_cache[key] = lots
        return self._parking_cache[key]

    def _kakao_lots(self, place: Place, known: list[ParkingPlace]) -> list[ParkingPlace]:
        """주차장 표에 없는 곳(건물 부설 등)을 카카오 주차장 분류(PK6)로 채운다 (#250).

        카카오에는 요금·운영시간·면수가 없어 빈 값으로 둔다(앱이 '정보 없음'으로 쓴다). 표에 있는 곳과 겹치면 뺀다.
        """
        try:
            found = self._kakao.by_category(
                CATEGORY_PARKING, place.latitude, place.longitude, parking_rank.PARKING_RADIUS_M, size=parking_rank.PARKING_FETCH
            )
        except Exception as e:  # noqa: BLE001 - 카카오 장애가 코스 생성을 막지 않는다
            log.warning("카카오 주차장 검색 실패: %s", type(e).__name__)
            return []
        return [lot for p in found if (lot := parking_rank.from_kakao(p)) and not parking_rank.is_duplicate(lot, known)]

    def _ensure_amenities(self, c: _Candidate, cond: TripConditions) -> None:
        """가중 선택으로 상위 조회 범위 밖의 후보가 뽑혀도 편의 정보를 붙인다."""
        self._lookup_amenities(c)
        if "parking" in cond.needs:
            self._lookup_parking(c)

    def _tour_nearby(self, origin: LatLng, radius: int, **kw) -> list[TourPlace]:
        """TourAPI 위치기반 목록. 20km를 넘으면 여러 중심점으로 나눠 모은다 (wide_area)."""
        return wide_area.gather(
            origin.latitude, origin.longitude, radius,
            lambda lat, lng, r: self._tour.nearby(lat, lng, r, **kw), key=lambda p: p.content_id,
        )

    def _kakao_keyword(self, query: str, origin: LatLng, radius: int) -> list[KakaoPlace]:
        """카카오 키워드 검색. 20km를 넘으면 여러 중심점으로 나눠 모은다 (wide_area)."""
        return wide_area.gather(
            origin.latitude, origin.longitude, radius,
            lambda lat, lng, r: self._kakao.by_keyword(query, lat, lng, r), key=lambda p: p.place_id,
        )

    def _general_candidates(self, origin: LatLng, radius: int, cond: TripConditions) -> list[_Candidate]:
        candidates = self._tour_candidates(origin, radius, with_pet=False)
        candidates += self._play_candidates(origin, radius)
        candidates += self._leisure_candidates(origin, radius)
        candidates += self._festival_candidates(origin, radius)
        candidates += self._public_candidates(origin, radius, candidates, cond)
        candidates += self._interest_candidates(origin, radius, cond.interests, candidates)
        return candidates

    def _public_candidates(self, origin: LatLng, radius: int, existing: list[_Candidate], cond: TripConditions) -> list[_Candidate]:
        """공공데이터포털: 놀이터가 있는 공공 공원, 박물관·미술관·도서관, 오늘 열리는 아이 동반 전시·공연.

        소스 하나가 실패해도 코스는 나온다. 이미 있는 장소(이름이 같은 것)는 건너뛴다.
        """
        if self._public is None:
            return []
        seen = {classify.normalize_name(c.place.name) for c in existing}
        result: list[_Candidate] = []

        def add(c: _Candidate) -> None:
            key = classify.normalize_name(c.place.name)
            if key and key not in seen:
                seen.add(key)
                result.append(c)

        try:
            for p in classify.best_playgrounds(self._public.playgrounds(origin.latitude, origin.longitude, radius)):
                place = Place(name=p.name, latitude=p.latitude, longitude=p.longitude, address=p.address, category=p.group,
                              source="public", source_id=p.source_id)
                place.amenities["kids_zone"] = Amenity.AVAILABLE  # 어린이놀이시설이 등록된 곳
                add(_Candidate(place=place, theme=Theme.NATURE, indoor=p.indoor, distance_m=p.distance_m, looked_up=True))
            for p in self._public.facilities(origin.latitude, origin.longitude, radius)[:PUBLIC_FACILITY_LIMIT]:
                kind = classify.culture_facility_theme(p.group, p.name)
                if kind is None:
                    continue
                place = Place(name=p.name, latitude=p.latitude, longitude=p.longitude, category=p.group,
                              source="public", source_id=p.source_id)
                c = _Candidate(place=place, theme=kind[0], indoor=kind[1], distance_m=p.distance_m, looked_up=True)
                if p.group == "도서관":
                    c.library = True
                add(c)
            for e in self._public.events(self._today(), origin.latitude, origin.longitude, radius):
                if not classify.culture_event_is_family(e.title, e.place):
                    continue
                place = Place(name=e.title, latitude=e.latitude, longitude=e.longitude, address=e.place, category=f"{e.realm} 행사",
                              source="public", source_id=f"event{e.seq}", image_url=e.image_url,
                              event_period=f"{_dash(e.start)}~{_dash(e.end)}")
                add(_Candidate(place=place, theme=Theme.EXHIBITION, indoor=True, distance_m=e.distance_m, festival=True, looked_up=True))
        except Exception as e:  # noqa: BLE001 - 공공데이터 장애가 코스 생성을 막지 않는다
            log.warning("공공데이터 후보 일부를 못 가져옴: %s", type(e).__name__)
        log.info("공공데이터 후보 %d곳 추가", len(result))
        return result

    def _leisure_candidates(self, origin: LatLng, radius: int) -> list[_Candidate]:
        """레포츠 중 스케이트장·물놀이장·눈썰매장처럼 아이와 갈 만한 곳."""
        result = []
        for p in self._tour_nearby(origin, radius, content_type_id=CONTENT_TYPE_LEISURE):
            kind = classify.leisure_kind(p)
            if kind is None:
                continue
            theme, indoor = kind
            result.append(
                _Candidate(place=_tour_place(p), theme=theme, indoor=indoor, distance_m=p.distance_m, content_type_id=p.content_type_id)
            )
        return result

    def _festival_candidates(self, origin: LatLng, radius: int) -> list[_Candidate]:
        """오늘 열리는 행사·축제. 점수에서 가점을 받아 상단에 오른다 (#48)."""
        result = []
        for p in self._tour.festivals(self._today(), origin.latitude, origin.longitude, radius):
            if not classify.event_is_suitable(p):
                continue
            place = _tour_place(p)
            place.category = "행사·축제"
            place.event_period = f"{_dash(p.event_start)}~{_dash(p.event_end)}"
            result.append(
                _Candidate(place=place, theme=Theme.EXPERIENCE, indoor=False, distance_m=p.distance_m,
                           content_type_id=p.content_type_id, festival=True)
            )
        return result

    def _pet_candidates(self, origin: LatLng, radius: int, cond: TripConditions) -> list[_Candidate]:
        """반려견 동반 장소를 먼저 쓰고, 너무 적으면 일반 장소도 '동반 정보 없음'으로 함께 낸다.

        반려동물 동반 여행 정보(KorPetTourService2)는 지역에 따라 몇 곳뿐이라
        그것만 쓰면 아이 조건·관심사와 거의 맞지 않는 코스가 나온다.
        """
        pets = self._tour_candidates(origin, radius, with_pet=True)
        for c in pets:
            c.place.amenities["pet"] = Amenity.AVAILABLE
        if len(pets) >= MIN_PET_CANDIDATES:
            return pets
        taken = {c.place.source_id for c in pets}
        extra = [c for c in self._general_candidates(origin, radius, cond) if c.place.source_id not in taken]
        for c in extra:
            c.place.amenities["pet"] = Amenity.UNKNOWN
        log.info("반려견 동반 후보 %d곳뿐이라 일반 장소 %d곳을 '동반 정보 없음'으로 더함", len(pets), len(extra))
        return pets + extra

    def _tour_candidates(self, origin: LatLng, radius: int, with_pet: bool) -> list[_Candidate]:
        if with_pet:
            places = self._tour_nearby(origin, radius, service=PET)
        else:
            places = [
                p
                for type_id in (CONTENT_TYPE_ATTRACTION, CONTENT_TYPE_CULTURE)
                for p in self._tour_nearby(origin, radius, content_type_id=type_id)
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
                    content_type_id=p.content_type_id,
                )
            )
        return result

    def _play_candidates(self, origin: LatLng, radius: int) -> list[_Candidate]:
        places = {
            p.place_id: p
            for word in classify.PLAY_KEYWORDS
            for p in self._kakao_keyword(word, origin, radius)
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
            for p in self._kakao_keyword(query, origin, radius):
                kind = classify.kakao_outing_kind(p)
                if kind is None or ("kakao", p.place_id) in seen:
                    continue
                seen.add(("kakao", p.place_id))
                theme, indoor = kind
                result.append(_Candidate(place=_kakao_place(p), theme=theme, indoor=indoor, distance_m=p.distance_m))
        return result

    def _eateries(self, code, center, radius, suitable, words, visits, rng) -> list[Place]:
        """근처 식당·카페를 점수(거리·키즈 편의·분류·최근 방문)로 매기고 상위에서 가중 선택한 순서로 준다."""
        found = [p for p in self._kakao.by_category(code, center.latitude, center.longitude, radius) if suitable(p)]
        today = self._today()

        def score(p: KakaoPlace) -> float:
            s = ranking.eatery_score(p.distance_m, radius, p.category, p.name, words)
            return s + ranking.visit_adjustment(f"kakao:{p.place_id}", None, visits, today)

        return [_kakao_place(p) for p in ranking.weighted_order(found, score, rng)]

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
        stays: list[int | None] | None = None,
    ) -> CourseResponse:
        points = [(origin.latitude, origin.longitude)]
        points += [(p.latitude, p.longitude) for _, p in stops]
        points.append((home.latitude, home.longitude))
        legs, route_source = self._route(points)

        result: list[Stop] = []
        clock = departure
        infant = cond.has_infant()
        for i, ((role, place), leg) in enumerate(zip(stops, legs)):
            drive = math.ceil(leg.duration_s / 60)
            clock += timedelta(minutes=drive)
            given = stays[i] if stays else None
            stay = given if given is not None else _stay_minutes(role, cond)
            arrive = clock
            clock += timedelta(minutes=stay)
            arrive_hhmm = arrive.strftime("%H:%M")
            result.append(
                Stop(
                    role=role,
                    place=place,
                    parking=parking_rank.rank(self._lots_near(place, arrive.date()), arrive_hhmm, infant),
                    arrive_at=arrive_hhmm,
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
        path = [LatLng(latitude=lat, longitude=lng) for leg in legs for lat, lng in leg.path]
        return CourseResponse(
            departure_at=departure.strftime("%H:%M"),
            path=path,
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


def _rng(seed: int | None) -> random.Random:
    if seed is None:
        seed = random.SystemRandom().randrange(2**31)
    log.info("선택 시드 %d", seed)
    return random.Random(seed)


def describe(course: CourseResponse) -> str:
    """로그용 한 줄 요약: 장소 순서, 출발·귀가, 이동 시간, 길찾기 출처, 경고."""
    route = " → ".join(f"{s.place.name}({s.arrive_at})" for s in course.stops)
    text = f"{course.departure_at} 출발 {route}, 이동 {course.total_drive_minutes}분, 길찾기={course.route_source}"
    if course.warnings:
        text += " ⚠ " + " / ".join(course.warnings)
    return text


def _score(c: _Candidate, cond: TripConditions, radius: int) -> float:
    score = ranking.FESTIVAL_BONUS if c.festival else 0.0
    if c.library:
        score -= 0.5
    if cond.themes and c.theme in cond.themes:
        score += 3
    if c.place.image_url:
        score += 0.5  # 정보가 충실한 장소를 우선
    score -= ranking.DISTANCE_WEIGHT * min(c.distance_m / radius, 1)  # 가까울수록 좋음 (비중은 낮게)
    if c.place.amenities.get("kids_zone") == Amenity.AVAILABLE and (cond.child_ages or cond.child_age_months):
        score += 0.4  # 놀이시설이 등록된 곳은 아이 동반에 맞다 (공공데이터 장소는 사진이 없어 받는 +0.5를 보완)
    if cond.has_infant() and c.indoor:
        score += 0.5  # 영아는 실내가 편하다
    if cond.with_pet and c.place.amenities.get("pet") == Amenity.AVAILABLE:
        score += 2  # 동반 가능이 확인된 곳을 먼저
    text = f"{c.place.name} {c.place.category}"
    if any(term in text for i in cond.interests for term in interests.terms(i)):
        score += 3  # 아이 관심사와 맞으면 테마 일치만큼 가점
    return score


def _dash(ymd: str) -> str:
    return f"{ymd[:4]}-{ymd[4:6]}-{ymd[6:]}"


def _stays(stops: list[tuple[StopRole, Place]], ev: bool) -> list[int | None]:
    """주유는 10분, 전기차 충전은 30분. 나머지는 역할 기본값(None)."""
    return [fuel.stay_minutes(ev) if role == StopRole.FUEL else None for role, _ in stops]


def _stay_minutes(role: StopRole, cond: TripConditions) -> int:
    if role == StopRole.OUTING and cond.has_infant():
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


def _attach_parking(place: Place, text: str) -> None:
    """주차 안내가 적혀 있으면 있음(불가·없음이라고 적힌 경우는 제외), 비었으면 정보 없음."""
    available = bool(text) and not any(w in text for w in ("불가", "없음", "없습니다", "불가능"))
    place.amenities["parking"] = Amenity.AVAILABLE if available else Amenity.UNKNOWN
    if text:
        place.amenity_notes["parking"] = text


def _tour_place(p: TourPlace) -> Place:
    return Place(
        name=p.title,
        latitude=p.latitude,
        longitude=p.longitude,
        address=p.address,
        category=p.cat3 or p.lcls3,  # 행사는 cat3가 비고 새 분류체계(EV…)만 온다
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


def _with_detours(
    course: CourseResponse, origin: LatLng, candidates: dict[StopRole, list[Place]]
) -> dict[StopRole, list[Alternative]]:
    """후보마다 교체 대상에서의 직선거리와 '바꾸면 늘어나는 이동 분'을 붙인다. 외부 API는 부르지 않는다.

    앞 지점(바로 앞 경유지, 없으면 출발지) → 후보 → 뒤 지점(바로 뒤 경유지, 없으면 귀가)의 추정 시간에서
    지금 장소를 지나는 추정 시간을 뺀 뒤, 코스 전체의 실제 이동 시간 ÷ 추정 시간 비율을 곱해 보정한다.
    (구간 하나로 비율을 잡으면 1분짜리 짧은 구간의 고정 지연이 우회 시간을 크게 부풀린다.)
    코스에 그 역할이 없으면 두 값은 비운다.
    """
    visits = course.stops  # 마지막은 귀가
    points = [(origin.latitude, origin.longitude)] + [(s.place.latitude, s.place.longitude) for s in visits]
    est_total = sum(_estimate_minutes(a, b) for a, b in zip(points, points[1:]))
    actual_total = sum(s.drive_minutes for s in visits)
    low, high = DETOUR_RATIO_RANGE
    ratio = min(max(actual_total / est_total, low), high) if est_total > 0 else 1.0
    result: dict[StopRole, list[Alternative]] = {}
    for role, places in candidates.items():
        i = next((k for k, s in enumerate(visits[:-1]) if s.role == role), None)
        if i is None:
            result[role] = [Alternative.model_validate(p.model_dump()) for p in places]
            continue
        before, current, after = points[i], points[i + 1], points[i + 2]
        est_now = _estimate_minutes(before, current) + _estimate_minutes(current, after)
        alternatives = []
        for p in places:
            x = (p.latitude, p.longitude)
            est_new = _estimate_minutes(before, x) + _estimate_minutes(x, after)
            alternatives.append(
                Alternative.model_validate(
                    p.model_dump()
                    | {"distance_m": round(_haversine_m(current, x)), "detour_minutes": round((est_new - est_now) * ratio)}
                )
            )
        result[role] = alternatives
    return result


def _estimate_minutes(a: tuple[float, float], b: tuple[float, float]) -> float:
    return _estimate_leg(a, b).duration_s / 60


def _estimate_leg(a: tuple[float, float], b: tuple[float, float]) -> Leg:
    meters = _haversine_m(a, b) * 1.4  # 도로 우회 계수
    return Leg(distance_m=round(meters), duration_s=round(meters / METERS_PER_DRIVE_MINUTE * 60), path=(a, b))


def _haversine_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, (*a, *b))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


# 추천 코스 이름: 나들이 장소의 성격별. (분야, 실내 여부) → 이름
_LABELS = {
    Theme.NATURE: "자연 산책 코스",
    Theme.EXHIBITION: "박물관·전시 코스",
    Theme.PLAY: "실내 놀이 코스",
    Theme.EXPERIENCE: "체험 코스",
    Theme.PET: "반려동물 동반 코스",
}


def _label(c: _Candidate) -> str:
    return _LABELS.get(c.theme, "가까운 나들이 코스") if c.theme else "가까운 나들이 코스"


def _pick_varied(outings: list[_Candidate], count: int) -> list[tuple[int, str]]:
    """점수 순으로 보면서 분야가 겹치지 않게 고른다. 분야가 모자라면 남은 높은 점수 장소로 채운다."""
    picked: list[tuple[int, str]] = []
    used_labels: set[str] = set()
    for i, c in enumerate(outings):
        label = _label(c)
        if label in used_labels:
            continue
        used_labels.add(label)
        picked.append((i, label))
        if len(picked) == count:
            return picked
    chosen = {i for i, _ in picked}
    for i, c in enumerate(outings):
        if i in chosen:
            continue
        picked.append((i, _label(c)))
        if len(picked) == count:
            break
    return picked


def _option(course: CourseResponse, candidate: _Candidate, label: str) -> CourseOption:
    outing_stop = next(s for s in course.stops if s.role == StopRole.OUTING)
    visits = [s for s in course.stops if s.role not in (StopRole.HOME, StopRole.FUEL)]
    h1, m1 = map(int, course.departure_at.split(":"))
    h2, m2 = map(int, course.return_at.split(":"))
    total = ((h2 * 60 + m2) - (h1 * 60 + m1)) % (24 * 60)
    # 같은 분야가 두 번 나오면 구분되도록 장소 이름을 붙인다.
    return CourseOption(
        name=f"{label} · {outing_stop.place.name}",
        summary=" → ".join(s.place.name for s in visits),
        total_minutes=total,
        first_drive_minutes=course.stops[0].drive_minutes,
        place_key=f"{outing_stop.place.source}:{outing_stop.place.source_id}",
        indoor=candidate.indoor,
        course=course,
    )

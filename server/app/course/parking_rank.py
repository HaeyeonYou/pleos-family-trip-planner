"""코스 장소 근처 주차장 정렬. 순수 함수만 둔다 (DB·시계에 기대지 않아 단위 테스트가 쉽다).

점수가 낮을수록 먼저 보인다 (거리 m 기준으로 환산).
- 도착 시각에 운영하지 않는 곳은 맨 뒤로 보내고 open_at_arrival=False 표시를 단다. 운영시간을 모르면 None(단정하지 않음).
- 무료·공영 주차장은 가점. 무료는 공영보다 크게 본다.
- 영유아 동반이면 걷는 거리를 더 무겁게 본다 (유모차·안기).
"""

from app.schemas import StopParking
from app.sources.kakao import KakaoPlace
from app.sources.parking import ParkingPlace, haversine_m

PARKING_RADIUS_M = 500  # 장소 둘레 검색 반경
PARKING_PER_STOP = 3  # 한 장소에 붙이는 최대 개수
PARKING_FETCH = 12  # 정렬 전에 가져오는 개수 (가까운 순)
FREE_BONUS_M = 150
PUBLIC_BONUS_M = 80
CLOSED_PENALTY_M = 10_000
INFANT_DISTANCE_WEIGHT = 1.6
NOT_A_LOT_WORDS = ("대행", "발렛", "발레")  # 카카오 주차장 분류에 섞인 주차대행 업체
DUPLICATE_M = 40  # 표의 주차장과 이 거리 안이면 같은 곳으로 본다
WALK_M_PER_MIN = 67  # 어른 보행 ≈ 4km/h. 앱도 같은 값으로 '약 N분'을 어림한다


def _minutes(hhmm: str) -> int:
    h, m = hhmm.split(":")
    return int(h) * 60 + int(m)


def is_open_at(open_time: str | None, close_time: str | None, arrive_hhmm: str) -> bool | None:
    """도착 시각에 운영 중인지. 운영시간을 모르면 None. 자정을 넘기는 운영(22:00~06:00)도 다룬다."""
    if not open_time or not close_time:
        return None
    start, end, now = _minutes(open_time), _minutes(close_time), _minutes(arrive_hhmm)
    if start <= end:
        return start <= now <= end
    return now >= start or now <= end


def is_free(lot: ParkingPlace) -> bool:
    return lot.fee_summary == "무료"


def sort_score(lot: ParkingPlace, open_now: bool | None, infant: bool) -> float:
    score = lot.distance_m * (INFANT_DISTANCE_WEIGHT if infant else 1.0)
    if is_free(lot):
        score -= FREE_BONUS_M
    if lot.kind == "공영":
        score -= PUBLIC_BONUS_M
    if open_now is False:
        score += CLOSED_PENALTY_M
    return score


def rank(lots: list[ParkingPlace], arrive_hhmm: str, infant: bool, limit: int = PARKING_PER_STOP) -> list[StopParking]:
    """후보를 규칙대로 정렬해 응답 항목으로 바꾼다. 점수가 같으면 가까운 곳이 먼저."""
    scored = []
    for lot in lots:
        open_now = is_open_at(lot.open_time, lot.close_time, arrive_hhmm)
        scored.append((sort_score(lot, open_now, infant), lot.distance_m, lot, open_now))
    scored.sort(key=lambda t: (t[0], t[1]))
    return [
        StopParking(
            name=lot.name,
            kind=lot.kind,
            distance_m=lot.distance_m,
            walk_minutes=max(1, round(lot.distance_m / WALK_M_PER_MIN)),
            capacity=lot.capacity,
            fee_summary=lot.fee_summary,
            free=is_free(lot),
            hours=lot.hours,
            open_at_arrival=open_now,
            latitude=lot.latitude,
            longitude=lot.longitude,
        )
        for _, _, lot, open_now in scored[:limit]
    ]


def _kakao_kind(category: str) -> str | None:
    """카카오 분류 끝 단계로 공영·민영을 가른다. 모르면 None (짐작하지 않음)."""
    last = category.split(" > ")[-1]
    if "공영" in last:
        return "공영"
    if "민영" in last:
        return "민영"
    return None


def from_kakao(p: KakaoPlace) -> ParkingPlace | None:
    """카카오 주차장(PK6) 한 곳. 요금·운영시간·면수는 카카오에 없어 비워 둔다. 주차장 분류가 아니면 None."""
    if "주차장" not in p.category or any(w in p.name for w in NOT_A_LOT_WORDS):
        return None
    return ParkingPlace(
        name=p.name, kind=_kakao_kind(p.category), lot_type=None, address=p.address, distance_m=round(p.distance_m),
        capacity=None, fee_summary="", hours="", latitude=p.latitude, longitude=p.longitude,
    )


def _plain(name: str) -> str:
    return name.replace(" ", "").replace("주차장", "")


def is_duplicate(lot: ParkingPlace, known: list[ParkingPlace]) -> bool:
    return any(
        _plain(lot.name) == _plain(k.name) or haversine_m(lot.latitude, lot.longitude, k.latitude, k.longitude) <= DUPLICATE_M
        for k in known
    )

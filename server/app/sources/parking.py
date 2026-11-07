"""주차장 근처 조회. 미리 적재한 parking_lots 표(scripts/load_parking.py)에서 찾는다. 외부 API는 부르지 않는다.

코스 계획(planner)에서는 이렇게 쓴다:

    from app.sources import parking
    lots = await parking.nearby(db, place.latitude, place.longitude, radius_m=800, limit=3)

위경도 사각형으로 먼저 걸러(인덱스 ix_parking_lots_latitude_longitude) 후보를 줄이고,
정확한 거리는 haversine으로 계산해 반경 안만 거리순으로 돌려준다.
"""

import math
from datetime import date, datetime
from zoneinfo import ZoneInfo

from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import ParkingLot

EARTH_RADIUS_M = 6_371_000
KST = ZoneInfo("Asia/Seoul")


class ParkingPlace(BaseModel):
    name: str
    kind: str | None  # 공영 / 민영
    lot_type: str | None  # 노외 / 노상 / 부설
    address: str
    distance_m: int
    capacity: int | None
    fee_summary: str  # "30분 1,000원·추가 10분 500원·일 최대 10,000원"
    hours: str  # 오늘 요일 기준: "평일 09:00~18:00"
    latitude: float
    longitude: float
    phone: str | None = None


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lng2 - lng1) / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def bounding_box(lat: float, lng: float, radius_m: float) -> tuple[float, float, float, float]:
    """반경을 감싸는 사각형 (min_lat, max_lat, min_lng, max_lng). 극 근처에서도 경도 범위가 무한대가 되지 않게 막는다."""
    dlat = math.degrees(radius_m / EARTH_RADIUS_M)
    dlng = math.degrees(radius_m / (EARTH_RADIUS_M * max(math.cos(math.radians(lat)), 0.01)))
    return lat - dlat, lat + dlat, lng - dlng, lng + dlng


def fee_summary(lot: ParkingLot) -> str:
    if lot.fee_type == "무료":
        return "무료"
    parts: list[str] = []
    if lot.base_minutes and lot.base_fee is not None:  # 요금이 비어 있으면 '무료'로 단정하지 않는다
        parts.append(f"{lot.base_minutes}분 " + (f"{lot.base_fee:,}원" if lot.base_fee else "무료"))
    elif lot.base_fee:
        parts.append(f"기본 {lot.base_fee:,}원")
    if lot.add_minutes and lot.add_fee:
        parts.append(f"추가 {lot.add_minutes}분 {lot.add_fee:,}원")
    if lot.day_fee:
        parts.append(f"일 최대 {lot.day_fee:,}원")
    if not parts:
        return "혼합 (요금 정보 없음)" if lot.fee_type == "혼합" else "요금 정보 없음"
    return "·".join(parts)


def hours_text(lot: ParkingLot, today: date) -> str:
    """오늘 요일에 맞는 운영시간. 토요일은 sat_*, 일요일은 holiday_*, 그 외는 weekday_*. 공휴일 달력은 없어 평일로 본다."""
    label, open_, close = (
        ("평일", lot.weekday_open, lot.weekday_close),
        ("토요일", lot.sat_open, lot.sat_close),
        ("일요일·공휴일", lot.holiday_open, lot.holiday_close),
    )[{5: 1, 6: 2}.get(today.weekday(), 0)]
    if not open_ or not close:
        return f"{label} 운영시간 정보 없음"
    if open_ == close:  # 00:00~00:00 처럼 시작=끝이면 값이 채워지지 않은 것
        return f"{label} 운영시간 정보 없음"
    if (open_, close) in (("00:00", "24:00"), ("00:00", "23:59")):
        return f"{label} 24시간"
    return f"{label} {open_}~{close}"


async def nearby(
    db: AsyncSession,
    latitude: float,
    longitude: float,
    radius_m: int = 1000,
    limit: int = 5,
    today: date | None = None,
) -> list[ParkingPlace]:
    """반경 안 주차장을 가까운 순으로 최대 limit개. today를 주지 않으면 한국 시간 오늘."""
    today = today or datetime.now(KST).date()
    min_lat, max_lat, min_lng, max_lng = bounding_box(latitude, longitude, radius_m)
    lots = await db.scalars(
        select(ParkingLot).where(
            ParkingLot.latitude.between(min_lat, max_lat), ParkingLot.longitude.between(min_lng, max_lng)
        )
    )
    found = sorted(
        ((haversine_m(latitude, longitude, lot.latitude, lot.longitude), lot) for lot in lots),
        key=lambda pair: pair[0],
    )
    return [
        ParkingPlace(
            name=lot.name,
            kind=lot.kind,
            lot_type=lot.lot_type,
            address=lot.address_road or lot.address_lot or "",
            distance_m=round(dist),
            capacity=lot.capacity,
            fee_summary=fee_summary(lot),
            hours=hours_text(lot, today),
            latitude=lot.latitude,
            longitude=lot.longitude,
            phone=lot.phone,
        )
        for dist, lot in found
        if dist <= radius_m
    ][:limit]

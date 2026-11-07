"""장소 보조 정보 API. 지금은 근처 주차장."""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import get_db
from app.sources import parking

router = APIRouter(prefix="/places")


@router.get("/parking", response_model=list[parking.ParkingPlace])
async def nearby_parking(
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    radius_m: int = Query(1000, ge=100, le=20000),
    limit: int = Query(5, ge=1, le=30),
    db: AsyncSession = Depends(get_db),
) -> list[parking.ParkingPlace]:
    """위도·경도 근처 주차장을 가까운 순으로. 요금·운영시간은 오늘 요일 기준 한 줄 문구."""
    return await parking.nearby(db, lat, lng, radius_m, limit)

"""지금 날씨 (기상청 API허브 초단기실황·초단기예보). 홈 칩과 실내 우선 판단에 쓴다.

위경도를 기상청 격자(nx, ny)로 바꿔 부르고, 같은 격자는 10분 동안 기억해 호출 수를 줄인다.
"""

import logging
import math
import time
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel

from app.config import Settings, get_settings

KST = ZoneInfo("Asia/Seoul")
log = logging.getLogger("weather")
BASE = "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0"
CACHE_SECONDS = 600

router = APIRouter()
_cache: dict[tuple[int, int], tuple[float, "Weather"]] = {}


class Weather(BaseModel):
    temperature: float | None = None
    condition: str = ""
    rainy: bool = False
    source: str = "kma"


def to_grid(lat: float, lng: float) -> tuple[int, int]:
    """위경도 → 기상청 격자 (람베르트 정각원추). 기상청 공식 변환식."""
    re, grid = 6371.00877, 5.0
    slat1, slat2, olon, olat = map(math.radians, (30.0, 60.0, 126.0, 38.0))
    sn = math.log(math.cos(slat1) / math.cos(slat2)) / math.log(
        math.tan(math.pi * 0.25 + slat2 * 0.5) / math.tan(math.pi * 0.25 + slat1 * 0.5)
    )
    sf = math.tan(math.pi * 0.25 + slat1 * 0.5) ** sn * math.cos(slat1) / sn
    ro = re / grid * sf / math.tan(math.pi * 0.25 + olat * 0.5) ** sn
    ra = re / grid * sf / math.tan(math.pi * 0.25 + math.radians(lat) * 0.5) ** sn
    theta = math.radians(lng) - olon
    theta = (theta + math.pi) % (2 * math.pi) - math.pi
    theta *= sn
    return int(ra * math.sin(theta) + 43 + 0.5), int(ro - ra * math.cos(theta) + 136 + 0.5)


def _items(client: httpx.Client, op: str, key: str, params: dict) -> list[dict]:
    res = client.get(f"{BASE}/{op}", params={"pageNo": 1, "numOfRows": 60, "dataType": "JSON", "authKey": key, **params})
    res.raise_for_status()
    body = res.json()["response"]
    if body["header"]["resultCode"] != "00":
        raise RuntimeError(body["header"]["resultMsg"])
    return body["body"]["items"]["item"]


def fetch(key: str, lat: float, lng: float, now: datetime | None = None) -> Weather:
    nx, ny = to_grid(lat, lng)
    now = now or datetime.now(KST)
    with httpx.Client(timeout=8) as client:
        # 실황은 매시 40분 이후에 그 시각 자료가 열린다
        base = (now - timedelta(minutes=40)).replace(minute=0, second=0, microsecond=0)
        obs = {
            i["category"]: i["obsrValue"]
            for i in _items(client, "getUltraSrtNcst", key, {"base_date": base.strftime("%Y%m%d"), "base_time": base.strftime("%H%M"), "nx": nx, "ny": ny})
        }
        pty = obs.get("PTY", "0")
        condition = {"1": "비", "2": "비/눈", "3": "눈", "5": "빗방울", "6": "빗방울/눈날림", "7": "눈날림"}.get(pty, "")
        if not condition:
            # 하늘 상태는 예보에만 있다 (매시 30분 발표, 45분 이후 제공)
            fbase = (now - timedelta(minutes=45)).replace(minute=30, second=0, microsecond=0)
            if fbase > now - timedelta(minutes=45):
                fbase -= timedelta(hours=1)
            try:
                fc = _items(client, "getUltraSrtFcst", key, {"base_date": fbase.strftime("%Y%m%d"), "base_time": fbase.strftime("%H%M"), "nx": nx, "ny": ny})
                sky = next(i["fcstValue"] for i in fc if i["category"] == "SKY")
                condition = {"1": "맑음", "3": "구름 많음", "4": "흐림"}.get(sky, "맑음")
            except Exception as e:  # noqa: BLE001 - 하늘 상태가 없어도 기온·강수는 준다
                log.info("하늘 상태 조회 실패: %r", e)
                condition = "강수 없음"
        temp = obs.get("T1H")
        return Weather(
            temperature=float(temp) if temp is not None else None,
            condition=condition,
            rainy=pty != "0",
        )


@router.get("/weather", response_model=Weather)
def weather(
    lat: float = Query(..., ge=33, le=39),
    lng: float = Query(..., ge=124, le=132),
    settings: Settings = Depends(get_settings),
) -> Weather:
    if not settings.kma_api_hub_key:
        raise HTTPException(503, "KMA_API_HUB_KEY가 필요합니다")
    grid = to_grid(lat, lng)
    hit = _cache.get(grid)
    if hit and time.monotonic() - hit[0] < CACHE_SECONDS:
        return hit[1]
    try:
        result = fetch(settings.kma_api_hub_key, lat, lng)
    except Exception as e:  # noqa: BLE001
        log.warning("날씨 조회 실패: %r", e)
        raise HTTPException(502, "날씨를 가져오지 못했어요") from e
    _cache[grid] = (time.monotonic(), result)
    return result

"""공공데이터포털 후보 소스 (#33).

- 어린이놀이시설(행안부 15124519)·문화시설(문화정보원 15138930): 목록 API가 느리고 호출이 제한적이라
  scripts/load_public_data.py 로 미리 받아 server/data/*.json 에 둔 것을 읽는다.
- 한눈에보는문화정보(문화정보원 15138937): 오늘 열리는 전시·공연을 실시간으로 받아 6시간 저장한다.
키는 기존 DATA_GO_KR_KEY 하나다 (데이터셋마다 활용신청 승인이 필요하다).
"""

import json
import logging
import math
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path

import httpx

from app.logs import api_log_hooks
from app.sources.cache import TtlCache

log = logging.getLogger("public")

DATA_DIR = Path(__file__).resolve().parents[2] / "data"
CULTURE_INFO_URL = "https://apis.data.go.kr/B553457/cultureinfo/period2"
MAX_EVENT_PAGES = 40  # 한 페이지 10건. 하루 400건까지만 본다


@dataclass(frozen=True)
class PublicPlace:
    """사전 적재한 장소 한 곳 (놀이시설 묶음, 박물관·미술관·도서관)."""

    source: str        # playground / culture
    source_id: str
    name: str
    latitude: float
    longitude: float
    address: str
    group: str         # 도시공원, 자연휴양림, 박물관, 미술관, 도서관
    indoor: bool
    distance_m: float = 0.0
    facilities: int = 1  # 묶인 놀이시설 수 (여러 곳이면 큰 공원)


@dataclass(frozen=True)
class CultureEvent:
    seq: str
    title: str
    place: str
    realm: str         # 전시, 공연 ...
    start: str         # YYYYMMDD
    end: str
    latitude: float
    longitude: float
    image_url: str
    area: str = ""
    sigungu: str = ""
    distance_m: float = 0.0


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    a, b, c, d = map(math.radians, (lat1, lon1, lat2, lon2))
    h = math.sin((c - a) / 2) ** 2 + math.cos(a) * math.cos(c) * math.sin((d - b) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


class PublicData:
    def __init__(self, service_key: str = "", data_dir: Path = DATA_DIR, http: httpx.Client | None = None):
        self._key = service_key
        self._dir = data_dir
        self._http = http or httpx.Client(timeout=10, event_hooks=api_log_hooks("culture"))
        self._events = TtlCache(ttl_seconds=6 * 3600, name="문화정보 전시·공연")
        self._playgrounds: list[PublicPlace] | None = None
        self._facilities: list[PublicPlace] | None = None

    def playgrounds(self, latitude: float, longitude: float, radius_m: int) -> list[PublicPlace]:
        """놀이터가 있는 공공 공원·자연휴양림 (가까운 순)."""
        if self._playgrounds is None:
            self._playgrounds = [
                PublicPlace("playground", str(r["id"]), r["name"], r["lat"], r["lng"], r.get("address", ""), r["kind"], bool(r.get("indoor")), 0.0, int(r.get("count", 1)))
                for r in self._load("playgrounds.json")
            ]
        return _nearby(self._playgrounds, latitude, longitude, radius_m)

    def facilities(self, latitude: float, longitude: float, radius_m: int) -> list[PublicPlace]:
        """박물관·미술관·도서관 (가까운 순)."""
        if self._facilities is None:
            self._facilities = [
                PublicPlace("culture", f"{r['group']}{r['id']}", r["name"], r["lat"], r["lng"], "", r["group"], True)
                for r in self._load("culture_facilities.json")
            ]
        return _nearby(self._facilities, latitude, longitude, radius_m)

    def events(self, today: date, latitude: float, longitude: float, radius_m: int) -> list[CultureEvent]:
        """오늘 열리는 전시·공연 중 반경 안의 것 (가까운 순). 키가 없으면 빈 목록."""
        if not self._key:
            return []
        every = self._events.get_or_load(today, lambda: self._fetch_events(today))
        found = []
        for e in every:
            d = haversine_m(latitude, longitude, e.latitude, e.longitude)
            if d <= radius_m:
                found.append(CultureEvent(**{**e.__dict__, "distance_m": d}))
        return sorted(found, key=lambda e: e.distance_m)

    def _fetch_events(self, today: date) -> list[CultureEvent]:
        day = today.strftime("%Y%m%d")
        events: list[CultureEvent] = []
        total = None
        for page in range(1, MAX_EVENT_PAGES + 1):
            res = self._http.get(
                CULTURE_INFO_URL,
                params={"serviceKey": self._key, "from": day, "to": day, "serviceTp": "A", "PageNo": page},
            )
            res.raise_for_status()
            text = res.text
            if total is None:
                m = re.search(r"<totalCount>(\d+)", text)
                total = int(m.group(1)) if m else 0
            events += _parse_events(text)
            if page * 10 >= total:
                break
        return events

    def _load(self, name: str) -> list[dict]:
        path = self._dir / name
        if not path.exists():
            log.warning("%s 없음 — scripts/load_public_data.py 로 받아 두세요", name)
            return []
        return json.loads(path.read_text(encoding="utf-8"))


def _nearby(places: list[PublicPlace], lat: float, lng: float, radius_m: int) -> list[PublicPlace]:
    found = []
    for p in places:
        if abs(p.latitude - lat) > radius_m / 100_000 or abs(p.longitude - lng) > radius_m / 70_000:
            continue  # 위경도 상자로 먼저 거른다
        d = haversine_m(lat, lng, p.latitude, p.longitude)
        if d <= radius_m:
            found.append(PublicPlace(**{**p.__dict__, "distance_m": d}))
    return sorted(found, key=lambda p: p.distance_m)


def _parse_events(text: str) -> list[CultureEvent]:
    result = []
    for item in re.findall(r"<item>(.*?)</item>", text, re.S):
        def f(tag: str) -> str:
            m = re.search(rf"<{tag}>(.*?)</{tag}>", item, re.S)
            return m.group(1).strip() if m else ""

        try:
            lat, lng = float(f("gpsY")), float(f("gpsX"))
        except ValueError:
            continue
        if not (33 <= lat <= 39 and 124 <= lng <= 132) or not f("title"):
            continue
        result.append(
            CultureEvent(f("seq"), f("title"), f("place"), f("realmName"), f("startDate"), f("endDate"), lat, lng,
                         f("thumbnail"), f("area"), f("sigungu"))
        )
    return result

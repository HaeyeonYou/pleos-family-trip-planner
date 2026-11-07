"""한국관광공사 TourAPI (국문 관광정보, 무장애 여행정보, 반려동물 동반여행)."""

from dataclasses import dataclass

import httpx

from app.logs import api_log_hooks
from app.sources.cache import TtlCache

BASE_URL = "https://apis.data.go.kr/B551011"
KOR = "KorService2"
WITH = "KorWithService2"  # 무장애: 영유아가족 편의 필드의 출처
PET = "KorPetTourService2"

CONTENT_TYPE_ATTRACTION = 12
CONTENT_TYPE_CULTURE = 14

# 무장애 detailWithTour2의 영유아가족 필드. 대부분 자유 텍스트라 '있음/정보 없음'으로만 쓴다.
AMENITY_FIELDS = {
    "stroller": "stroller",
    "nursing_room": "lactationroom",
    "kids_chair": "babysparechair",
    "infant_etc": "infantsfamilyetc",
}


# 소개정보(detailIntro2)의 주차 필드는 분류마다 이름이 다르다.
PARKING_FIELDS = {CONTENT_TYPE_ATTRACTION: "parking", CONTENT_TYPE_CULTURE: "parkingculture"}


@dataclass(frozen=True)
class TourPlace:
    content_id: str
    content_type_id: int
    title: str
    latitude: float
    longitude: float
    address: str
    cat1: str
    cat2: str
    cat3: str
    distance_m: float
    image_url: str


class TourApiClient:
    def __init__(self, service_key: str, http: httpx.Client | None = None):
        self._key = service_key
        self._http = http or httpx.Client(timeout=10, event_hooks=api_log_hooks("tour"))
        self._list_cache = TtlCache(ttl_seconds=6 * 3600, name="TourAPI 목록")
        self._amenity_cache = TtlCache(ttl_seconds=24 * 3600, name="TourAPI 편의정보")
        self._parking_cache = TtlCache(ttl_seconds=24 * 3600, name="TourAPI 주차정보")

    def nearby(
        self,
        latitude: float,
        longitude: float,
        radius_m: int,
        content_type_id: int | None = None,
        service: str = KOR,
        rows: int = 60,
    ) -> list[TourPlace]:
        # 좌표를 약 100m 단위로 묶어 캐시 적중률을 높인다.
        key = (service, round(latitude, 3), round(longitude, 3), radius_m, content_type_id, rows)
        return self._list_cache.get_or_load(
            key, lambda: self._fetch_nearby(latitude, longitude, radius_m, content_type_id, service, rows)
        )

    def amenities(self, content_id: str) -> dict[str, str]:
        """영유아가족 편의 원문. 무장애 정보가 없는 장소는 빈 dict."""
        return self._amenity_cache.get_or_load(content_id, lambda: self._fetch_amenities(content_id))

    def parking(self, content_id: str, content_type_id: int) -> str:
        """주차 안내 원문 (소개정보). 분류에 주차 필드가 없거나 비어 있으면 빈 문자열."""
        field = PARKING_FIELDS.get(content_type_id)
        if field is None:
            return ""
        return self._parking_cache.get_or_load(content_id, lambda: self._fetch_parking(content_id, content_type_id, field))

    def _fetch_parking(self, content_id: str, content_type_id: int, field: str) -> str:
        items = self._items(f"{KOR}/detailIntro2", {"contentId": content_id, "contentTypeId": content_type_id})
        return (items[0].get(field) or "").strip() if items else ""

    def _fetch_nearby(
        self,
        latitude: float,
        longitude: float,
        radius_m: int,
        content_type_id: int | None,
        service: str,
        rows: int,
    ) -> list[TourPlace]:
        params = {
            "mapX": longitude,
            "mapY": latitude,
            "radius": min(radius_m, 20000),
            "arrange": "E",  # 거리순
            "numOfRows": rows,
            "pageNo": 1,
        }
        if content_type_id:
            params["contentTypeId"] = content_type_id
        return [_to_place(item) for item in self._items(f"{service}/locationBasedList2", params)]

    def _fetch_amenities(self, content_id: str) -> dict[str, str]:
        items = self._items(f"{WITH}/detailWithTour2", {"contentId": content_id})
        if not items:
            return {}
        raw = items[0]
        return {name: (raw.get(field) or "").strip() for name, field in AMENITY_FIELDS.items()}

    def _items(self, path: str, params: dict) -> list[dict]:
        common = {"serviceKey": self._key, "MobileOS": "ETC", "MobileApp": "FamilyTrip", "_type": "json"}
        res = self._http.get(f"{BASE_URL}/{path}", params=common | params)
        res.raise_for_status()
        body = res.json()["response"]["body"]
        items = body.get("items") or {}
        # 결과가 없으면 items가 빈 문자열로 온다.
        item = items.get("item", []) if isinstance(items, dict) else []
        return item if isinstance(item, list) else [item]


def _to_place(item: dict) -> TourPlace:
    return TourPlace(
        content_id=str(item["contentid"]),
        content_type_id=int(item.get("contenttypeid") or 0),
        title=item.get("title", ""),
        latitude=float(item["mapy"]),
        longitude=float(item["mapx"]),
        address=" ".join(filter(None, [item.get("addr1"), item.get("addr2")])),
        cat1=item.get("cat1", ""),
        cat2=item.get("cat2", ""),
        cat3=item.get("cat3", ""),
        distance_m=float(item.get("dist") or 0),
        image_url=item.get("firstimage", ""),
    )

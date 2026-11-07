"""공공데이터포털에서 후보 장소를 미리 받아 server/data/*.json 으로 저장한다 (#33 사전 적재).

  cd server && .venv/bin/python -m scripts.load_public_data [playgrounds] [culture]

- playgrounds: 행정안전부 전국어린이놀이시설정보서비스(15124519) 중 공공 도시공원·자연휴양림의 운영 중인 곳.
  전체 8만 5천 건 가운데 주택단지·어린이집 등 나들이 장소가 아닌 것은 버린다. 가까운 시설은 한 곳으로 묶는다.
- culture: 한국문화정보원 문화시설조회서비스(15138930)의 박물관·미술관·도서관 (목록 API가 한 번에 10건이라 수백 번 호출).
둘 다 인증키는 .env의 DATA_GO_KR_KEY (활용신청 승인 필요). 한 달에 한 번쯤 다시 받으면 된다.
"""

import json
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import httpx

from app.config import get_settings

DATA = Path(__file__).resolve().parents[1] / "data"
PFC = "https://apis.data.go.kr/1741000/pfc3/getPfctInfo3"
CULTURE = "https://apis.data.go.kr/B553457/nopenapi/rest/cultureartspaces"
KEEP_PLACE = {"A003": "도시공원", "A030": "자연휴양림"}
_SUFFIX = re.compile(r"\s*(어린이)?\s*(놀이터|놀이시설|놀이장|놀이공간)\s*\d*$")


def get(client: httpx.Client, url: str, params: dict) -> httpx.Response:
    """429(호출 과다)면 쉬었다 다시. 오류 메시지에는 주소(인증키가 들어 있음)를 담지 않는다."""
    for attempt in range(6):
        try:
            res = client.get(url, params=params, timeout=60)
            if res.status_code == 429:
                time.sleep(2 + attempt * 3)
                continue
            res.raise_for_status()
            return res
        except httpx.HTTPError as e:
            if attempt == 5:
                raise RuntimeError(f"호출 실패: {type(e).__name__} {getattr(getattr(e, 'response', None), 'status_code', '')}") from None
    raise RuntimeError("호출 실패: 429 반복")


def load_playgrounds(key: str) -> None:
    with httpx.Client() as client:
        def page(p: int) -> list[dict]:
            body = get(client, PFC, {"serviceKey": key, "pageIndex": p, "recordCountPerPage": 1000, "type": "json"}).json()
            return body["response"]["body"]["items"]

        with ThreadPoolExecutor(6) as pool:
            rows = [r for items in pool.map(page, range(1, 87)) for r in items]
    places: dict[tuple[int, int], dict] = {}
    for r in rows:
        if r["instlPlaceCd"] not in KEEP_PLACE or r["operYnCdNm"] != "운영":
            continue
        if r["instlPlaceCd"] == "A003" and r["prvtPblcYnCdNm"] != "공공":
            continue
        try:
            lat, lng = float(r["latCrtsVl"]), float(r["lotCrtsVl"])
        except (TypeError, ValueError):
            continue
        if not (33 <= lat <= 39 and 124 <= lng <= 132):
            continue
        name = _SUFFIX.sub("", r["pfctNm"]).strip() or r["pfctNm"]
        slot = (round(lat * 400), round(lng * 400))  # 약 250m 안의 시설은 한 곳
        place = places.setdefault(slot, {
            "id": r["pfctSn"], "name": name, "lat": lat, "lng": lng, "address": r.get("ronaAddr") or "",
            "kind": KEEP_PLACE[r["instlPlaceCd"]], "indoor": r.get("idrodrCdNm") == "실내", "count": 0,
        })
        place["count"] += 1
    out = sorted(places.values(), key=lambda p: p["id"])
    (DATA / "playgrounds.json").write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"어린이놀이시설 {len(rows)}건 → 나들이 후보 {len(out)}곳")


def load_culture(key: str) -> None:
    result = []
    with httpx.Client() as client:
        for kind in ("museum", "library"):
            url = f"{CULTURE}/{kind}"
            first = get(client, url, {"serviceKey": key, "PageNo": 1}).text
            total = int(re.search(r"<totalCount>(\d+)", first).group(1))

            def page(p: int) -> str:
                return get(client, url, {"serviceKey": key, "PageNo": p}).text

            with ThreadPoolExecutor(1) as pool:  # 문화시설 API는 호출이 잦으면 429
                texts = list(pool.map(lambda p: (time.sleep(0.6), page(p))[1], range(1, total // 10 + 2)))
            for text in texts:
                for item in re.findall(r"<item>(.*?)</item>", text, re.S):
                    f = lambda tag: (re.search(rf"<{tag}>(.*?)</{tag}>", item, re.S) or [None, ""])[1].strip()  # noqa: E731
                    try:
                        lat, lng = float(f("gpsY")), float(f("gpsX"))
                    except ValueError:
                        continue
                    if 33 <= lat <= 39 and 124 <= lng <= 132:
                        result.append({"id": f("seq"), "name": f("culName"), "group": f("culGrpName"), "lat": lat, "lng": lng,
                                       "url": f("culHomeUrl"), "tel": f("culTel")})
    seen, out = set(), []
    for p in result:
        if (p["id"], p["group"]) not in seen:
            seen.add((p["id"], p["group"]))
            out.append(p)
    (DATA / "culture_facilities.json").write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"문화시설 {len(out)}곳")


if __name__ == "__main__":
    key = get_settings().data_go_kr_key
    if not key:
        sys.exit("DATA_GO_KR_KEY가 .env에 없습니다")
    DATA.mkdir(exist_ok=True)
    targets = sys.argv[1:] or ["playgrounds", "culture"]
    if "playgrounds" in targets:
        load_playgrounds(key)
    if "culture" in targets:
        load_culture(key)

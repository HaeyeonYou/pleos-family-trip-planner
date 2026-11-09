"""폰 웹과 차 앱의 주기적 변경 확인(GET /me/sync). pleos_test DB를 쓴다."""

import re
import uuid

from tests.test_auth import phone, run, signup
from tests.test_me_api import CHILD, now_iso
from tests.test_presets import PRESET_FORM, car_token


async def rev(client, headers=None) -> dict:
    res = await client.get("/me/sync", headers=headers or {})
    assert res.status_code == 200 and res.headers["cache-control"] == "no-store"
    return res.json()


def changed(before: dict, after: dict) -> set[str]:
    return {area for area in ("presets", "feedings", "visits", "family", "cars") if before[area] != after[area]}


def test_sync_requires_login_and_shows_nothing_but_marks():
    async def scenario(client):
        assert (await client.get("/me/sync")).status_code == 401
        async with phone(client) as mom:
            await signup(mom, "mom_sync")
            first = await rev(mom)
            assert set(first) == {"presets", "feedings", "visits", "family", "cars", "all"}
            assert all(len(v) == 12 for v in first.values())  # 내용이 아니라 짧은 표시만
            assert await rev(mom) == first  # 아무것도 안 바뀌면 같다

    run(scenario)


def test_each_area_changes_only_when_its_data_changes_and_web_and_car_see_the_same():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_sync2")
            await signup(other, "other_sync2")
            other_before = await rev(other)
            base = await rev(mom)

            # 웹에서 프리셋을 보내면 → 차 앱이 알아챈다 (차 로그인 자체는 '차' 영역을 바꾼다)
            car = await car_token(client, mom)
            after_car = await rev(mom)
            assert changed(base, after_car) == {"cars"} and await rev(client, car) == after_car

            await mom.put("/presets/nap", json={"name": "낮잠", "temperature": 23, "fan_speed": 1})
            sent = await rev(client, car)
            assert changed(after_car, sent) == {"presets"} and sent["all"] != after_car["all"]

            # 차가 적용했다고 알리면 → 웹이 알아챈다
            nap_id = next(s["id"] for s in (await client.get("/presets", headers=car)).json() if s["slot"] == "nap")
            await client.post(f"/presets/{nap_id}/applied", headers=car)
            applied = await rev(mom)
            assert changed(sent, applied) == {"presets"}

            # 수유 기록 (웹이 넣으면 차가, 지우면 다시)
            fid = (await mom.post("/me/feedings", json={"fed_at": now_iso(5), "kind": "formula", "amount_ml": 120})).json()["id"]
            fed = await rev(client, car)
            assert changed(applied, fed) == {"feedings"}
            await mom.delete(f"/me/feedings/{fid}")
            assert changed(fed, await rev(mom)) == {"feedings"}

            # 방문: 추가 → 확인 상태 변경 → 삭제 모두 표시가 바뀐다
            vid = str(uuid.uuid4())
            visit = {"place_key": "k1", "place_name": "서울숲", "theme": "nature", "visited_on": "2026-11-08", "stayed_minutes": 60, "status": "pending"}
            await client.put(f"/me/visits/{vid}", json=visit, headers=car)
            v1 = await rev(mom)
            await mom.patch(f"/me/visits/{vid}", json={"status": "confirmed", "mood": "excited"})
            v2 = await rev(client, car)
            assert v1["visits"] != v2["visits"] and changed(v1, v2) == {"visits"}
            await mom.delete(f"/me/visits/{vid}")
            assert (await rev(mom))["visits"] != v2["visits"]

            # 아이를 고치면 '가족'만 바뀐다 (이름만 고쳐도)
            cid = str(uuid.uuid4())
            await mom.put(f"/me/children/{cid}", json=CHILD)
            f1 = await rev(client, car)
            await mom.put(f"/me/children/{cid}", json=CHILD | {"nickname": "서연"})
            f2 = await rev(mom)
            assert f1["family"] != f2["family"] and changed(f1, f2) == {"family"}

            # 연결된 차를 웹에서 끊으면 '차'가 바뀌고, 끊긴 차는 더 확인할 수 없다
            html = (await mom.get("/m/my")).text
            session_id = re.search(r"/m/my/cars/([0-9a-f-]{36})/disconnect", html).group(1)
            await mom.post(f"/m/my/cars/{session_id}/disconnect")
            assert changed(f2, await rev(mom)) == {"cars"}
            assert (await client.get("/me/sync", headers=car)).status_code == 401

            # 다른 사람의 변경은 내 표시에 영향이 없다
            assert await rev(other) == other_before

    run(scenario)


def test_web_pages_poll_for_changes_and_preset_page_reloads_by_get():
    async def scenario(client):
        async with phone(client) as mom:
            login = (await mom.get("/m/login")).text
            assert "/me/sync" not in login  # 로그인 전 화면은 확인하지 않는다
            await signup(mom, "mom_sync3")
            for path in ("/m/", "/m/care", "/m/grow", "/m/my"):
                html = (await mom.get(path)).text
                assert 'fetch("/me/sync"' in html and "setInterval(check" in html, path
                assert "data-reload" not in html
            preset = (await mom.get("/m/preset?slot=nap")).text
            assert 'fetch("/me/sync"' in preset and 'data-reload="/m/preset?slot=nap"' in preset
            # 보낸 직후 화면(POST 결과)도 새로고침이 아니라 GET 주소로 다시 연다 (같은 요청을 또 보내지 않게)
            sent = (await mom.post("/m/preset", data=PRESET_FORM | {"slot": "nap"})).text
            assert 'data-reload="/m/preset?slot=nap"' in sent

    run(scenario)

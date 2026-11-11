"""폰에서 보낸 공조 프리셋을 차가 받고 적용 완료로 표시하는 흐름."""

import uuid

from tests.test_auth import phone, run, signup


async def car_token(client, mobile) -> dict:
    """폰이 로그인한 계정으로 차를 QR 로그인시키고 차 인증 헤더를 돌려준다."""
    start = (await client.post("/auth/car-login", json={"device_name": "car"})).json()
    await mobile.post("/m/car", data={"code": start["code"]})
    poll = await client.post(f"/auth/car-login/{start['request_id']}/poll", json={"poll_secret": start["poll_secret"]})
    return {"Authorization": f"Bearer {poll.json()['token']}"}


PRESET_FORM = {
    "name": "아기 탑승",
    "temperature": "24.5",
    "fan_speed": "2",
    "driver_seat_heat": "0",
    "passenger_seat_heat": "2",
    "steering_wheel_heat": "1",
    "power": "1",
    "air_cleaner": "1",
}


def test_phone_sends_preset_and_car_applies_once():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "mom_preset")
            car = await car_token(client, mobile)
            assert (await client.get("/presets/pending", headers=car)).status_code == 204  # 아직 없음

            page = await mobile.post("/m/preset", data=PRESET_FORM)
            assert page.status_code == 200 and "차로 보냈어요" in page.text

            pending = (await client.get("/presets/pending", headers=car)).json()
            s = pending["settings"]
            assert s["name"] == "아기 탑승" and s["temperature"] == 24.5 and s["passenger_seat_heat"] == 2
            assert s["air_cleaner"] is True and s["ac"] is False  # 체크하지 않은 항목은 꺼짐

            assert (await client.post(f"/presets/{pending['id']}/applied", headers=car)).status_code == 204
            assert (await client.get("/presets/pending", headers=car)).status_code == 204
            assert "차에서 적용했어요" in (await mobile.get("/m/preset")).text

    run(scenario)


def test_resending_replaces_preset_and_old_ack_does_not_hide_new_one():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "dad_preset")
            car = await car_token(client, mobile)
            await mobile.post("/m/preset", data=PRESET_FORM)
            first = (await client.get("/presets/pending", headers=car)).json()
            await mobile.post("/m/preset", data=PRESET_FORM | {"temperature": "21"})
            # 차가 예전 것을 적용했다고 늦게 알려도 새 프리셋은 대기로 남는다.
            assert (await client.post(f"/presets/{first['id']}/applied", headers=car)).status_code == 404
            second = (await client.get("/presets/pending", headers=car)).json()
            assert second["settings"]["temperature"] == 21

    run(scenario)


def test_presets_are_private_and_need_login():
    async def scenario(client):
        assert (await client.get("/presets/pending")).status_code == 401
        async with phone(client) as a, phone(client) as b:
            await signup(a, "family_a")
            await signup(b, "family_b")
            car_a, car_b = await car_token(client, a), await car_token(client, b)
            await a.post("/m/preset", data=PRESET_FORM)
            assert (await client.get("/presets/pending", headers=car_b)).status_code == 204
            mine = (await client.get("/presets/pending", headers=car_a)).json()
            assert (await client.post(f"/presets/{mine['id']}/applied", headers=car_b)).status_code == 404
        # 범위를 벗어난 값은 거절
        async with phone(client) as c:
            await signup(c, "family_c")
            bad = await c.post("/m/preset", data=PRESET_FORM | {"temperature": "40"})
            assert bad.status_code == 400

    run(scenario)


# 아이별 케어 모드 (QO5, #192)

CHILD_A = {"nickname": "서아", "birth_month": "2025-09"}
CHILD_B = {"nickname": "도윤", "birth_month": "2023-03"}
NAP = {"name": "낮잠", "temperature": 22.5, "fan_speed": 1, "media_volume": 10}


async def two_children(mobile) -> tuple[str, str]:
    a, b = str(uuid.uuid4()), str(uuid.uuid4())
    assert (await mobile.put(f"/me/children/{a}", json=CHILD_A)).status_code == 200
    assert (await mobile.put(f"/me/children/{b}", json=CHILD_B)).status_code == 200
    return a, b


def test_presets_are_kept_per_child():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "mom_child_modes")
            car = await car_token(client, mobile)
            a, b = await two_children(mobile)

            # 가족 기본값(child_id 없음)을 정해 두면 아직 자기 값이 없는 아이는 그 값으로 시작한다.
            await client.put("/presets/nap", json=NAP | {"temperature": 24}, headers=car)
            saved = (await client.put(f"/presets/nap?child_id={a}&applied_on_car=true", json=NAP, headers=car)).json()
            assert saved["child_id"] == a and saved["settings"]["temperature"] == 22.5 and saved["pending"] is False

            nap_a = next(p for p in (await client.get(f"/presets?child_id={a}", headers=car)).json() if p["slot"] == "nap")
            nap_b = next(p for p in (await client.get(f"/presets?child_id={b}", headers=car)).json() if p["slot"] == "nap")
            family = next(p for p in (await client.get("/presets", headers=car)).json() if p["slot"] == "nap")
            assert nap_a["settings"]["temperature"] == 22.5 and nap_a["sent"]
            assert nap_b["settings"]["temperature"] == 24 and not nap_b["sent"] and nap_b["child_id"] == b
            assert family["settings"]["temperature"] == 24 and family["child_id"] is None

            groups = (await client.get("/presets/children", headers=car)).json()
            assert [g["child_id"] for g in groups] == [None, a, b]
            assert [g["nickname"] for g in groups] == [None, "서아", "도윤"]
            assert all([p["slot"] for p in g["presets"]] == ["boarding", "nap", "feeding_stop"] for g in groups)

            # 아이를 지우면 그 아이 값도 지워진다.
            assert (await mobile.delete(f"/me/children/{a}")).status_code == 204
            assert [g["child_id"] for g in (await client.get("/presets/children", headers=car)).json()] == [None, b]

    run(scenario)


def test_child_presets_change_sync_revision_and_reject_other_family():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_child_sync")
            await signup(other, "other_child_sync")
            car = await car_token(client, mom)
            a, _ = await two_children(mom)
            before = (await client.get("/me/sync", headers=car)).json()["presets"]
            await client.put(f"/presets/boarding?child_id={a}", json=NAP, headers=car)
            assert (await client.get("/me/sync", headers=car)).json()["presets"] != before
            # 다른 가족의 아이 id로는 읽지도 쓰지도 못한다.
            assert (await other.get(f"/presets?child_id={a}")).status_code == 404
            assert (await other.put(f"/presets/boarding?child_id={a}", json=NAP)).status_code == 404

    run(scenario)


def test_web_care_and_preset_pages_pick_a_child():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "mom_child_web")
            car = await car_token(client, mobile)
            a, b = await two_children(mobile)

            care = (await mobile.get(f"/m/care?child={b}")).text
            assert "서아" in care and "도윤" in care and f"/m/preset?slot=boarding&amp;child={b}" in care

            page = await mobile.post("/m/preset", data=PRESET_FORM | {"slot": "nap", "child": b, "temperature": "21.5"})
            assert page.status_code == 200 and "도윤 · 낮잠" in page.text
            nap_b = next(p for p in (await client.get(f"/presets?child_id={b}", headers=car)).json() if p["slot"] == "nap")
            nap_a = next(p for p in (await client.get(f"/presets?child_id={a}", headers=car)).json() if p["slot"] == "nap")
            assert nap_b["settings"]["temperature"] == 21.5 and nap_b["pending"]
            assert not nap_a["sent"]
            # 차가 받는 예전 경로도 아이 값을 놓치지 않는다.
            assert (await client.get("/presets/pending", headers=car)).json()["id"] == nap_b["id"]

            home = (await mobile.get("/m/")).text
            assert "도윤 · 낮잠" in home
            my = (await mobile.get("/m/my")).text
            assert f"/m/care?child={a}" in my and "케어 모드(탑승 준비)" in my

    run(scenario)

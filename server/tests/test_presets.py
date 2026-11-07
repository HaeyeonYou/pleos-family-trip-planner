"""폰에서 보낸 공조 프리셋을 차가 받고 적용 완료로 표시하는 흐름."""

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

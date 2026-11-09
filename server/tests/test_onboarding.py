"""온보딩(S01~S03)·가입 동의·아이 정보 확장(M01-1)과 그것을 쓰는 곳(권장 온도·코스 기본값). pleos_test DB를 쓴다."""

import re

from tests.test_auth import phone, run, signup
from tests.test_presets import PRESET_FORM, car_token
from tests.test_web_mobile import _FakePlanner


def test_qr_landing_shows_the_car_and_leads_to_signup_with_the_code():
    async def scenario(client):
        start = (await client.post("/auth/car-login", json={"device_name": "아이오닉 5"})).json()
        async with phone(client) as mobile:
            res = await mobile.get(f"/m/car?code={start['code']}")  # 로그인 전 QR → 랜딩
            assert res.status_code == 303 and res.headers["location"] == f"/m/start?code={start['code']}"
            landing = (await mobile.get(f"/m/start?code={start['code']}")).text
            assert "QR로 들어왔어요 · 아이오닉 5" in landing and "폰에서 미리 해두세요" in landing
            assert f"/m/signup?next=/m/start/child%3Fcode%3D{start['code']}" in landing
            assert f"/m/login?next=/m/car%3Fcode%3D{start['code']}" in landing

            # 모양이 틀린 코드는 무시하고, QR 없이 열어도 랜딩은 나온다
            plain = (await mobile.get("/m/start?code=<script>")).text
            assert "QR로 들어왔어요" not in plain and "<script>" not in plain

            await signup(mobile, "mom_land")  # 로그인한 뒤에는 랜딩 대신 차 연결로
            again = await mobile.get(f"/m/start?code={start['code']}")
            assert again.status_code == 303 and again.headers["location"] == f"/m/car?code={start['code']}"

    run(scenario)


def test_signup_needs_consent_and_confirm_and_has_tabs():
    async def scenario(client):
        async with phone(client) as mobile:
            page = (await mobile.get("/m/signup")).text
            assert 'aria-selected="true">가입' in page and "/m/login?next=" in page and "개인정보 수집·이용 안내" in page and 'name="password2"' in page
            assert 'aria-selected="true">로그인' in (await mobile.get("/m/login")).text

            base = {"login_id": "mom_consent", "password": "password123", "display_name": "지은", "next": "/m/"}
            no_consent = await mobile.post("/m/signup", data=base)
            assert no_consent.status_code == 400 and "동의해야" in no_consent.text and "mom_consent" in no_consent.text
            mismatch = await mobile.post("/m/signup", data=base | {"agree": "1", "password2": "different123"})
            assert mismatch.status_code == 400 and "비밀번호 확인이 맞지 않아요" in mismatch.text
            ok = await mobile.post("/m/signup", data=base | {"agree": "1", "password2": "password123"})
            assert ok.status_code == 303 and ok.headers["location"] == "/m/start/child"  # 처음 가입 → 아이 정보

            from sqlalchemy import select

            from app.main import app
            from app.models import User

            async with app.state.db.sessions() as db:
                user = await db.scalar(select(User).where(User.login_id == "mom_consent"))
                assert user.agreed_at is not None  # 동의 시각이 남는다

        privacy = (await client.get("/m/privacy")).text
        assert "저장하지 않는 것" in privacy and "위치 좌표" in privacy and "알레르기" in privacy
        assert "다녀온 곳 요약" in privacy and "방문 시각(시·분)" in privacy and "되돌릴 수 없어요" in privacy and "차 기기 안에만 있는 기록" in privacy

    run(scenario)


def test_onboarding_steps_save_a_child_in_two_parts_then_connect_the_car():
    async def scenario(client):
        start = (await client.post("/auth/car-login", json={"device_name": "아이오닉 5"})).json()
        code = start["code"]
        async with phone(client) as mom:
            await mom.post("/m/signup", data={"agree": "1", "login_id": "mom_onb", "password": "password123", "next": f"/m/start/child?code={code}"})
            step1 = (await mom.get(f"/m/start/child?code={code}")).text
            assert "아이 정보 1/2" in step1 and "동물 좋아함" in step1 and "건너뛰기" in step1 and f'name="code" value="{code}"' in step1

            bad = await mom.post("/m/start/child", data={"sections": "basic", "code": code, "nickname": "서아", "birth_month": "2099-01"})
            assert bad.status_code == 400 and "태어난 달을 다시 확인" in bad.text and 'value="서아"' in bad.text

            res = await mom.post("/m/start/child", data={"sections": "basic", "code": code, "nickname": "서아", "birth_month": "2025-09", "temperament": ["animal", "active", "bogus"]})
            assert res.status_code == 303 and res.headers["location"] == f"/m/start/care?code={code}"
            family = (await mom.get("/me/family")).json()["children"]
            assert len(family) == 1 and family[0]["temperament"] == ["animal", "active"] and family[0]["feeding_type"] is None

            step2 = (await mom.get(f"/m/start/care?code={code}")).text
            assert "케어 정보 2/2" in step2 and "서아의 외출 리듬" in step2 and "완료기 · 유아식" in step2
            res = await mom.post("/m/start/care", data={"sections": "care", "code": code, "feeding_type": "mixed", "feed_interval_minutes": "180", "nap_count": "2", "nap_times": "10:30–12:00 · 15:00–16:00", "nap_in_car": "1"})
            assert res.status_code == 303 and res.headers["location"] == f"/m/start/car?code={code}"
            child = (await mom.get("/me/family")).json()["children"][0]
            # 2단계는 케어 정보만 바꾸고 1단계 값은 그대로
            assert child["nickname"] == "서아" and child["temperament"] == ["animal", "active"]
            assert child["feeding_type"] == "mixed" and child["nap_times"] == "10:30–12:00 · 15:00–16:00" and child["nap_in_car"] is True

            # 첫 단계로 돌아가 고쳐도 아이가 늘지 않고 케어 정보는 남는다
            await mom.post("/m/start/child", data={"sections": "basic", "nickname": "서아", "birth_month": "2025-09", "temperament": ["quiet"]})
            again = (await mom.get("/me/family")).json()["children"]
            assert len(again) == 1 and again[0]["temperament"] == ["quiet"] and again[0]["feeding_type"] == "mixed"

            # S03: 요청한 차를 보여 주고, 연결하면 차가 로그인된다
            car_step = (await mom.get(f"/m/start/car?code={code}")).text
            assert "아이오닉 5" in car_step and "이 차와 연결하기" in car_step
            await mom.post("/m/car", data={"code": code})
            poll = await client.post(f"/auth/car-login/{start['request_id']}/poll", json={"poll_secret": start["poll_secret"]})
            assert poll.json()["status"] == "approved"

            # 차가 없으면(QR 없이 가입) 케어 단계가 끝나고 홈으로, 로그인 전 접근은 로그인으로
            assert (await mom.post("/m/start/care", data={"sections": "care", "feeding_type": "formula"})).headers["location"] == "/m/"
        async with phone(client) as anon:
            assert (await anon.get("/m/start/child")).status_code == 303
            assert "/m/login?next=" in (await anon.get("/m/start/care")).headers["location"]

    run(scenario)


def test_care_info_edit_covers_the_extra_items_and_api_roundtrip():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_care_info")
            form = (await mom.get("/m/my/children/new")).text
            for text in ("더위 · 추위", "멀미", "소리", "유모차 사용", "완료기 · 유아식", "동물 좋아함"):
                assert text in form
            await mom.post("/m/my/children/new", data={"nickname": "서아", "birth_month": "2025-09", "temperament": ["animal"], "feeding_type": "toddler",
                           "heat_sensitivity": "hot", "motion_sickness": "1", "noise_sensitivity": "sensitive", "stroller_use": "1"})
            child = (await mom.get("/me/family")).json()["children"][0]
            assert child["feeding_type"] == "toddler" and child["heat_sensitivity"] == "hot" and child["motion_sickness"] is True
            assert child["noise_sensitivity"] == "sensitive" and child["stroller_use"] is True and child["nap_in_car"] is None
            edit = (await mom.get(f"/m/my/children/{child['id']}")).text
            assert re.search(r'name="heat_sensitivity" value="hot" checked', edit) and re.search(r'name="stroller_use" value="1" checked', edit)

            # 차 앱(API)도 같은 항목을 읽고 쓴다. 알 수 없는 값은 거절
            car = await car_token(client, mom)
            put = await client.put(f"/me/children/{child['id']}", headers=car, json={"nickname": "서아", "birth_month": "2025-09", "temperament": ["music", "music"], "heat_sensitivity": "cold"})
            assert put.status_code == 200 and put.json()["temperament"] == ["music"] and put.json()["heat_sensitivity"] == "cold"
            bad = await client.put(f"/me/children/{child['id']}", headers=car, json={"nickname": "서아", "birth_month": "2025-09", "heat_sensitivity": "lava"})
            assert bad.status_code == 422

    run(scenario)


def test_care_info_is_used_for_preset_advice_and_course_defaults():
    from app.main import app, get_planner

    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_use")
            assert "권장해요" not in (await mom.get("/m/preset?slot=nap")).text  # 아이 정보가 없으면 권장값도 없다
            await mom.post("/m/my/children/new", data={"nickname": "서아", "birth_month": "2025-09", "heat_sensitivity": "hot", "motion_sickness": "1", "noise_sensitivity": "sensitive", "stroller_use": "1"})

            nap = (await mom.get("/m/preset?slot=nap")).text  # 낮잠 기본 23℃ → 더위 탐 22℃, 소리 예민 → 음량 최소
            assert "서아는 더위를 타는 편이라 22℃를 권장해요" in nap and "미디어 음량을 「최소」로 권장해요" in nap
            boarding = (await mom.get("/m/preset")).text  # 탑승 준비 기본 24℃ → 23℃, 음량 권장은 낮잠에만
            assert "23℃를 권장해요" in boarding and "음량을 「최소」" not in boarding
            sent = (await mom.post("/m/preset", data=PRESET_FORM | {"slot": "nap"})).text
            assert "22℃를 권장해요" in sent

            page = (await mom.get("/m/course")).text  # 멀미 → 20분, 유모차 → 유모차 길 미리 선택
            assert "서아가 멀미가 있어 편도 이동 시간을 20분으로 시작했어요" in page
            assert re.search(r'name="minutes" value="20" checked', page) and re.search(r'name="need" value="stroller" checked', page)
            assert "유모차 길」을 미리 골라" in page

            await mom.get("/m/course?lat=37.55&lng=127.07&minutes=60")  # 조건을 보낸 뒤에는 고른 그대로
            req = planner.requests[0]
            assert req.conditions.max_drive_minutes == 60 and req.conditions.needs == []
            await mom.get("/m/course?lat=37.55&lng=127.07")  # 이동 시간을 안 정하면 멀미 기본값 20분
            assert planner.requests[1].conditions.max_drive_minutes == 20

    planner = _FakePlanner()
    app.dependency_overrides[get_planner] = lambda: planner
    try:
        run(scenario)
    finally:
        app.dependency_overrides.pop(get_planner, None)


def test_korean_particles_follow_the_last_syllable():
    from app.family import subject, topic

    assert topic("서아") == "서아는" and topic("하늘") == "하늘은" and topic("Ben") == "Ben는"
    assert subject("서아") == "서아가" and subject("하늘") == "하늘이"

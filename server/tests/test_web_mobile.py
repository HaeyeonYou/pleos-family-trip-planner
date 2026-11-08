"""폰 웹 로그인 후 화면(홈·케어·그로우·MY)과 차 연결 해제·계정 삭제. pleos_test DB를 쓴다."""

import re
from datetime import date

from app.auth import web
from tests.test_auth import phone, run, signup
from tests.test_presets import PRESET_FORM

TAB_PAGES = ["/m/", "/m/care", "/m/grow", "/m/my"]


async def connect_car(client, mobile, device_name: str = "아이오닉 5") -> dict:
    """폰이 로그인한 계정에 차를 연결하고, 차 인증 헤더를 돌려준다."""
    start = (await client.post("/auth/car-login", json={"device_name": device_name})).json()
    await mobile.post("/m/car", data={"code": start["code"]})
    poll = await client.post(f"/auth/car-login/{start['request_id']}/poll", json={"poll_secret": start["poll_secret"]})
    return {"Authorization": f"Bearer {poll.json()['token']}"}


def test_pages_need_login_and_keep_the_destination():
    async def scenario(client):
        for path in TAB_PAGES + ["/m/preset", "/m/my/delete"]:
            res = await client.get(path)
            assert res.status_code == 303, path
            assert res.headers["location"] == f"/m/login?next={path}"
        assert (await client.post("/m/my/delete", data={"password": "x"})).status_code == 303

    run(scenario)


def test_car_login_hint_only_appears_for_the_car_qr_page():
    async def scenario(client):
        assert "차 화면의 QR" in (await client.get("/m/login?next=/m/car?code=ABC")).text
        assert "차 화면의 QR" not in (await client.get("/m/login?next=/m/care")).text  # 케어 탭은 차 QR이 아니다

    run(scenario)


def test_login_returns_to_the_page_that_was_asked_for():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "mom_next")
            await mobile.get("/m/logout")
            res = await mobile.post("/m/login", data={"login_id": "mom_next", "password": "password123", "next": "/m/care"})
            assert res.status_code == 303 and res.headers["location"] == "/m/care"

    run(scenario)


def test_tab_bar_marks_the_current_tab_and_pages_show_real_values():
    async def scenario(client):
        async with phone(client) as mobile:
            await mobile.post(
                "/m/signup", data={"login_id": "mom_home", "password": "password123", "display_name": "지은", "next": "/m/"}
            )
            for path, tab in zip(TAB_PAGES, ["홈", "케어", "그로우", "MY"]):
                html = (await mobile.get(path)).text
                assert re.search(rf'aria-current="page"><span class="shape[^"]*" aria-hidden="true"></span>{tab}</a>', html), path
                assert html.count('aria-current="page"') == 1

            home = (await mobile.get("/m/")).text
            assert "지은 님," in home and "이번 주 외출 일정이 없어요" in home
            assert "아직 차로 보낸 공조 설정이 없어요" in home and "연결된 차가 없어요" in home
            assert f"<b>{date.today().day}</b>" in home  # 주간 띠에 오늘 날짜

            grow = (await mobile.get("/m/grow")).text
            assert "차 안 기기에만 저장" in grow  # 방문 기록은 서버에 없다는 정직한 안내

    run(scenario)


def test_display_name_is_escaped():
    async def scenario(client):
        async with phone(client) as mobile:
            await mobile.post(
                "/m/signup", data={"login_id": "xss_user", "password": "password123", "display_name": "<script>alert(1)</script>", "next": "/m/"}
            )
            for path in ["/m/", "/m/my"]:
                html = (await mobile.get(path)).text
                assert "<script>alert(1)</script>" not in html and "&lt;script&gt;" in html

    run(scenario)


def test_home_and_care_follow_the_preset_state():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "dad_home")
            car = await connect_car(client, mobile)
            await mobile.post("/m/preset", data=PRESET_FORM)

            for path in ["/m/", "/m/care"]:
                html = (await mobile.get(path)).text
                assert "아기 탑승" in html and "24.5℃" in html and "풍량 2단" in html
                assert "조수석 열선 2단" in html and "핸들 열선 1단" in html and "공기청정" in html
                assert "차 대기 중" in html and "차에서 적용함" not in html

            pending = (await client.get("/presets/pending", headers=car)).json()
            await client.post(f"/presets/{pending['id']}/applied", headers=car)
            assert "차에서 적용함" in (await mobile.get("/m/")).text

            care = (await mobile.get("/m/care")).text
            assert "원격으로 미리 켜기는 안전상 지원하지 않아요" in care  # 정책: 시동 꺼진 차는 원격 제어 불가
            assert "지금 적용" not in care and "10분 전 자동" not in care

    run(scenario)


def test_preset_form_keeps_fields_and_uses_labeled_controls():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "mom_form")
            html = (await mobile.get("/m/preset")).text
            assert 'for="name"' in html and 'for="temperature"' in html
            assert html.count('name="fan_speed"') == 8 and html.count('name="driver_seat_heat"') == 4
            bad = await mobile.post("/m/preset", data=PRESET_FORM | {"temperature": "99"})
            assert bad.status_code == 400 and "값을 다시 확인해 주세요" in bad.text

    run(scenario)


def test_my_lists_cars_and_disconnect_ends_only_that_car():
    async def scenario(client):
        async with phone(client) as mobile, phone(client) as other:
            await signup(mobile, "mom_my")
            await signup(other, "other_my")
            ioniq = await connect_car(client, mobile, "아이오닉 5")
            tucson = await connect_car(client, mobile, "투싼 HEV")
            stranger = await connect_car(client, other, "남의 차")

            html = (await mobile.get("/m/my")).text
            assert "mom_my" in html and "아이오닉 5" in html and "투싼 HEV" in html and "남의 차" not in html
            assert "방금 전" in html and html.count("연결 해제") >= 2
            assert "QR로 다른 차 연결" in html

            ids = re.findall(r'/m/my/cars/([0-9a-f-]{36})/disconnect', html)
            assert len(ids) == 2
            # 다른 사람의 세션 id로는 아무 일도 일어나지 않는다.
            other_html = (await other.get("/m/my")).text
            other_id = re.search(r'/m/my/cars/([0-9a-f-]{36})/disconnect', other_html).group(1)
            res = await mobile.post(f"/m/my/cars/{other_id}/disconnect")
            assert res.status_code == 303
            assert (await client.get("/auth/me", headers=stranger)).status_code == 200

            res = await mobile.post(f"/m/my/cars/{ids[0]}/disconnect")
            assert res.status_code == 303 and res.headers["location"] == "/m/my?notice=disconnected"
            states = {name: (await client.get("/auth/me", headers=h)).status_code for name, h in [("ioniq", ioniq), ("tucson", tucson)]}
            assert sorted(states.values()) == [200, 401]  # 둘 중 눌러 끊은 차만 로그아웃

            after = (await mobile.get("/m/my?notice=disconnected")).text
            assert "차 연결을 해제했어요" in after and after.count("연결 해제</button>") == 1

    run(scenario)


def test_delete_account_needs_password_and_removes_everything():
    async def scenario(client):
        async with phone(client) as mobile:
            await signup(mobile, "mom_del")
            car = await connect_car(client, mobile)
            await mobile.post("/m/preset", data=PRESET_FORM)

            page = await mobile.get("/m/my/delete")
            assert page.status_code == 200 and 'for="password"' in page.text and "되돌릴 수 없어요" in page.text

            wrong = await mobile.post("/m/my/delete", data={"password": "wrong-password"})
            assert wrong.status_code == 400 and "비밀번호가 맞지 않아요" in wrong.text
            assert (await client.get("/auth/me", headers=car)).status_code == 200  # 아직 그대로

            done = await mobile.post("/m/my/delete", data={"password": "password123"})
            assert done.status_code == 303 and done.headers["location"] == "/m/login"
            assert (await client.get("/auth/me", headers=car)).status_code == 401
            relogin = await client.post("/m/login", data={"login_id": "mom_del", "password": "password123", "next": "/m/"})
            assert relogin.status_code == 400
            # 같은 아이디로 다시 가입할 수 있고, 새 계정에는 예전 프리셋이 없다.
            async with phone(client) as again:
                assert (await signup(again, "mom_del")).status_code == 303
                assert "아직 차로 보낸 공조 설정이 없어요" in (await again.get("/m/")).text

    run(scenario)


def test_stylesheet_is_served_without_external_resources():
    async def scenario(client):
        css = await client.get("/m/assets/m.css")
        assert css.status_code == 200 and "text/css" in css.headers["content-type"]
        assert "http://" not in css.text and "https://" not in css.text and "@import" not in css.text
        async with phone(client) as mobile:
            await signup(mobile, "mom_css")
            html = (await mobile.get("/m/")).text
            assert 'href="/m/assets/m.css"' in html and "https://" not in html

    run(scenario)


def test_greeting_and_week_helpers():
    assert web._greeting(7) == "좋은 아침이에요" and web._greeting(13) == "좋은 오후예요"
    assert web._greeting(19) == "좋은 저녁이에요" and web._greeting(2) == "편안한 밤이에요"
    week = web._week(date(2026, 11, 8))  # 일요일
    assert [d["label"] for d in week] == ["일", "월", "화", "수", "목", "금", "토"]
    assert [d["day"] for d in week] == [8, 9, 10, 11, 12, 13, 14] and week[0]["today"] and sum(d["today"] for d in week) == 1
    assert [d["day"] for d in web._week(date(2026, 11, 14))][0] == 8  # 토요일에도 같은 주

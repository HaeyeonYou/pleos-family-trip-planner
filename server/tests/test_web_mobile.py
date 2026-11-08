"""폰 웹 로그인 후 화면(홈·케어·그로우·MY)과 차 연결 해제·계정 삭제. pleos_test DB를 쓴다."""

import re
import uuid
from datetime import date

from app.auth import web_app as web
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
            assert "아직 차로 보낸 공조 설정이 없어요" in home and "연결된 차가 없어요" in home and "수유 시각을 기록해 보세요" in home
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
                assert "탑승 준비" in html and "24.5℃" in html and "풍량 2단" in html
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


def test_care_page_feeding_timer_and_records():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_feedweb")
            care = (await mom.get("/m/care")).text
            assert "아직 수유 기록이 없어요" in care and "지금 수유했어요" in care and "시간 선택" in care

            await mom.post("/m/my/children/new", data={"nickname": "서아", "birth_month": "2025-09", "feeding_type": "formula", "feed_interval_minutes": "180", "nap_count": "2"})
            res = await mom.post("/m/care/feed", data={"mode": "now"})
            assert res.status_code == 303 and res.headers["location"] == "/m/care?notice=fed"
            care = (await mom.get("/m/care?notice=fed")).text
            assert "수유 시각을 기록했어요" in care and "다음 수유" in care and "3시간 간격" in care and "3시간 남음" in care
            assert "마지막 수유" in (await mom.get("/m/")).text  # 홈 한 줄

            # 시간 선택 + 종류 + 양. 지금보다 늦은 시각은 어제 기록
            res = await mom.post("/m/care/feed", data={"mode": "time", "time": "00:01", "kind": "breast", "amount": ""})
            assert res.status_code == 303
            page = (await mom.get("/m/care/feed")).text
            assert "모유" in page and "분유" in page and "오늘 기록" in page
            bad = await mom.post("/m/care/feed", data={"mode": "time", "time": "abc"})
            assert bad.status_code == 400 and "다시 확인" in bad.text
            assert (await mom.post("/m/care/feed", data={"mode": "time", "time": "10:00", "amount": "9999"})).status_code == 400

            api = (await mom.get("/me/feedings")).json()
            assert len(api) == 2 and api[0]["source"] == "web"  # 웹에서 쌓은 기록은 API로도 같은 값
            fid = api[0]["id"]
            await mom.post(f"/m/care/feed/{fid}/delete")
            assert len((await mom.get("/me/feedings")).json()) == 1

    run(scenario)


def test_preset_slots_on_the_web():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_slots")
            care = (await mom.get("/m/care")).text
            for label in ["탑승 준비", "낮잠", "수유 정차"]:
                assert label in care
            assert care.count("보내지 않음") == 3

            page = (await mom.get("/m/preset?slot=nap")).text
            assert "낮잠" in page and 'name="slot" value="nap"' in page and 'name="media_volume"' in page
            res = await mom.post("/m/preset", data={"slot": "nap", "temperature": "22.5", "fan_speed": "1", "media_volume": "10", "power": "1"})
            assert res.status_code == 200 and "차로 보냈어요" in res.text and 'value="10" checked' in res.text
            states = {s["slot"]: s for s in (await mom.get("/presets")).json()}
            assert states["nap"]["settings"]["media_volume"] == 10 and states["nap"]["pending"] and not states["boarding"]["sent"]
            assert states["nap"]["settings"]["name"] == "낮잠"  # 이름을 비우면 슬롯 이름

            # 음량 '그대로'는 값 없음
            await mom.post("/m/preset", data={"slot": "nap", "temperature": "22.5", "fan_speed": "1", "media_volume": ""})
            assert {s["slot"]: s for s in (await mom.get("/presets")).json()}["nap"]["settings"]["media_volume"] is None
            care = (await mom.get("/m/care")).text
            assert "차 대기 중" in care and care.count("보내지 않음") == 2
            # 모르는 슬롯은 탑승 준비로
            assert 'name="slot" value="boarding"' in (await mom.get("/m/preset?slot=bogus")).text

    run(scenario)


def test_my_children_members_and_clearing_records():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_myfam")
            await signup(other, "other_myfam")
            my = (await mom.get("/m/my")).text
            assert "아이 정보 입력하기" in my and "아직 등록한 아이가 없어요" in my

            res = await mom.post("/m/my/children/new", data={"nickname": "서아", "birth_month": "2025-09", "feeding_type": "mixed", "feed_interval_minutes": "180", "nap_count": "2"})
            assert res.status_code == 303 and res.headers["location"] == "/m/my?notice=child_saved"
            my = (await mom.get("/m/my")).text
            assert "서아 · " in my and "개월" in my and "혼합 수유 · 3시간 간격 · 낮잠 2회" in my
            child_id = re.search(r'/m/my/children/([0-9a-f-]{36})"', my).group(1)

            form = (await mom.get(f"/m/my/children/{child_id}")).text
            assert 'value="서아"' in form and 'value="2025-09"' in form and 'value="mixed" checked' in form and 'value="180" checked' in form
            await mom.post(f"/m/my/children/{child_id}", data={"nickname": "서아", "birth_month": "2025-09", "feeding_type": "formula", "feed_interval_minutes": "", "nap_count": "1"})
            assert "분유 수유 · 낮잠 1회" in (await mom.get("/m/my")).text
            assert len((await mom.get("/me/family")).json()["children"]) == 1  # API와 같은 데이터

            bad = await mom.post("/m/my/children/new", data={"nickname": "", "birth_month": "2099-01"})
            assert bad.status_code == 400 and "다시 확인" in bad.text
            # 남의 아이는 열 수도 지울 수도 없다
            assert (await other.get(f"/m/my/children/{child_id}")).headers["location"] == "/m/my"
            await other.post(f"/m/my/children/{child_id}/delete")
            assert len((await mom.get("/me/family")).json()["children"]) == 1

            await mom.post("/m/my/members", data={"label": "엄마", "is_driver": "1", "is_default": "1"})
            await mom.post("/m/my/members", data={"label": "할머니", "is_default": "0"})
            assert (await mom.post("/m/my/members", data={"label": ""})).headers["location"] == "/m/my?notice=member_error"
            my = (await mom.get("/m/my")).text
            assert "엄마 · 운전" in my and "할머니" in my and "자주 함께" in my
            member_id = re.search(r'/m/my/members/([0-9a-f-]{36})/delete', my).group(1)
            await mom.post(f"/m/my/members/{member_id}/delete")
            assert len((await mom.get("/me/family")).json()["members"]) == 1

            # 기록 삭제: 확인 화면 → POST
            await mom.post("/m/care/feed", data={"mode": "now"})
            await mom.put(f"/me/visits/{uuid.uuid4()}", json={"place_name": "서울숲", "place_key": "tour:1", "theme": "nature", "visited_on": "2026-11-05"})
            assert "되돌릴 수 없어요" in (await mom.get("/m/my/clear/visits")).text
            assert (await mom.get("/m/my/clear/bogus")).status_code == 303
            await mom.post("/m/my/clear/visits")
            assert (await mom.get("/me/visits")).json() == [] and len((await mom.get("/me/feedings")).json()) == 1
            await mom.post("/m/my/clear/feedings")
            assert (await mom.get("/me/feedings")).json() == []

            await mom.post(f"/m/my/children/{child_id}/delete")
            assert (await mom.get("/me/family")).json()["children"] == []

    run(scenario)

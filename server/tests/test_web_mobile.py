"""폰 웹 로그인 후 화면(홈·케어·그로우·MY)과 차 연결 해제·계정 삭제. pleos_test DB를 쓴다."""

import re
import uuid
from datetime import date, timedelta

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
                "/m/signup", data={"agree": "1", "login_id": "mom_home", "password": "password123", "display_name": "지은", "next": "/m/"}
            )
            for path, tab in zip(TAB_PAGES, ["홈", "케어", "그로우", "MY"]):
                html = (await mobile.get(path)).text
                assert re.search(rf'aria-current="page"><svg class="ico"[^>]*>.*?</svg>{tab}</a>', html, re.S), path
                assert html.count('aria-current="page"') == 1

            home = (await mobile.get("/m/")).text
            assert "지은 님," in home and "이번 주 외출 일정이 없어요" in home
            assert "아직 차로 보낸 공조 설정이 없어요" in home and "연결된 차가 없어요" in home and "수유 시각을 기록해 보세요" in home
            assert f"<b>{date.today().day}</b>" in home  # 주간 띠에 오늘 날짜

            grow = (await mobile.get("/m/grow")).text
            assert "아직 방문 기록이 없어요" in grow and "위치 좌표와 음성은 저장하지 않아요" in grow  # 빈 상태와 저장 범위 안내

    run(scenario)


def test_display_name_is_escaped():
    async def scenario(client):
        async with phone(client) as mobile:
            await mobile.post(
                "/m/signup", data={"agree": "1", "login_id": "xss_user", "password": "password123", "display_name": "<script>alert(1)</script>", "next": "/m/"}
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
            assert re.search(r'href="/m/assets/m.css\?v=\d+"', html) and "https://" not in html  # 수정하면 주소가 바뀌어 캐시가 새로 받는다

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


def _visit(name, theme, day, status="confirmed", key=None, **extra):
    return {"place_key": key or f"tour:{name}", "place_name": name, "theme": theme, "visited_on": day, "stayed_minutes": 90, "status": status} | extra


def test_grow_experience_map_theme_sheet_and_visit_confirmation():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_grow")
            await signup(other, "other_grow")
            today = date.today()
            yesterday = (today - timedelta(days=1)).isoformat()
            long_ago = (today - timedelta(days=30)).isoformat()
            await mom.post("/m/my/children/new", data={"nickname": "서아", "birth_month": "2025-09"})
            rows = [
                _visit("서울숲", "nature", long_ago),
                _visit("서울숲", "nature", (today - timedelta(days=20)).isoformat(), mood="excited"),
                _visit("수목원", "nature", (today - timedelta(days=15)).isoformat()),
                _visit("키즈카페", "play", today.isoformat()),
                _visit("어제 간 곳", "experience", yesterday, status="pending"),
            ]
            await mom.post("/me/visits/bulk", json={"visits": rows})

            grow = (await mom.get("/m/grow")).text
            assert "서아의 경험 우주" in grow and "다녀온 곳 1곳 확인하기" in grow
            # 젤리 12분류 (#186): 서울숲·수목원 = 자연·숲, 키즈카페 = 몸놀이
            assert "별 2개 · 4개의 경험 · 3개 장소" in grow and "서울숲 · 2회" in grow and "수목원" in grow and "키즈카페" in grow
            assert "자연·숲 · 곰 방울" in grow and "몸놀이 · 말 방울" in grow
            assert "비어 있는 별" in grow and "과학·박물관" in grow and "/m/course?theme=exhibition" in grow
            assert "어제 간 곳" in grow  # 확인 대기 안내에 장소 이름

            star = (await mom.get("/m/grow/jelly/nature")).text
            assert "자연·숲" in star and "서울숲 · 2회" in star and "신났어요" in star and "이 분야로 코스 받기" in star
            assert "/m/course?theme=nature" in star
            assert (await mom.get("/m/grow/jelly/science")).status_code == 303  # 경험 없는 분류는 경험 우주로
            assert (await mom.get("/m/grow/theme/nature")).status_code == 303  # 예전 5분야 주소

            home = (await mom.get("/m/")).text
            assert "다녀온 곳 1곳 확인하기" in home and "다시 가볼까요?" in home and "서울숲" in home and "2번 다녀왔어요" in home

            pending = (await mom.get("/me/visits?status=pending")).json()[0]
            page = (await mom.get(f"/m/grow/visit/{pending['id']}")).text
            assert "어제 간 곳에 다녀왔나요?" in page and 'value="excited" checked' in page
            res = await mom.post(f"/m/grow/visit/{pending['id']}", data={"action": "confirm", "mood": "sleepy"})
            assert res.status_code == 303 and res.headers["location"] == "/m/grow?notice=saved"
            saved = (await mom.get("/me/visits?status=confirmed")).json()
            assert any(v["place_name"] == "어제 간 곳" and v["mood"] == "sleepy" for v in saved)
            assert "방문을 기록했어요" in (await mom.get("/m/grow?notice=saved")).text
            assert (await mom.get(f"/m/grow/visit/{pending['id']}")).status_code == 303  # 이미 확인함

            # 안 갔어요 → 경험맵에 들어가지 않는다
            other_pending = _visit("안 간 곳", "pet", yesterday, status="pending")
            await mom.post("/me/visits/bulk", json={"visits": [other_pending]})
            vid = (await mom.get("/me/visits?status=pending")).json()[0]["id"]
            await mom.post(f"/m/grow/visit/{vid}", data={"action": "dismiss"})
            assert "안 간 곳" not in (await mom.get("/m/grow")).text

            # 남의 방문은 열 수도 확인할 수도 없다
            assert (await other.get(f"/m/grow/visit/{vid}")).status_code == 303
            assert (await other.get("/m/grow")).text.count("아직 방문 기록이 없어요") == 1

    run(scenario)


class _FakePlanner:
    """실제 API 없이 /m/course가 조건을 어떻게 만드는지만 본다."""

    def __init__(self, fail=None):
        self.requests = []
        self.fail = fail

    def suggest(self, req):
        self.requests.append(req)
        if self.fail:
            raise self.fail
        from app.schemas import CourseOption, CourseResponse, Place, Stop, StopRole, SuggestResponse

        place = Place(name="아차산 생태공원", latitude=37.55, longitude=127.1, source="tour", source_id="1", amenities={"nursing_room": "available", "stroller": "unknown"})
        stops = [
            Stop(role=StopRole.OUTING, place=place, arrive_at="10:40", depart_at="12:10", stay_minutes=90, drive_minutes=25, drive_distance_m=9000),
            Stop(role=StopRole.HOME, place=place, arrive_at="13:30", drive_minutes=25, drive_distance_m=9000),
        ]
        course = CourseResponse(departure_at="10:15", stops=stops, total_drive_minutes=50, return_at="13:30", route_source="estimate")
        option = CourseOption(name="자연 · 아차산 생태공원", summary="아차산 생태공원 → 귀가", total_minutes=195, first_drive_minutes=25, place_key="tour:1", course=course)
        return SuggestResponse(options=[option])


def test_course_page_builds_conditions_from_family_and_shows_options():
    from app.main import app, get_planner

    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_course")
            await mom.post("/m/my/children/new", data={"nickname": "서아", "birth_month": "2025-09"})
            await mom.post("/m/my/members", data={"label": "엄마", "is_default": "1"})
            await mom.post("/m/my/members", data={"label": "아빠", "is_default": "1"})
            await mom.post("/me/visits/bulk", json={"visits": [_visit("서울숲", "nature", "2026-11-01"), _visit("대기", "play", "2026-11-02", status="pending")]})

            form = (await mom.get("/m/course")).text  # 위치 없이: 조건 폼과 위치 요청만
            assert "내 위치로 코스 받기" in form and "추천 코스 3개" not in form and "위치는 저장하지 않고" in form
            assert not planner.requests
            # 위치가 막힌 이유를 구분해 안내한다 (HTTPS가 아닌 주소 / 권한 거부 / 시간 초과)
            assert "window.isSecureContext" in form and "보안 연결(HTTPS)이 아니라" in form
            assert "위치 권한이 없어" in form and "시간이 너무 걸려요" in form

            res = await mom.get("/m/course?lat=37.55&lng=127.07&need=nursing_room&need=bogus&indoor=1&minutes=60&theme=nature")
            assert res.status_code == 200 and "추천 코스 1개" in res.text and "아차산 생태공원" in res.text and "수유실" in res.text
            assert "추천 1순위" in res.text and "총 3시간 15분" in res.text and "우리 집" in res.text
            assert "일정으로 저장하는 기능은 아직 준비 중" in res.text  # 일정 저장은 하지 않는다
            req = planner.requests[0]
            c = req.conditions
            assert req.origin.latitude == 37.55 and c.child_age_months and c.adults == 2
            assert c.needs == ["nursing_room"] and c.indoor_only and c.max_drive_minutes == 60 and [t.value for t in c.themes] == ["nature"]
            assert [(v.place_key, v.date) for v in req.visits] == [("tour:서울숲", "2026-11-01")]  # 확정된 방문만 추천에 반영

            assert (await mom.get("/m/course?lat=999&lng=127")).status_code == 400

    planner = _FakePlanner()
    app.dependency_overrides[get_planner] = lambda: planner
    try:
        run(scenario)
    finally:
        app.dependency_overrides.pop(get_planner, None)


def test_course_page_explains_failures_honestly():
    import httpx
    from fastapi import HTTPException

    from app.course.planner import NoCourseError
    from app.main import app, get_planner

    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_coursefail")
            url = "/m/course?lat=37.55&lng=127.07"
            res = await mom.get(url)
            assert res.status_code == 502 and "서버 설정이 아직 없어요" in res.text
            planners["p"] = _FakePlanner(fail=NoCourseError("none"))
            res = await mom.get(url)
            assert "조건에 맞는 코스를 찾지 못했어요" in res.text
            planners["p"] = _FakePlanner(fail=httpx.ConnectError("down"))
            assert "외부 장소 정보를 불러오지 못했어요" in (await mom.get(url)).text
            assert (await phone(client).get(url)).status_code == 303  # 로그인 필요

    planners = {}

    def unavailable():
        if "p" in planners:
            return planners["p"]
        raise HTTPException(503, "키 없음")

    app.dependency_overrides[get_planner] = unavailable
    try:
        run(scenario)
    finally:
        app.dependency_overrides.pop(get_planner, None)

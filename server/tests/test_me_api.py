"""가족 정보·수유 기록·방문 기록·경험맵·프리셋 3개 API (폰 웹 쿠키와 차 앱 Bearer 둘 다). pleos_test DB를 쓴다."""

import uuid
from datetime import date, datetime, timedelta, timezone

from tests.test_auth import phone, run, signup
from tests.test_presets import PRESET_FORM, car_token

KST = timezone(timedelta(hours=9))


def now_iso(minutes_ago: int = 0) -> str:
    return (datetime.now(KST) - timedelta(minutes=minutes_ago)).isoformat()


def this_month(day: int = 1) -> str:
    today = datetime.now(KST).date()
    return today.replace(day=min(day, today.day)).isoformat()


CHILD = {"nickname": "서아", "birth_month": "2025-09", "feeding_type": "mixed", "feed_interval_minutes": 180, "nap_count": 2}


def test_family_children_and_members_crud_is_private_and_idempotent():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_family")
            await signup(other, "other_family")
            car = await car_token(client, mom)
            cid = str(uuid.uuid4())

            res = await mom.put(f"/me/children/{cid}", json=CHILD)
            assert res.status_code == 200
            body = res.json()
            assert body["nickname"] == "서아" and body["age_months"] >= 14 and body["feeding_type"] == "mixed"
            # 같은 id로 다시 보내면 고쳐질 뿐 늘지 않는다 (차가 재전송해도 안전)
            await mom.put(f"/me/children/{cid}", json=CHILD | {"nap_count": 1})
            family = (await client.get("/me/family", headers=car)).json()  # 차 앱(Bearer)도 같은 데이터
            assert len(family["children"]) == 1 and family["children"][0]["nap_count"] == 1

            mid = str(uuid.uuid4())
            assert (await mom.put(f"/me/members/{mid}", json={"label": "엄마", "is_driver": True})).status_code == 200
            await mom.put(f"/me/members/{uuid.uuid4()}", json={"label": "할머니", "is_default": False})
            members = (await mom.get("/me/family")).json()["members"]
            assert [m["label"] for m in members] == ["엄마", "할머니"] and members[0]["is_driver"] and not members[1]["is_default"]

            # 남의 데이터는 보이지도 고쳐지지도 지워지지도 않는다
            assert (await other.get("/me/family")).json() == {"children": [], "members": []}
            assert (await other.put(f"/me/children/{cid}", json=CHILD)).status_code == 404
            assert (await other.delete(f"/me/members/{mid}")).status_code == 404
            assert (await client.get("/me/family")).status_code == 401  # 로그인 필요

            assert (await mom.delete(f"/me/children/{cid}")).status_code == 204
            assert (await mom.get("/me/family")).json()["children"] == []

    run(scenario)


def test_child_validation():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_valid")
            url = f"/me/children/{uuid.uuid4()}"
            assert (await mom.put(url, json=CHILD | {"birth_month": "2099-01"})).status_code == 422  # 미래
            assert (await mom.put(url, json=CHILD | {"birth_month": "2025-13"})).status_code == 422
            assert (await mom.put(url, json=CHILD | {"nickname": "  "})).status_code == 422
            assert (await mom.put(url, json=CHILD | {"feed_interval_minutes": 10})).status_code == 422
            assert (await mom.put(url, json=CHILD | {"feeding_type": "water"})).status_code == 422
            for i in range(6):
                assert (await mom.put(f"/me/children/{uuid.uuid4()}", json=CHILD | {"nickname": f"아이{i}"})).status_code == 200
            assert (await mom.put(f"/me/children/{uuid.uuid4()}", json=CHILD)).status_code == 409  # 6명까지

    run(scenario)


def test_feedings_record_status_and_next_time():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_feed")
            await signup(other, "other_feed")
            car = await car_token(client, mom)
            cid = str(uuid.uuid4())
            await mom.put(f"/me/children/{cid}", json=CHILD)

            empty = (await mom.get("/me/feedings/status")).json()
            assert empty["last_fed_at"] is None and empty["next_due_at"] is None and empty["interval_minutes"] == 180

            fid = str(uuid.uuid4())
            body = {"fed_at": now_iso(40), "kind": "formula", "amount_ml": 160}
            res = await mom.put(f"/me/feedings/{fid}", json=body)
            assert res.status_code == 200 and res.json()["child_id"] == cid  # 비우면 첫째 아이
            await client.put(f"/me/feedings/{fid}", json=body | {"source": "car"}, headers=car)  # 차 재전송은 같은 기록
            assert len((await mom.get("/me/feedings")).json()) == 1

            status = (await mom.get("/me/feedings/status")).json()
            assert status["nickname"] == "서아" and 138 <= status["minutes_left"] <= 141 and not status["overdue"]

            await mom.post("/me/feedings", json={"fed_at": now_iso(300), "kind": "breast"})
            assert [f["kind"] for f in (await mom.get("/me/feedings")).json()] == ["formula", "breast"]  # 최신순
            await mom.put(f"/me/feedings/{uuid.uuid4()}", json={"fed_at": now_iso(), "kind": "solid"})
            assert (await mom.get("/me/feedings/status")).json()["minutes_left"] >= 178

            assert (await other.get("/me/feedings")).json() == []
            assert (await other.delete(f"/me/feedings/{fid}")).status_code == 404
            assert (await mom.post("/me/feedings", json={"fed_at": "2026-11-08T12:00:00", "kind": "formula"})).status_code == 422  # 시간대 없음
            assert (await mom.post("/me/feedings", json={"fed_at": (datetime.now(KST) + timedelta(hours=3)).isoformat()})).status_code == 422
            assert (await mom.post("/me/feedings", json={"fed_at": now_iso(), "child_id": str(uuid.uuid4())})).status_code == 404
            assert (await mom.delete(f"/me/feedings/{fid}")).status_code == 204

    run(scenario)


def test_overdue_feeding():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_overdue")
            await mom.put(f"/me/children/{uuid.uuid4()}", json=CHILD | {"feed_interval_minutes": 120})
            await mom.post("/me/feedings", json={"fed_at": now_iso(150), "kind": "formula"})
            status = (await mom.get("/me/feedings/status")).json()
            assert status["overdue"] and status["minutes_left"] < 0

    run(scenario)


def visit(name, theme, day, key=None, **extra):
    return {"place_key": key if key is not None else f"tour:{name}", "place_name": name, "theme": theme, "visited_on": day, "stayed_minutes": 90} | extra


def test_visits_upload_confirm_summary_and_privacy():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_visit")
            await signup(other, "other_visit")
            car = await car_token(client, mom)

            vid = str(uuid.uuid4())
            res = await client.put(f"/me/visits/{vid}", json=visit("서울숲", "nature", "2026-11-05"), headers=car)
            assert res.status_code == 200 and res.json()["status"] == "confirmed"
            bulk = {"visits": [{"id": vid} | visit("서울숲", "nature", "2026-11-05"), visit("키즈카페", "play", "2026-10-20", status="pending")]}
            assert (await client.post("/me/visits/bulk", json=bulk, headers=car)).json() == {"saved": 2}  # 다시 보내도 중복 없음
            all_visits = (await mom.get("/me/visits")).json()
            assert len(all_visits) == 2
            # 받지 않는 정보는 응답에도 없다: 좌표·음성·도착 시각
            assert set(all_visits[0]) == {"id", "place_key", "place_name", "theme", "visited_on", "stayed_minutes", "status", "mood"}
            extra = visit("몰래", "nature", "2026-11-01") | {"latitude": 37.5, "longitude": 127.0, "voice_url": "x"}
            saved = (await mom.put(f"/me/visits/{uuid.uuid4()}", json=extra)).json()
            assert "latitude" not in saved and "voice_url" not in saved
            await mom.delete(f"/me/visits/{saved['id']}")

            summary = (await mom.get("/me/visits/summary")).json()  # 확정만, /course/suggest의 visits 모양
            assert summary == [{"place_key": "tour:서울숲", "theme": "nature", "date": "2026-11-05"}]

            pending = (await mom.get("/me/visits?status=pending")).json()
            pid = pending[0]["id"]
            res = await mom.patch(f"/me/visits/{pid}", json={"status": "confirmed", "mood": "excited"})
            assert res.json()["status"] == "confirmed" and res.json()["mood"] == "excited"
            assert len((await mom.get("/me/visits/summary")).json()) == 2

            assert (await other.get("/me/visits")).json() == []
            assert (await other.patch(f"/me/visits/{pid}", json={"mood": "bad"})).status_code == 404
            assert (await other.put(f"/me/visits/{vid}", json=visit("가로채기", "nature", "2026-11-05"))).status_code == 404
            assert (await mom.put(f"/me/visits/{uuid.uuid4()}", json=visit("", "nature", "2026-11-05"))).status_code == 422
            assert (await mom.put(f"/me/visits/{uuid.uuid4()}", json=visit("x", "space", "2026-11-05"))).status_code == 422

            assert (await mom.delete("/me/visits")).status_code == 204
            assert (await mom.get("/me/visits")).json() == []

    run(scenario)


def test_summary_works_as_course_request_visits():
    from app.schemas import CourseRequest, LatLng, TripConditions

    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_ride")
            await mom.put(f"/me/visits/{uuid.uuid4()}", json=visit("서울숲", "nature", "2026-11-05"))
            summary = (await mom.get("/me/visits/summary")).json()
            req = CourseRequest(conditions=TripConditions(), origin=LatLng(latitude=37.5, longitude=127.0), visits=summary)
            assert req.visits[0].place_key == "tour:서울숲"

    run(scenario)


def test_experience_map_groups_places_and_finds_empty_branches():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_map")
            month_day = this_month()
            rows = [
                visit("서울숲", "nature", "2026-09-01"),
                visit("서울숲", "nature", "2026-10-01", mood="excited"),
                visit("수목원", "nature", "2026-10-15"),
                visit("키즈카페", "play", month_day),
                visit("이름만 있는 곳", None, "2026-10-02", key=""),
                visit("안 간 곳", "experience", "2026-10-03", status="dismissed"),
                visit("대기 중", "exhibition", "2026-10-04", status="pending"),
            ]
            await mom.post("/me/visits/bulk", json={"visits": rows})
            m = (await mom.get("/me/experience-map")).json()
            assert m["visit_count"] == 5 and m["place_count"] == 4 and m["pending_count"] == 1
            nature = next(t for t in m["themes"] if t["theme"] == "nature")
            assert nature["label"] == "자연" and nature["visits"] == 3
            assert [p["place_name"] for p in nature["places"]] == ["서울숲", "수목원"] and nature["places"][0]["visits"] == 2
            assert nature["places"][0]["last_mood"] == "excited"
            assert m["themes"][0]["theme"] == "nature"  # 방문 많은 순
            assert set(m["empty_themes"]) == {"experience", "exhibition", "pet"}  # 확정되지 않은 분야는 빈 가지
            assert m["new_themes_this_month"] == ["play"] and m["this_month_visits"] == 1

            assert (await phone(client).get("/me/experience-map")).status_code == 401

    run(scenario)


def test_three_presets_with_volume_and_legacy_car_still_works():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_presets3")
            await signup(other, "other_presets3")
            car = await car_token(client, mom)

            states = (await mom.get("/presets")).json()
            assert [s["slot"] for s in states] == ["boarding", "nap", "feeding_stop"]
            assert [s["label"] for s in states] == ["탑승 준비", "낮잠", "수유 정차"]
            assert all(not s["sent"] and not s["pending"] and s["id"] is None for s in states)
            assert states[0]["settings"]["media_volume"] == 30 and states[1]["settings"]["temperature"] == 23  # 기본값

            nap = {"name": "낮잠", "temperature": 23, "fan_speed": 1, "media_volume": 15}
            res = await mom.put("/presets/nap", json=nap)
            assert res.status_code == 200 and res.json()["pending"] and res.json()["settings"]["media_volume"] == 15
            # 슬롯을 모르는 예전 차 앱도 낮잠으로 보낸 것을 받는다 (slot은 덤으로 온다)
            seen = (await client.get("/presets/pending", headers=car)).json()
            assert seen["slot"] == "nap" and seen["settings"]["temperature"] == 23 and set(seen) == {"id", "settings", "sent_at", "slot"}

            # 웹 폼(예전 방식)은 탑승 준비 슬롯에 저장된다. 더 최근에 보냈으니 이것이 먼저 온다
            await mom.post("/m/preset", data=PRESET_FORM)
            legacy = (await client.get("/presets/pending", headers=car)).json()
            assert legacy["slot"] == "boarding" and legacy["settings"]["temperature"] == 24.5 and legacy["settings"]["media_volume"] is None
            assert (await client.post(f"/presets/{legacy['id']}/applied", headers=car)).status_code == 204
            # 탑승 준비를 적용했으니 아직 남은 낮잠이 다음으로 온다
            assert (await client.get("/presets/pending", headers=car)).json()["slot"] == "nap"

            states = {s["slot"]: s for s in (await client.get("/presets", headers=car)).json()}
            assert states["boarding"]["sent"] and not states["boarding"]["pending"] and states["nap"]["pending"]
            # 낮잠도 id로 적용 완료 표시
            assert (await client.post(f"/presets/{states['nap']['id']}/applied", headers=car)).status_code == 204
            assert not {s["slot"]: s for s in (await mom.get("/presets")).json()}["nap"]["pending"]

            # 차에서 정차 중 직접 고친 것은 이미 적용된 값이라 대기로 남지 않는다
            res = await client.put("/presets/feeding_stop?applied_on_car=true", json={"name": "수유 정차", "temperature": 25}, headers=car)
            assert res.status_code == 200 and not res.json()["pending"] and res.json()["applied_at"]

            assert (await mom.put("/presets/weekend", json=nap)).status_code == 422
            assert (await mom.put("/presets/nap", json=nap | {"media_volume": 101})).status_code == 422
            assert all(not s["sent"] for s in (await other.get("/presets")).json())

    run(scenario)


def test_account_delete_removes_all_new_data():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_wipe")
            await mom.put(f"/me/children/{uuid.uuid4()}", json=CHILD)
            await mom.put(f"/me/members/{uuid.uuid4()}", json={"label": "아빠"})
            await mom.post("/me/feedings", json={"fed_at": now_iso(), "kind": "formula"})
            await mom.put(f"/me/visits/{uuid.uuid4()}", json=visit("서울숲", "nature", "2026-11-05"))
            await mom.put("/presets/nap", json={"name": "낮잠"})
            assert (await mom.post("/m/my/delete", data={"password": "password123"})).status_code == 303
            async with phone(client) as again:
                await signup(again, "mom_wipe")
                assert (await again.get("/me/family")).json() == {"children": [], "members": []}
                assert (await again.get("/me/visits")).json() == [] and (await again.get("/me/feedings")).json() == []
                assert all(not s["sent"] for s in (await again.get("/presets")).json())

    run(scenario)

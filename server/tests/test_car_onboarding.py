"""차 온보딩 O01~O06(#184)이 쓰는 서버 값: 생년월일(일까지)·카시트 뒤보기·자주 가는 케어 장소. pleos_test DB를 쓴다."""

import uuid
from datetime import date, timedelta

from app import family
from tests.test_auth import phone, run, signup
from tests.test_me_api import CHILD
from tests.test_presets import car_token


def test_age_months_counts_the_day_when_birth_date_is_known():
    born = date(2025, 9, 3)
    assert family.age_months(born.replace(day=1), today=date(2026, 11, 2), birth_date=born) == 13  # 생일 전날
    assert family.age_months(born.replace(day=1), today=date(2026, 11, 3), birth_date=born) == 14
    assert family.age_months(born.replace(day=1), today=date(2026, 11, 2)) == 14  # 달만 알면 예전 그대로
    assert family.age_months(date(2026, 11, 1), today=date(2026, 11, 5), birth_date=date(2026, 11, 5)) == 0


def test_child_birth_date_and_rear_facing_seat_round_trip_and_stay_compatible():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_birthdate")
            car = await car_token(client, mom)
            cid = str(uuid.uuid4())
            url = f"/me/children/{cid}"

            body = {"nickname": "서아", "birth_date": "2025-09-03", "rear_facing_seat": True, "stroller_use": True}
            res = await client.put(url, headers=car, json=body)  # 차 O04: 일까지 + 카시트 뒤보기
            assert res.status_code == 200, res.text
            out = res.json()
            assert out["birth_date"] == "2025-09-03" and out["birth_month"] == "2025-09"  # 달은 일에서 계산
            assert out["rear_facing_seat"] is True and out["stroller_use"] is True

            # 예전 클라이언트가 같은 달만 다시 보내도 이미 아는 일은 지우지 않는다
            again = (await mom.put(url, json={"nickname": "서아", "birth_month": "2025-09"})).json()
            assert again["birth_date"] == "2025-09-03" and again["rear_facing_seat"] is None
            # 달을 바꾸면 그 달과 안 맞는 일은 버린다
            moved = (await mom.put(url, json={"nickname": "서아", "birth_month": "2025-08"})).json()
            assert moved["birth_date"] is None and moved["birth_month"] == "2025-08"
            # 둘 다 보내면 일이 이긴다
            both = (await mom.put(url, json={"nickname": "서아", "birth_month": "2024-01", "birth_date": "2025-09-03"})).json()
            assert both["birth_month"] == "2025-09"

            tomorrow = (family.today_kst() + timedelta(days=1)).isoformat()
            assert (await mom.put(url, json={"nickname": "서아", "birth_date": tomorrow})).status_code == 422  # 예정일 모드 없음
            assert (await mom.put(url, json={"nickname": "서아", "birth_date": "2025-02-30"})).status_code == 422
            assert (await mom.put(url, json={"nickname": "서아"})).status_code == 422  # 생년월일 필수

            # 예전 모양(달만)도 그대로 받는다
            old = (await mom.put(f"/me/children/{uuid.uuid4()}", json=CHILD)).json()
            assert old["birth_month"] == "2025-09" and old["birth_date"] is None

            family_ = (await client.get("/me/family", headers=car)).json()
            assert {c["birth_date"] for c in family_["children"]} == {"2025-09-03", None}

    run(scenario)


def test_sync_family_revision_changes_with_birth_date_and_seat():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_birth_sync")
            car = await car_token(client, mom)
            url = f"/me/children/{uuid.uuid4()}"
            await mom.put(url, json={"nickname": "서아", "birth_date": "2025-09-03"})
            first = (await client.get("/me/sync", headers=car)).json()["family"]
            await mom.put(url, json={"nickname": "서아", "birth_date": "2025-09-04"})
            second = (await client.get("/me/sync", headers=car)).json()["family"]
            await mom.put(url, json={"nickname": "서아", "birth_date": "2025-09-04", "rear_facing_seat": True})
            third = (await client.get("/me/sync", headers=car)).json()["family"]
            assert len({first, second, third}) == 3

    run(scenario)


def test_care_places_merge_from_two_cars_and_are_private():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_care_places")
            await signup(other, "other_care_places")
            car_a = await car_token(client, mom)
            car_b = await car_token(client, mom)
            stranger = await car_token(client, other)

            assert (await client.get("/me/care-places", headers=car_a)).json() == {"places": []}
            center = {"place_key": "kakao:1", "place_name": "구리시 육아종합지원센터", "amenities": ["nursing_room"], "visit_count": 3, "last_visited_on": "2026-11-01"}
            cafe = {"place_name": "키즈카페 숲", "amenities": ["stroller", "stroller"], "visit_count": 2}
            res = await client.put("/me/care-places", headers=car_a, json={"places": [center, cafe]})
            assert res.status_code == 200, res.text
            assert [p["place_name"] for p in res.json()["places"]] == ["구리시 육아종합지원센터", "키즈카페 숲"]
            assert res.json()["places"][1]["amenities"] == ["stroller"]

            # 다른 차가 같은 곳을 덜 간 기록으로 올려도 줄지 않고, 시설·날짜는 합쳐진다
            b = {"place_name": "구리시 육아종합지원센터", "amenities": ["stroller"], "visit_count": 2, "last_visited_on": "2026-11-05"}
            b2 = {"place_name": "아차산 숲길 쉼터", "amenities": [], "visit_count": 5}
            merged = (await client.put("/me/care-places", headers=car_b, json={"places": [b, b2]})).json()["places"]
            assert [p["place_name"] for p in merged] == ["아차산 숲길 쉼터", "구리시 육아종합지원센터", "키즈카페 숲"]
            center_row = merged[1]
            assert center_row["visit_count"] == 3 and center_row["amenities"] == ["nursing_room", "stroller"]
            assert center_row["last_visited_on"] == "2026-11-05" and center_row["place_key"] == "kakao:1"

            # 폰 웹(쿠키)도 같은 목록, 다른 사람에게는 안 보인다
            assert len((await mom.get("/me/care-places")).json()["places"]) == 3
            assert (await client.get("/me/care-places", headers=stranger)).json() == {"places": []}

            bad = await client.put("/me/care-places", headers=car_a, json={"places": [{"place_name": "x", "visit_count": 1, "amenities": ["sauna"]}]})
            assert bad.status_code == 422
            too_many = [{"place_name": f"곳 {i}", "visit_count": 1} for i in range(21)]
            assert (await client.put("/me/care-places", headers=car_a, json={"places": too_many})).status_code == 422

            # 많이 올려도 계정 전체는 20곳까지 (적게 간 곳부터 빠진다)
            for start in (0, 20):
                await client.put("/me/care-places", headers=car_a, json={"places": [{"place_name": f"곳 {i}", "visit_count": 1} for i in range(start, start + 20)]})
            kept = (await client.get("/me/care-places", headers=car_a)).json()["places"]
            assert len(kept) == 20 and kept[0]["place_name"] == "아차산 숲길 쉼터"

            # 방문 기록 전체 삭제 → 케어 장소도 지워진다
            assert (await client.delete("/me/visits", headers=car_a)).status_code == 204
            assert (await client.get("/me/care-places", headers=car_a)).json() == {"places": []}
            assert (await client.delete("/me/care-places", headers=car_a)).status_code == 204

    run(scenario)


def test_web_child_form_takes_birth_date_to_the_day_and_rear_facing_seat():
    async def scenario(client):
        async with phone(client) as mom:
            await signup(mom, "mom_web_birth")
            page = (await mom.get("/m/start/child")).text
            assert 'name="birth_date" type="date"' in page and "required" in page

            res = await mom.post("/m/start/child", data={"sections": "basic", "nickname": "서아", "birth_date": "2025-09-03"})
            assert res.status_code == 303
            child = (await mom.get("/me/family")).json()["children"][0]
            assert child["birth_date"] == "2025-09-03" and child["birth_month"] == "2025-09"
            assert 'value="2025-09-03"' in (await mom.get("/m/start/child")).text

            future = (family.today_kst() + timedelta(days=1)).isoformat()
            bad = await mom.post("/m/start/child", data={"sections": "basic", "nickname": "서아", "birth_date": future})
            assert bad.status_code == 400 and "생년월일을 다시 확인" in bad.text

            # MY 편집: 카시트 뒤보기를 받고 다른 칸은 그대로
            cid = child["id"]
            edit = (await mom.get(f"/m/my/children/{cid}")).text
            assert 'name="rear_facing_seat"' in edit
            await mom.post(f"/m/my/children/{cid}", data={"nickname": "서아", "birth_date": "2025-09-03", "rear_facing_seat": "1"})
            child = (await mom.get("/me/family")).json()["children"][0]
            assert child["rear_facing_seat"] is True and child["birth_date"] == "2025-09-03"
            assert "2025.09.03" in (await mom.get("/m/my")).text

            # 달만 아는 예전 아이: 날짜 칸이 필수가 아니고, 달을 숨은 칸으로 다시 보내 그대로 둔다
            old_id = str(uuid.uuid4())
            await mom.put(f"/me/children/{old_id}", json={"nickname": "도윤", "birth_month": "2023-09"})
            old_page = (await mom.get(f"/m/my/children/{old_id}")).text
            assert 'name="birth_month" value="2023-09"' in old_page and "2023년 9월로만" in old_page
            await mom.post(f"/m/my/children/{old_id}", data={"nickname": "도윤", "birth_date": "", "birth_month": "2023-09"})
            kept = [c for c in (await mom.get("/me/family")).json()["children"] if c["id"] == old_id][0]
            assert kept["birth_month"] == "2023-09" and kept["birth_date"] is None

    run(scenario)

"""진행 중 코스 `/me/trip` (#218, Figma H03·H04 "바꾼 일정은 웹앱에도 바로 반영돼요"). pleos_test DB를 쓴다."""

from datetime import datetime

from app.auth.web_app import KST
from tests.test_auth import phone, run, signup
from tests.test_presets import car_token


def _trip(**over) -> dict:
    today = datetime.now(KST).date().isoformat()
    body = {
        "title": "덕수궁 나들이 코스",
        "started_on": today,
        "departure_at": "10:00",
        "status": "active",
        "ended_by": None,
        "stops": [
            {"name": "올데이 브런치", "role": "meal", "arrive_at": "10:40", "stay_minutes": 60, "state": "visited"},
            {"name": "덕수궁", "role": "outing", "arrive_at": "12:00", "stay_minutes": 60, "state": "current"},
            {"name": "벨지안 와플", "role": "cafe", "arrive_at": "13:20", "stay_minutes": 60, "state": "upcoming"},
            {"name": "우리 집", "role": "home", "arrive_at": "14:40", "stay_minutes": 0, "state": "upcoming"},
        ],
    }
    return body | over


def test_car_puts_trip_and_phone_home_shows_changes():
    async def scenario(client):
        async with phone(client) as mom, phone(client) as other:
            await signup(mom, "mom_trip")
            await signup(other, "other_trip")
            car = await car_token(client, mom)
            stranger = await car_token(client, other)

            assert (await client.get("/me/trip", headers=car)).status_code == 204
            assert "이번 주 외출 일정이 없어요" in (await mom.get("/m/")).text
            before = (await client.get("/me/sync", headers=car)).json()

            res = await client.put("/me/trip", headers=car, json=_trip())
            assert res.status_code == 200
            assert (await client.get("/me/trip", headers=car)).json()["stops"][1]["name"] == "덕수궁"
            after = (await client.get("/me/sync", headers=car)).json()
            assert after["trip"] != before["trip"] and after["all"] != before["all"]
            page = (await mom.get("/m/")).text
            assert "코스 진행 중 · 2/3 장소" in page and "덕수궁 나들이 코스" in page and "1곳 다녀왔어요" in page

            # 장소 바꾸기 → 폰에 바로 보인다
            changed = _trip()
            changed["stops"][1] = changed["stops"][1] | {"name": "서울시립미술관"}
            await client.put("/me/trip", headers=car, json=changed)
            page = (await mom.get("/m/")).text
            assert "서울시립미술관" in page and "덕수궁</" not in page

            # 여기서 코스 끝내기: 귀가 줄을 빼고 '안 감'을 표시한다
            ended = _trip(status="finished", ended_by="here")
            ended["stops"][1] = ended["stops"][1] | {"state": "visited"}
            ended["stops"][2] = ended["stops"][2] | {"state": "skipped"}
            await client.put("/me/trip", headers=car, json=ended)
            page = (await mom.get("/m/")).text
            assert "코스를 마쳤어요" in page and "안 감" in page and "귀가</" not in page and "2곳 다녀왔어요" in page

            # 연료 경유지(#215)는 장소 수에 넣지 않는다
            fueled = _trip()
            fueled["stops"].insert(1, {"name": "판교주유소", "role": "fuel", "arrive_at": "11:50", "stay_minutes": 10, "state": "upcoming"})
            assert (await client.put("/me/trip", headers=car, json=fueled)).status_code == 200
            assert "코스 진행 중 · 2/3 장소" in (await mom.get("/m/")).text

            # 다른 계정은 못 본다 · 지난 날짜 코스는 홈에 안 보인다
            assert (await client.get("/me/trip", headers=stranger)).status_code == 204
            await client.put("/me/trip", headers=car, json=_trip(started_on="2020-01-01"))
            assert "이번 주 외출 일정이 없어요" in (await mom.get("/m/")).text

            # 좌표 등 모르는 값·잘못된 시각은 받지 않는다
            bad = _trip()
            bad["stops"][0] = bad["stops"][0] | {"arrive_at": "25:00"}
            assert (await client.put("/me/trip", headers=car, json=bad)).status_code == 422
            assert (await client.put("/me/trip", headers=car, json=_trip(stops=[]))).status_code == 422

            # 방문 기록 전체 삭제와 함께 지운다
            await client.put("/me/trip", headers=car, json=_trip())
            assert (await client.delete("/me/visits", headers=car)).status_code == 204
            assert (await client.get("/me/trip", headers=car)).status_code == 204

    run(scenario)

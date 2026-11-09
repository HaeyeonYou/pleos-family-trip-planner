"""회원가입·로그인·차 QR 로그인. 테스트용 PostgreSQL DB(pleos_test)를 쓴다.

여러 사용자가 동시에 가입·로그인해도 세션이 섞이지 않는지 asyncio.gather로 확인한다.
"""

import asyncio
import os
import re

import httpx
import pytest

from app.config import get_settings
from app.db import Base, Database
from app.main import app

TEST_DB = os.environ.get("TEST_DATABASE_URL", "postgresql+asyncpg://localhost/pleos_test")


def run(scenario):
    """테스트마다 새 이벤트 루프에서 빈 스키마를 만들고 시나리오를 돌린다."""

    async def main():
        db = Database(get_settings(), url=TEST_DB)
        async with db.engine.begin() as conn:
            await conn.run_sync(Base.metadata.drop_all)
            await conn.run_sync(Base.metadata.create_all)
        app.state.db = db
        try:
            transport = httpx.ASGITransport(app=app)
            async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                await scenario(client)
        finally:
            await db.close()

    asyncio.run(main())


async def signup(client: httpx.AsyncClient, login_id: str, password: str = "password123") -> httpx.Response:
    return await client.post(
        "/m/signup", data={"agree": "1", "login_id": login_id, "password": password, "display_name": login_id, "next": "/m/"}
    )


def phone(client: httpx.AsyncClient) -> httpx.AsyncClient:
    """쿠키를 따로 갖는 폰 브라우저 하나."""
    return httpx.AsyncClient(transport=client._transport, base_url="http://test")


def test_signup_validates_and_rejects_duplicates():
    async def scenario(client):
        assert (await signup(client, "ab")).status_code == 400  # 아이디 너무 짧음
        assert (await signup(client, "mom_01", "short")).status_code == 400  # 비밀번호 짧음
        ok = await signup(client, "Mom_01")
        assert ok.status_code == 303 and "sid" in ok.cookies
        dup = await signup(client, "mom_01")
        assert dup.status_code == 400 and "이미 쓰고 있는 아이디" in dup.text

    run(scenario)


def test_wrong_password_is_rejected():
    async def scenario(client):
        await signup(client, "dad_01")
        res = await client.post("/m/login", data={"login_id": "dad_01", "password": "wrongpass1", "next": "/m/"})
        assert res.status_code == 400 and "맞지 않아요" in res.text

    run(scenario)


def test_car_qr_login_flow():
    async def scenario(client):
        start = (await client.post("/auth/car-login", json={"device_name": "아이오닉"})).json()
        assert re.match(r"^[A-Z2-9]{10}$", start["code"]) and start["login_url"].endswith(f"/m/car?code={start['code']}")
        qr = await client.get(f"/auth/car-login/{start['code']}/qr.png")
        assert qr.status_code == 200 and qr.headers["content-type"] == "image/png"

        poll = lambda: client.post(f"/auth/car-login/{start['request_id']}/poll", json={"poll_secret": start["poll_secret"]})  # noqa: E731
        assert (await poll()).json()["status"] == "pending"

        async with phone(client) as mobile:
            # 로그인 전이면 로그인 화면으로, 가입 후 승인 화면으로 돌아온다.
            first = await mobile.get(f"/m/car?code={start['code']}")
            assert first.status_code == 303 and first.headers["location"] == f"/m/start?code={start['code']}"
            joined = await mobile.post(
                "/m/signup",
                data={"agree": "1", "login_id": "mom_02", "password": "password123", "next": f"/m/car?code={start['code']}"},
            )
            assert joined.headers["location"] == f"/m/car?code={start['code']}"
            page = await mobile.get(joined.headers["location"])
            assert "이 차에서 로그인" in page.text and "아이오닉" in page.text
            assert (await mobile.post("/m/car", data={"code": start["code"]})).status_code == 200

        approved = (await poll()).json()
        assert approved["status"] == "approved" and approved["user"]["login_id"] == "mom_02"
        car = {"Authorization": f"Bearer {approved['token']}"}
        assert (await client.get("/auth/me", headers=car)).json()["login_id"] == "mom_02"
        # 같은 요청으로 토큰을 두 번 받을 수 없다.
        assert (await poll()).json() == {"status": "consumed", "token": None, "user": None}
        # 잘못된 폴링 비밀값은 거절
        bad = await client.post(f"/auth/car-login/{start['request_id']}/poll", json={"poll_secret": "x"})
        assert bad.status_code == 404

        # 로그아웃하면 그 토큰은 더 못 쓴다.
        assert (await client.post("/auth/logout", headers=car)).status_code == 204
        assert (await client.get("/auth/me", headers=car)).status_code == 401

    run(scenario)


def test_many_users_log_in_cars_at_the_same_time():
    """20가족이 동시에 가입하고 각자 차에 로그인해도 차 세션이 제 주인에게만 붙는다."""

    async def family(client, i):
        start = (await client.post("/auth/car-login", json={"device_name": f"car{i}"})).json()
        async with phone(client) as mobile:
            await mobile.post("/m/signup", data={"agree": "1", "login_id": f"family_{i:02d}", "password": "password123", "next": "/m/"})
            await mobile.post("/m/car", data={"code": start["code"]})
        result = (
            await client.post(f"/auth/car-login/{start['request_id']}/poll", json={"poll_secret": start["poll_secret"]})
        ).json()
        me = await client.get("/auth/me", headers={"Authorization": f"Bearer {result['token']}"})
        return i, me.json()["login_id"]

    async def scenario(client):
        results = await asyncio.gather(*(family(client, i) for i in range(20)))
        assert all(login_id == f"family_{i:02d}" for i, login_id in results)

    run(scenario)


def test_same_id_signed_up_at_once_creates_one_account():
    async def scenario(client):
        async def attempt():
            async with phone(client) as mobile:
                return (await signup(mobile, "same_id")).status_code

        codes = await asyncio.gather(*(attempt() for _ in range(8)))
        assert codes.count(303) == 1 and codes.count(400) == 7

    run(scenario)

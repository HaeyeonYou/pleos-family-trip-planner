"""앱 크래시·오류 원격 수집(#261)과 서버 파일 로그. pleos_test DB를 쓴다."""

import json
import logging
import uuid

from app import applogs
from app.logs import setup_logging
from tests.test_auth import phone, run, signup
from tests.test_web_mobile import connect_car


def batch(install_id: str, **event) -> dict:
    return {
        "install_id": install_id,
        "app_version": "0.1.0 (1)",
        "device": "Google sdk_gcar_x86_64",
        "os": "Android 14",
        "events": [{"ts": "2026-11-12T23:00:00+09:00", "level": "crash", "tag": "MainActivity",
                    "message": "IllegalStateException: 테스트", "stack": "at a.b(C.kt:1)", **event}],
    }


def read_lines(folder) -> list[dict]:
    files = list(folder.glob("app-*.jsonl"))
    assert len(files) == 1
    return [json.loads(line) for line in files[0].read_text(encoding="utf-8").splitlines()]


def test_app_crash_is_saved_as_jsonl_with_user_only_when_logged_in(tmp_path, monkeypatch):
    monkeypatch.setenv("LOG_DIR", str(tmp_path))

    async def scenario(client):
        anonymous = await client.post("/logs/app", json=batch(uuid.uuid4().hex))
        assert anonymous.status_code == 204

        mobile = phone(client)
        await signup(mobile, "logs" + uuid.uuid4().hex[:6])
        headers = await connect_car(client, mobile)
        logged_in = await client.post("/logs/app", json=batch(uuid.uuid4().hex, level="warn", count=7), headers=headers)
        assert logged_in.status_code == 204

        # 만료·잘못된 토큰이어도 받는다 (크래시는 로그인과 상관없이 모은다)
        bad = await client.post("/logs/app", json=batch(uuid.uuid4().hex), headers={"Authorization": "Bearer nope"})
        assert bad.status_code == 204

    run(scenario)
    first, second, third = read_lines(tmp_path)
    assert first["user_id"] is None and first["level"] == "crash" and first["stack"] == "at a.b(C.kt:1)"
    assert second["user_id"] and second["count"] == 7
    assert third["user_id"] is None
    # IP 같은 개인 정보는 남기지 않는다
    assert set(first) == {"received_at", "install_id", "user_id", "app_version", "device", "os",
                          "ts", "level", "tag", "message", "stack", "count"}


def test_app_logs_reject_oversized_and_flooding_batches(tmp_path, monkeypatch):
    monkeypatch.setenv("LOG_DIR", str(tmp_path))
    install = uuid.uuid4().hex

    async def scenario(client):
        too_long = batch(install, message="x" * 2001)
        assert (await client.post("/logs/app", json=too_long)).status_code == 422
        too_many = batch(install)
        too_many["events"] = too_many["events"] * 51
        assert (await client.post("/logs/app", json=too_many)).status_code == 422
        codes = [(await client.post("/logs/app", json=batch(install))).status_code for _ in range(applogs.PER_MINUTE + 1)]
        assert codes[:-1] == [204] * applogs.PER_MINUTE and codes[-1] == 429

    run(scenario)


def test_old_app_log_files_are_removed(tmp_path, monkeypatch):
    monkeypatch.setenv("LOG_DIR", str(tmp_path))
    monkeypatch.setenv("LOG_KEEP_DAYS", "30")
    monkeypatch.setattr(applogs, "_cleaned_on", None)
    old = tmp_path / "app-2020-01-01.jsonl"
    old.write_text("{}\n")
    applogs.write_events(applogs.AppLogBatch(**batch(uuid.uuid4().hex)), None)
    assert not old.exists()
    assert len(list(tmp_path.glob("app-*.jsonl"))) == 1


def test_server_log_is_also_written_to_file_when_log_dir_is_set(tmp_path, monkeypatch):
    monkeypatch.setenv("LOG_DIR", str(tmp_path))
    try:
        setup_logging()
        logging.getLogger("app").info("파일 로그 확인")
        for handler in logging.getLogger().handlers:
            handler.flush()
        text = (tmp_path / "server.log").read_text(encoding="utf-8")
        assert "파일 로그 확인" in text and text[:4].isdigit()  # 날짜가 붙는다
    finally:
        monkeypatch.delenv("LOG_DIR")
        setup_logging()

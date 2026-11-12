"""서버 로그 설정과 외부 API 호출 기록.

- 요청마다: 메서드·경로·상태·걸린 시간 (main.py 미들웨어)
- 코스: 받은 조건 요약, 후보 수와 상위 점수, 고른 장소와 귀가 시각
- 외부 API: 대상·경로·상태·걸린 시간, 저장본(메모리 캐시) 재사용 여부
API 키는 쿼리(serviceKey)·헤더(Authorization)로만 오가므로 경로만 남겨 기록되지 않는다.
"""

import logging
import logging.handlers
import os
import time
from pathlib import Path

import httpx

FORMAT = "%(asctime)s %(levelname)-5s %(name)-12s | %(message)s"


def log_dir() -> Path | None:
    """LOG_DIR이 있으면 로그 파일을 그 폴더에 남긴다 (NAS: /volume1/docker/pleos/logs)."""
    value = os.environ.get("LOG_DIR", "").strip()
    return Path(value) if value else None


def keep_days() -> int:
    """로그 파일 보관 일수 (LOG_KEEP_DAYS, 기본 30일)."""
    try:
        return max(1, int(os.environ.get("LOG_KEEP_DAYS", "30")))
    except ValueError:
        return 30


def setup_logging() -> None:
    """LOG_LEVEL 환경 변수로 수준을 바꾼다 (기본 INFO, 자세히 보려면 DEBUG).

    LOG_DIR이 있으면 화면(Docker 로그)과 함께 server.log에도 쓴다. 자정마다 server.log.YYYY-MM-DD로 넘기고 보관 일수가 지나면 지운다.
    """
    level = os.environ.get("LOG_LEVEL", "INFO").upper()
    handlers: list[logging.Handler] = [logging.StreamHandler()]
    folder = log_dir()
    if folder is not None:
        folder.mkdir(parents=True, exist_ok=True)
        file_handler = logging.handlers.TimedRotatingFileHandler(
            folder / "server.log", when="midnight", backupCount=keep_days(), encoding="utf-8"
        )
        file_handler.setFormatter(logging.Formatter(FORMAT, datefmt="%Y-%m-%d %H:%M:%S"))
        handlers.append(file_handler)
    # 형식이 없는 화면 핸들러에만 시:분:초 형식이 붙고, 파일은 위에서 정한 날짜 포함 형식을 유지한다
    logging.basicConfig(level=level, format=FORMAT, datefmt="%H:%M:%S", handlers=handlers, force=True)
    # 외부 API는 아래 훅으로 따로 남기므로 httpx 자체 로그는 줄인다.
    logging.getLogger("httpx").setLevel(logging.WARNING)


def api_log_hooks(service: str) -> dict:
    """httpx.Client(event_hooks=...)에 넣어 외부 API 호출을 한 줄씩 남긴다."""
    log = logging.getLogger(f"api.{service}")

    def on_request(request: httpx.Request) -> None:
        request.extensions["started"] = time.perf_counter()

    def on_response(response: httpx.Response) -> None:
        started = response.request.extensions.get("started")
        ms = (time.perf_counter() - started) * 1000 if started else -1
        level = logging.INFO if response.status_code < 400 else logging.WARNING
        log.log(level, "%s %s → %s (%.0fms)", response.request.method, response.request.url.path, response.status_code, ms)

    return {"request": [on_request], "response": [on_response]}

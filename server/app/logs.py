"""서버 로그 설정과 외부 API 호출 기록.

- 요청마다: 메서드·경로·상태·걸린 시간 (main.py 미들웨어)
- 코스: 받은 조건 요약, 후보 수와 상위 점수, 고른 장소와 귀가 시각
- 외부 API: 대상·경로·상태·걸린 시간, 저장본(메모리 캐시) 재사용 여부
API 키는 쿼리(serviceKey)·헤더(Authorization)로만 오가므로 경로만 남겨 기록되지 않는다.
"""

import logging
import os
import time

import httpx

FORMAT = "%(asctime)s %(levelname)-5s %(name)-12s | %(message)s"


def setup_logging() -> None:
    """LOG_LEVEL 환경 변수로 수준을 바꾼다 (기본 INFO, 자세히 보려면 DEBUG)."""
    level = os.environ.get("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(level=level, format=FORMAT, datefmt="%H:%M:%S", force=True)
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

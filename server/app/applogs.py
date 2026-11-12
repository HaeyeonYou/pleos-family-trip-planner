"""앱 크래시·오류 원격 수집 (#261).

차 앱이 기기에 모아 둔 크래시·경고를 묶어 보내면, LOG_DIR의 app-YYYY-MM-DD.jsonl에 한 줄씩 남긴다.
- 개인정보는 받지 않는다: 위치·이름·입력 문장 없음, 클라이언트 IP도 저장하지 않는다.
- 로그인 토큰이 있으면 user_id만 붙인다 (없거나 만료여도 받는다. 크래시는 로그인 전에도 난다).
- 공개 주소라 크기·개수·빈도를 제한한다.
"""

import json
import logging
import threading
import time
from datetime import datetime, timedelta, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import service
from app.auth.deps import bearer_token, get_db
from app.logs import keep_days, log_dir

router = APIRouter(tags=["logs"])
log = logging.getLogger("app.client")

KST = timezone(timedelta(hours=9))
MAX_EVENTS = 50
# 설치 하나가 1분에 보낼 수 있는 묶음 수
PER_MINUTE = 12


class AppLogEvent(BaseModel):
    ts: str = Field(max_length=40, description="기기 시각 (ISO 8601)")
    level: Literal["crash", "error", "warn", "info"]
    tag: str = Field(max_length=60)
    message: str = Field(max_length=2000)
    stack: str | None = Field(default=None, max_length=16000)
    count: int = Field(default=1, ge=1, le=100000, description="기기에서 묶은 같은 오류 수")


class AppLogBatch(BaseModel):
    install_id: str = Field(min_length=8, max_length=64, description="앱 설치마다 무작위로 만든 id")
    app_version: str = Field(max_length=40)
    device: str = Field(max_length=100)
    os: str = Field(max_length=40)
    events: list[AppLogEvent] = Field(min_length=1, max_length=MAX_EVENTS)


class _Limiter:
    """설치 id별 1분 창 요청 수. 프로세스 하나 기준이면 충분하다."""

    def __init__(self) -> None:
        self._hits: dict[str, list[float]] = {}
        self._lock = threading.Lock()

    def allow(self, key: str, now: float | None = None) -> bool:
        now = time.monotonic() if now is None else now
        with self._lock:
            hits = [t for t in self._hits.get(key, []) if now - t < 60]
            if len(hits) >= PER_MINUTE:
                self._hits[key] = hits
                return False
            hits.append(now)
            self._hits[key] = hits
            if len(self._hits) > 10000:  # 오래된 키 정리
                self._hits = {k: v for k, v in self._hits.items() if v and now - v[-1] < 60}
            return True


limiter = _Limiter()
_cleaned_on: str | None = None


def _remove_old_files(today: str) -> None:
    """하루 한 번, 보관 일수가 지난 app-*.jsonl을 지운다."""
    global _cleaned_on
    folder = log_dir()
    if folder is None or _cleaned_on == today:
        return
    _cleaned_on = today
    cutoff = (datetime.now(KST) - timedelta(days=keep_days())).strftime("%Y-%m-%d")
    for path in folder.glob("app-*.jsonl"):
        day = path.stem.removeprefix("app-")
        if day < cutoff:
            path.unlink(missing_ok=True)


def write_events(batch: AppLogBatch, user_id: str | None) -> None:
    received = datetime.now(KST)
    today = received.strftime("%Y-%m-%d")
    lines = []
    for event in batch.events:
        record = {
            "received_at": received.isoformat(timespec="seconds"),
            "install_id": batch.install_id,
            "user_id": user_id,
            "app_version": batch.app_version,
            "device": batch.device,
            "os": batch.os,
            **event.model_dump(),
        }
        lines.append(json.dumps(record, ensure_ascii=False))
        # 서버 로그에도 한 줄 요약 (스택은 파일에만)
        level = logging.ERROR if event.level in ("crash", "error") else logging.INFO
        log.log(level, "%s %s/%s ×%d %s", event.level, batch.app_version, event.tag, event.count, event.message[:200])
    folder = log_dir()
    if folder is None:
        return
    folder.mkdir(parents=True, exist_ok=True)
    with open(folder / f"app-{today}.jsonl", "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    _remove_old_files(today)


@router.post("/logs/app", status_code=204)
async def receive_app_logs(batch: AppLogBatch, request: Request, db: AsyncSession = Depends(get_db)) -> Response:
    if not limiter.allow(batch.install_id):
        raise HTTPException(429, "잠시 뒤 다시 보내 주세요")
    token = bearer_token(request)
    user = await service.user_for_token(db, token) if token else None
    write_events(batch, str(user.id) if user else None)
    return Response(status_code=204)

"""Anthropic(Claude) 호출을 한곳에 모은다. 문장 해석, 음성 명령 분류 등 모든 LLM 호출이 이 모듈을 쓴다.

- 모델은 가장 작은 Haiku. 키는 서버 .env의 ANTHROPIC_API_KEY (앱에는 넣지 않는다).
- 응답은 Pydantic 모델(JSON 스키마)로 받아 검증한다.
- 3초를 넘기거나 실패하면 None을 돌려준다. 호출하는 쪽이 규칙 기반으로 되돌아간다.
- 로그에는 문장 원문을 남기지 않고 길이·걸린 시간·토큰 수만 남긴다.
"""

import logging
import time
from typing import TypeVar

import anthropic
import httpx
from pydantic import BaseModel

log = logging.getLogger("llm")

DEFAULT_TIMEOUT_S = 3.0

T = TypeVar("T", bound=BaseModel)


class LlmClient:
    def __init__(
        self,
        api_key: str,
        model: str,
        timeout_s: float = DEFAULT_TIMEOUT_S,
        http_client=None,
    ):
        # 재시도 없이 3초 안에 못 받으면 포기한다 (코스 요청을 막지 않는다)
        self._client = anthropic.Anthropic(api_key=api_key, timeout=timeout_s, max_retries=0, http_client=http_client)
        self._model = model

    def structured(self, task: str, system: str, text: str, output: type[T], max_tokens: int = 2000) -> T | None:
        """text를 system 지침대로 해석해 output 스키마로 돌려준다. 실패·시간 초과·거절이면 None.

        task는 로그에 남길 작업 이름이다 (예: "parse").
        """
        started = time.perf_counter()
        try:
            response = self._client.messages.parse(
                model=self._model,
                max_tokens=max_tokens,
                system=system,
                messages=[{"role": "user", "content": text}],
                output_format=output,
            )
        except (anthropic.APIError, httpx.HTTPError) as e:
            log.warning(
                "%s 호출 실패(%s, %d자, %.0fms) → 규칙 기반으로 대체",
                task, type(e).__name__, len(text), (time.perf_counter() - started) * 1000,
            )
            return None
        except Exception as e:  # noqa: BLE001 - 응답 검증 실패 등 어떤 오류여도 앱은 동작해야 한다
            log.warning("%s 결과 사용 불가(%s, %d자) → 규칙 기반으로 대체", task, type(e).__name__, len(text))
            return None

        if response.stop_reason == "refusal" or response.parsed_output is None:
            log.warning("%s 응답 사용 불가(stop_reason=%s) → 규칙 기반으로 대체", task, response.stop_reason)
            return None
        log.info(
            "%s %d자 → %.0fms (토큰 in=%s out=%s)",
            task, len(text), (time.perf_counter() - started) * 1000,
            response.usage.input_tokens, response.usage.output_tokens,
        )
        return response.parsed_output

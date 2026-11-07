import logging
import time

import anthropic
import httpx

from app import interests
from app.parsing.rule_based import RuleBasedParser
from app.schemas import TripConditions

log = logging.getLogger(__name__)

_SYSTEM = """\
너는 가족 나들이 앱의 요청 해석기다. 사용자의 한국어 요청에서 코스 조건만 추출한다.
- 요청에 없는 값은 추측하지 말고 비워 둔다(null 또는 빈 배열).
- child_ages는 만 나이 정수. 개월 수로 말한 영아는 0.
- themes는 nature(자연), experience(체험), exhibition(전시), play(놀이), pet(반려동물 동반) 중에서만 고른다.
- indoor_only는 비·추위·더위·미세먼지 등으로 실내를 원할 때만 true.
- return_by_hour는 귀가 희망 시각(0~23). "6시 전"처럼 오전/오후가 없으면 오후로 본다.
- needs는 stroller(유아차), nursing_room(수유실), kids_chair(유아 의자), kids_zone(키즈존) 중에서만 고른다.
- interests는 아이가 좋아하거나 관심 있는 대상을 한 단어 명사로 적는다(예: 공룡, 기차, 동물, 우주, 물놀이, 곤충).
  가능하면 이 목록의 말을 쓴다: {vocabulary}
"""


class ClaudeParser:
    name = "claude"

    def __init__(self, api_key: str, model: str, http_client: httpx.Client | None = None):
        self._client = anthropic.Anthropic(api_key=api_key, timeout=15.0, max_retries=1, http_client=http_client)
        self._model = model
        self._fallback = RuleBasedParser()

    def parse(self, text: str) -> TripConditions:
        started = time.perf_counter()
        try:
            response = self._client.messages.parse(
                model=self._model,
                max_tokens=2000,
                system=_SYSTEM.format(vocabulary=", ".join(interests.INTEREST_TERMS)),
                messages=[{"role": "user", "content": text}],
                output_format=TripConditions,
            )
        except anthropic.APIError as e:
            # 네트워크 장애나 크레딧 소진이어도 앱은 동작해야 하므로 규칙 기반으로 대체
            log.warning("Claude 호출 실패, 규칙 기반으로 대체: %s", e)
            return self._fallback.parse(text)

        if response.stop_reason == "refusal" or response.parsed_output is None:
            log.warning("Claude 응답 사용 불가(stop_reason=%s), 규칙 기반으로 대체", response.stop_reason)
            return self._fallback.parse(text)
        log.info(
            "Claude 해석 %.0fms (토큰 in=%s out=%s)",
            (time.perf_counter() - started) * 1000,
            response.usage.input_tokens,
            response.usage.output_tokens,
        )
        result = response.parsed_output
        # 사전 단어로 맞춰 코스 생성의 관련어 검색이 동작하게 한다. 사전에 없으면 그대로 둔다.
        result.interests = list(dict.fromkeys(interests.normalize(w) or w for w in result.interests))
        return result

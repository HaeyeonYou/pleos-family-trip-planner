"""Claude 호출 모듈: 느리거나 실패하면 규칙 기반으로 되돌아가는 대체 경로를 정식 경로로 검증한다."""

import httpx2
import pytest
from pydantic import BaseModel

from app.config import Settings
from app.llm import LlmClient
from app.parsing import get_parser
from app.parsing.claude import ClaudeParser
from app.parsing.rule_based import RuleBasedParser

TEXT = "공룡 좋아하는 5살, 수유실 있는 곳, 6시 전 귀가"


def client(handler) -> httpx2.Client:
    return httpx2.Client(transport=httpx2.MockTransport(handler))


def message(content: str, stop_reason: str = "end_turn"):
    return httpx2.Response(
        200,
        json={
            "id": "msg_1", "type": "message", "role": "assistant", "model": "claude-haiku-5-5",
            "content": [{"type": "text", "text": content}], "stop_reason": stop_reason,
            "usage": {"input_tokens": 10, "output_tokens": 10},
        },
    )


def parser(handler) -> ClaudeParser:
    return ClaudeParser("test-key", "claude-haiku-5-5", http_client=client(handler))


def assert_rule_result(p: ClaudeParser):
    c = p.parse(TEXT)
    assert p.last_used == "rule"
    assert c.child_ages == [5] and c.return_by_hour == 18 and "공룡" in c.interests


def test_timeout_falls_back_to_rule():
    def slow(request):
        raise httpx2.ReadTimeout("3초 초과", request=request)

    assert_rule_result(parser(slow))


def test_server_error_falls_back_to_rule():
    assert_rule_result(parser(lambda r: httpx2.Response(529, json={"type": "error", "error": {"type": "overloaded_error", "message": "x"}})))


def test_auth_error_falls_back_to_rule():
    assert_rule_result(parser(lambda r: httpx2.Response(401, json={"type": "error", "error": {"type": "authentication_error", "message": "x"}})))


def test_invalid_json_falls_back_to_rule():
    assert_rule_result(parser(lambda r: message("이건 JSON이 아니에요")))


def test_schema_violation_falls_back_to_rule():
    assert_rule_result(parser(lambda r: message('{"return_by_hour": 99}')))


def test_refusal_falls_back_to_rule():
    assert_rule_result(parser(lambda r: message("{}", stop_reason="refusal")))


def test_valid_response_is_used_and_interests_normalized():
    p = parser(lambda r: message('{"child_ages": [5], "interests": ["티라노", "로봇"], "indoor_only": true}'))
    c = p.parse(TEXT)
    assert p.last_used == "claude"
    assert c.child_ages == [5] and c.indoor_only
    assert c.interests == ["공룡", "로봇"]  # 별칭은 사전 단어로, 사전에 없는 말은 그대로


def test_llm_client_uses_three_second_timeout_without_retry():
    llm = LlmClient("test-key", "claude-haiku-5-5")
    assert llm._client.timeout == 3.0 and llm._client.max_retries == 0


def test_llm_logs_length_not_text(caplog):
    class Out(BaseModel):
        ok: bool = True

    secret = "아주 개인적인 문장 원문"
    llm = LlmClient("test-key", "claude-haiku-5-5", http_client=client(lambda r: message("{}")))
    with caplog.at_level("INFO", logger="llm"):
        assert llm.structured("테스트", "지침", secret, Out) == Out()
    assert secret not in caplog.text and f"{len(secret)}자" in caplog.text


def test_get_parser_modes():
    assert isinstance(get_parser(Settings(_env_file=None, parser="auto", anthropic_api_key="")), RuleBasedParser)
    assert get_parser(Settings(_env_file=None, parser="auto", anthropic_api_key="k")).name == "claude"
    assert isinstance(get_parser(Settings(_env_file=None, parser="rule", anthropic_api_key="k")), RuleBasedParser)
    with pytest.raises(RuntimeError):
        get_parser(Settings(_env_file=None, parser="claude", anthropic_api_key=""))

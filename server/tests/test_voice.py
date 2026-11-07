"""음성 해석: 실제 Haiku 응답(저장본)으로 대표 문장을, 가짜 응답으로 검증·대체 경로를 확인한다."""

import json

import httpx2
import pytest
from fastapi.testclient import TestClient

from app.llm import LlmClient
from app.main import app
from app.voice import (
    Action,
    InterpretRequest,
    Interpretation,
    Screen,
    VoiceInterpreter,
    get_interpreter,
    normalize,
)
from tests.api_cache import caching_transport
from tests.test_real_api_data import key, settings

real_http = httpx2.Client(transport=caching_transport(httpx2), timeout=30)


def interpreter(http) -> VoiceInterpreter:
    return VoiceInterpreter(LlmClient(key("anthropic_api_key"), settings.claude_model, http_client=http))


def ask(text: str, **screen) -> "VoiceResult":  # noqa: F821
    return interpreter(real_http).interpret(InterpretRequest(text=text, screen=Screen(**screen)))


def message(payload: dict):
    return httpx2.Response(
        200,
        json={
            "id": "msg_1", "type": "message", "role": "assistant", "model": "claude-haiku-5-5",
            "content": [{"type": "text", "text": json.dumps(payload)}], "stop_reason": "end_turn",
            "usage": {"input_tokens": 10, "output_tokens": 10},
        },
    )


def fake(handler) -> VoiceInterpreter:
    return VoiceInterpreter(LlmClient("test-key", "claude-haiku-5-5", http_client=httpx2.Client(transport=httpx2.MockTransport(handler))))


# 실제 Haiku 응답 (저장본 재생)

def test_real_too_hot_lowers_temperature():
    r = ask("너무 더워")
    assert r.source == "llm" and r.action == Action.CLIMATE
    assert r.temperature_delta is not None and r.temperature_delta < 0 or r.temperature is not None
    assert r.reply


def test_real_diaper_place_question():
    r = ask("기저귀 갈 데 어디 있어?")
    assert r.action in (Action.FIND_PLACE, Action.CARE_SITUATION)
    assert r.target == "diaper"


def test_real_go_home():
    r = ask("홈으로 가줘", tab="care")
    assert r.action == Action.GO_TAB and r.target == "home"


def test_real_fed_just_now():
    r = ask("방금 수유했어")
    assert r.action == Action.FEED_LOG


def test_real_remove_pet_filter():
    r = ask("반려견 빼고 다시 추천해줘", has_home_options=True)
    assert r.action == Action.SET_FILTER and r.target == "pet" and r.on is False


def test_real_eta_question():
    r = ask("언제 도착해?", guiding=True, has_course=True)
    assert r.action == Action.QUERY and r.target == "eta"


def test_real_chitchat_is_unknown():
    r = ask("오늘 저녁 뭐 먹지 사업 아이디어 좀 말해줘")
    assert r.action == Action.UNKNOWN


# 검증·대체 경로 (가짜 응답)

def test_timeout_gives_unknown_fallback():
    def slow(request):
        raise httpx2.ReadTimeout("3초 초과", request=request)

    r = fake(slow).interpret(InterpretRequest(text="너무 더워"))
    assert r.action == Action.UNKNOWN and r.source == "fallback"


def test_server_error_gives_unknown_fallback():
    r = fake(lambda req: httpx2.Response(529, json={"type": "error", "error": {"type": "overloaded_error", "message": "x"}})).interpret(
        InterpretRequest(text="너무 더워")
    )
    assert r.action == Action.UNKNOWN and r.source == "fallback"


def test_action_outside_catalog_is_rejected_as_unknown():
    r = fake(lambda req: message({"action": "launch_missiles", "confidence": 0.99, "reply": "x"})).interpret(InterpretRequest(text="x"))
    assert r.action == Action.UNKNOWN and r.source == "fallback"


def test_low_confidence_is_unknown():
    r = fake(lambda req: message({"action": "sleep_mode", "on": True, "confidence": 0.3, "reply": "x"})).interpret(InterpretRequest(text="x"))
    assert r.action == Action.UNKNOWN


def test_missing_required_argument_is_unknown():
    assert normalize(Interpretation(action="go_tab", confidence=0.9)) is None
    assert normalize(Interpretation(action="set_filter", target="pet", confidence=0.9)) is None  # on 없음
    assert normalize(Interpretation(action="climate", confidence=0.9)) is None


def test_climate_values_are_clamped_to_safe_range():
    r = normalize(Interpretation(action="climate", temperature=45, temperature_delta=-20, number=99, driver_seat_heat=9, confidence=0.9))
    assert r.temperature == 30 and r.temperature_delta == -6 and r.number == 8 and r.driver_seat_heat == 3


def test_feed_interval_snaps_to_allowed_choices():
    r = normalize(Interpretation(action="feed_interval", number=200, confidence=0.9))
    assert r.number == 180


def test_choose_number_range():
    assert normalize(Interpretation(action="choose", number=7, confidence=0.9)) is None
    assert normalize(Interpretation(action="choose", number=2, confidence=0.9)).number == 2


def test_endpoint_validates_request_and_returns_result():
    handler = lambda req: message({"action": "sleep_mode", "on": True, "confidence": 0.9, "reply": "수면 모드를 켤게요."})  # noqa: E731
    app.dependency_overrides[get_interpreter] = lambda: fake(handler)
    try:
        with TestClient(app) as c:
            ok = c.post("/voice/interpret", json={"text": "애 재워야 해", "screen": {"tab": "care"}})
            assert ok.status_code == 200 and ok.json()["action"] == "sleep_mode" and ok.json()["on"] is True
            assert c.post("/voice/interpret", json={"text": ""}).status_code == 422
            assert c.post("/voice/interpret", json={"text": "x" * 500}).status_code == 422
    finally:
        app.dependency_overrides.pop(get_interpreter, None)


def test_log_does_not_contain_the_sentence(caplog):
    secret = "우리집비밀문장"
    handler = lambda req: message({"action": "unknown", "confidence": 0.9, "reply": ""})  # noqa: E731
    with caplog.at_level("INFO"):
        fake(handler).interpret(InterpretRequest(text=secret))
    assert secret not in caplog.text

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


# 그로우 음성 명령 (#185)

GROW_PLACES = ["서울숲", "국립중앙박물관 어린이박물관", "키즈카페 점프킹", "롯데월드 아쿠아리움", "별마당 도서관"]


def test_real_grow_last_month():
    r = ask("지난달 간 곳 보여줘", tab="grow", places=GROW_PLACES)
    assert r.source == "llm" and r.action == Action.GROW_SHOW
    assert r.period == "last_month" and r.target is None


def test_real_grow_kind_by_character():
    r = ask("공룡 별 보여줘", tab="grow", places=GROW_PLACES)
    assert r.action == Action.GROW_SHOW and r.target == "science"


def test_real_grow_this_month_question():
    r = ask("이번 달에 어디 갔었지?", tab="grow", places=GROW_PLACES)
    assert r.action == Action.GROW_SHOW and r.period == "this_month"


def test_real_grow_revisit():
    r = ask("다시 가볼 만한 곳 알려줘", tab="grow", places=GROW_PLACES)
    assert r.action == Action.GROW_REVISIT


def test_real_grow_go_place():
    r = ask("서울숲 다시 가자", tab="grow", places=GROW_PLACES)
    assert r.action == Action.GROW_GO and r.place == "서울숲"


def test_real_grow_go_unknown_place_is_not_invented():
    r = ask("에버랜드 다시 가자", tab="grow", places=GROW_PLACES)
    assert r.action != Action.GROW_GO


def test_grow_show_validation():
    assert normalize(Interpretation(action="grow_show", target="dragon", confidence=0.9)) is None
    assert normalize(Interpretation(action="grow_show", period="last_year", confidence=0.9)) is None
    r = normalize(Interpretation(action="grow_show", target="nature", period="this_month", place="x", confidence=0.9))
    assert r.target == "nature" and r.period == "this_month" and r.place is None
    # 전체 보기는 인자가 없다
    assert normalize(Interpretation(action="grow_show", confidence=0.9)).target is None
    # 다른 동작에는 기간이 붙지 않는다
    assert normalize(Interpretation(action="go_tab", target="grow", period="this_month", confidence=0.9)).period is None


def test_grow_go_only_takes_listed_places():
    assert normalize(Interpretation(action="grow_go", place="에버랜드", confidence=0.9), GROW_PLACES) is None
    assert normalize(Interpretation(action="grow_go", place="서울숲", confidence=0.9), None) is None
    # 이름 앞부분만 말해도 목록의 이름으로 맞춘다
    r = normalize(Interpretation(action="grow_go", place="국립중앙박물관", confidence=0.9), GROW_PLACES)
    assert r.place == "국립중앙박물관 어린이박물관"


def _failing(request):
    raise httpx2.ReadTimeout("3초 초과", request=request)


@pytest.mark.parametrize(
    ("text", "tab", "expected"),
    [
        ("지난달 간 곳 보여줘", "home", {"action": "grow_show", "period": "last_month", "target": None}),
        ("이번 달 어디 갔어?", "home", {"action": "grow_show", "period": "this_month", "target": None}),
        ("공룡 별 보여줘", "home", {"action": "grow_show", "period": None, "target": "science"}),
        ("지난달 물놀이 간 곳", "home", {"action": "grow_show", "period": "last_month", "target": "water"}),
        ("자연", "grow", {"action": "grow_show", "period": None, "target": "nature"}),
        ("경험 우주 전체 보여줘", "home", {"action": "grow_show", "period": None, "target": None}),
        ("다시 가볼 곳 알려줘", "home", {"action": "grow_revisit"}),
        ("서울숲 다시 가자", "home", {"action": "grow_go", "place": "서울숲"}),
        ("국립중앙박물관 또 가고 싶어", "home", {"action": "grow_go", "place": "국립중앙박물관 어린이박물관"}),
    ],
)
def test_rule_fallback_reads_grow_commands_when_llm_fails(text, tab, expected):
    r = fake(_failing).interpret(InterpretRequest(text=text, screen=Screen(tab=tab, places=GROW_PLACES)))
    assert r.source == "rule"
    for k, v in expected.items():
        assert getattr(r, k) == v, (k, getattr(r, k))


@pytest.mark.parametrize(
    ("text", "tab"),
    [
        ("너무 더워", "home"),
        ("방금 수유했어", "grow"),
        ("자연", "home"),  # 그로우 화면이 아니면 분류 한 마디는 그로우 명령으로 보지 않는다
        ("에버랜드 다시 가자", "home"),  # 다녀온 곳 목록에 없다
        ("마지막 수유 기록", "home"),
    ],
)
def test_rule_fallback_leaves_other_sentences_unknown(text, tab):
    r = fake(_failing).interpret(InterpretRequest(text=text, screen=Screen(tab=tab, places=GROW_PLACES)))
    assert r.action == Action.UNKNOWN and r.source == "fallback"


def test_screen_places_are_bounded():
    with TestClient(app) as c:
        app.dependency_overrides[get_interpreter] = lambda: fake(_failing)
        try:
            assert c.post("/voice/interpret", json={"text": "x", "screen": {"places": ["a"] * 31}}).status_code == 422
            assert c.post("/voice/interpret", json={"text": "x", "screen": {"places": ["가" * 61]}}).status_code == 422
        finally:
            app.dependency_overrides.pop(get_interpreter, None)

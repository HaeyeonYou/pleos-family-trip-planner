"""음성 명령 해석: 인식된 한국어 문장을 앱이 실제로 할 수 있는 동작 하나로 바꾼다 (#70).

- 해석은 Haiku(llm.py)가 하고, 결과는 아래 동작 목록(Action)과 인자 범위로 다시 검증한다.
  목록에 없거나 인자가 범위를 벗어나거나 확신이 낮으면 unknown으로 돌려 앱이 되묻게 한다.
- 3초 안에 못 받거나 오류면 그로우 명령만 규칙(voice_rules.py)으로 해석해 source="rule"로 돌려준다(#185).
  그 밖에는 source="fallback"의 unknown을 돌려준다. 앱은 키워드 규칙·예시 문장 버튼으로 되돌아간다.
- 운전 중 위험한 동작의 확인("응/아니")은 앱이 한다. 서버는 동작과 읽어 줄 짧은 답변만 정한다.
- 로그에는 문장 원문을 남기지 않는다(길이·걸린 시간·동작 이름만).
"""

import logging
import time
from enum import Enum
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.config import Settings, get_settings
from app.llm import LlmClient
from app.voice_rules import GROW_KINDS, interpret_grow, match_place

log = logging.getLogger("voice")
router = APIRouter(prefix="/voice")

MIN_CONFIDENCE = 0.5
MAX_REPLY_CHARS = 80


class Action(str, Enum):
    CARE_SITUATION = "care_situation"  # situation: sleep|diaper|feed|soothe
    SLEEP_MODE = "sleep_mode"  # on
    BABY_CARE = "baby_care"  # on
    CLIMATE = "climate"  # temperature / temperature_delta / fan_speed / ac / driver_seat_heat / passenger_seat_heat / steering_heat 중 하나 이상
    FEED_LOG = "feed_log"  # 방금 수유했어
    FEED_INTERVAL = "feed_interval"  # interval_minutes
    FIND_PLACE = "find_place"  # kind
    ADD_WAYPOINT = "add_waypoint"  # kind: 가까운 그곳을 경유지로 넣는다
    REMOVE_WAYPOINT = "remove_waypoint"
    GO_TAB = "go_tab"  # tab
    SET_FILTER = "set_filter"  # filter, on
    ANOTHER_COURSES = "another_courses"
    OPEN_COURSE = "open_course"  # number
    START_COURSE = "start_course"
    CHOOSE = "choose"  # number (보이는 후보 중 번호)
    CONFIRM = "confirm"  # 응·네·그래
    CANCEL = "cancel"  # 아니야·취소
    KEEP_GOING = "keep_going"  # 그대로 가기
    QUERY = "query"  # topic
    GROW_SHOW = "grow_show"  # period?, target(분류)? — 그로우에서 기간·분류로 거르기 (#185)
    GROW_REVISIT = "grow_revisit"  # 다시 가볼 곳 고르기
    GROW_GO = "grow_go"  # place — 다녀온 곳으로 다시 안내
    UNKNOWN = "unknown"


class Situation(str, Enum):
    SLEEP = "sleep"
    DIAPER = "diaper"
    FEED = "feed"
    SOOTHE = "soothe"


class PlaceKind(str, Enum):
    NURSING = "nursing"
    DIAPER = "diaper"
    REST = "rest"
    RESTROOM = "restroom"


class Tab(str, Enum):
    HOME = "home"
    CARE = "care"
    GROW = "grow"
    SETTINGS = "settings"


class CourseFilter(str, Enum):
    PET = "pet"
    INDOOR = "indoor"
    NURSING_ROOM = "nursing_room"
    STROLLER = "stroller"
    KIDS_MENU = "kids_menu"
    PLAYROOM = "playroom"
    PARKING = "parking"


class Topic(str, Enum):
    ETA = "eta"  # 언제 도착해
    NEXT_FEED = "next_feed"  # 다음 수유까지 얼마나
    LAST_FEED = "last_feed"
    WEATHER = "weather"
    CLIMATE = "climate"  # 지금 공조 설정


class Period(str, Enum):
    THIS_MONTH = "this_month"
    LAST_MONTH = "last_month"


MAX_PLACES = 30
MAX_PLACE_CHARS = 60


class Screen(BaseModel):
    """앱이 알려 주는 지금 상태 요약. 해석의 맥락으로만 쓴다."""

    tab: Tab = Tab.HOME
    has_course: bool = False
    guiding: bool = False
    baby_care: bool = False
    sleep_mode: bool = False
    child_months: int | None = Field(None, ge=0, le=240)
    # 음성 패널에 번호로 고를 후보가 떠 있는지
    has_options: bool = False
    # 홈에 추천 코스가 떠 있는지
    has_home_options: bool = False
    # 그로우: 다녀온 곳 이름(최근 순). "○○ 다시 가자"의 장소는 이 안에서만 고른다.
    places: list[Annotated[str, Field(max_length=MAX_PLACE_CHARS)]] = Field(default_factory=list, max_length=MAX_PLACES)


class InterpretRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=200)
    screen: Screen = Field(default_factory=Screen)


class Interpretation(BaseModel):
    """모델이 채우는 결과. 필요한 인자만 채우고 나머지는 비운다.

    선택 인자가 많은 스키마는 모델 쪽 처리가 멈추므로(선택 항목은 24개 이하로) 같은 뜻의 인자는 target·number로 합쳤다.
    """

    action: str = Field(..., description="동작 이름 하나: " + ", ".join(a.value for a in Action) + ". 맞는 것이 없으면 unknown")
    target: str | None = Field(
        None,
        description="care_situation: sleep|diaper|feed|soothe / find_place·add_waypoint: nursing|diaper|rest|restroom / "
        "go_tab: home|care|grow|settings / set_filter: pet|indoor|nursing_room|stroller|kids_menu|playroom|parking / "
        "query: eta|next_feed|last_feed|weather|climate / grow_show: " + "|".join(GROW_KINDS) + " (분류를 말했을 때만)",
    )
    period: str | None = Field(None, description="grow_show: this_month(이번 달)|last_month(지난달). 기간을 말하지 않았으면 비움")
    place: str | None = Field(None, description="grow_go: 화면 상태 places 중 하나를 글자 그대로")
    on: bool | None = Field(None, description="sleep_mode, baby_care, set_filter에서 켜기(true)/끄기(false)")
    number: int | None = Field(None, description="choose·open_course: 1부터 번호 / feed_interval: 분 / climate: 바람 세기 1(약)~8(강)")
    temperature: float | None = Field(None, description="climate: 목표 온도(°C), 절대값을 말했을 때만")
    temperature_delta: float | None = Field(None, description="climate: 지금보다 올리면 +, 내리면 -(°C). 덥다/춥다처럼 상대적일 때")
    ac: bool | None = Field(None, description="climate: 에어컨 켜기/끄기")
    driver_seat_heat: int | None = Field(None, description="climate: 운전석 열선 0(끔)~3")
    passenger_seat_heat: int | None = Field(None, description="climate: 조수석 열선 0(끔)~3")
    steering_heat: int | None = Field(None, description="climate: 핸들 열선 0(끔)~3")
    confidence: float = Field(..., description="해석에 대한 확신 0~1")
    reply: str = Field("", description="사용자에게 읽어 줄 짧은 한국어 답변(한 문장). 모르는 사실은 지어내지 않는다")


class VoiceResult(Interpretation):
    """앱에 돌려주는 결과. source는 llm(모델 해석) / fallback(시간 초과·오류·검증 실패)."""

    source: str = "llm"


# 앱 쪽 제어 범위와 같아야 한다 (PleosVehicleRepository가 구역 범위로 한 번 더 맞춘다).
TEMP_MIN, TEMP_MAX = 16.0, 30.0
DELTA_MAX = 6.0
FAN_MIN, FAN_MAX = 1, 8
HEAT_MAX = 3
INTERVALS = (120, 150, 180, 240)


_SYSTEM = """\
너는 어린 아이가 있는 가족의 차량 앱 「차곡」의 음성 명령 해석기다.
운전 중 짧게 말하는 한국어 문장을 앱이 실제로 할 수 있는 동작 하나로 바꾼다. 앱에 없는 일은 action=unknown.

동작 목록과 인자:
- care_situation(target): 아이 상황을 말할 때. sleep(애가 잔다·재웠다), diaper(쌌다·기저귀), feed(배고프다·밥), soothe(운다·보챈다).
- sleep_mode(on): 수면 모드를 켜거나 끄라는 명시적 요청.
- baby_care(on): 베이비케어 모드(아기용 공조)를 켜거나 끄라는 요청.
- climate: 차 공조 조절. 덥다/춥다는 temperature_delta(덥다 -2, 많이 덥다 -4, 춥다 +2), 숫자로 말하면 temperature(16~30), 바람은 number 1~8(약하게 2, 세게 6),
  에어컨은 ac, 열선은 driver_seat_heat/passenger_seat_heat/steering_heat 0~3. 말한 것만 채우고 나머지는 비운다.
- feed_log: 방금 수유했다고 기록. feed_interval(number=분): 수유 간격을 120/150/180/240분 중 하나로.
- find_place(target): 근처 장소 찾기. nursing(수유실), diaper(기저귀 교환대), rest(쉴 곳·주차장·공원), restroom(화장실).
- add_waypoint(target): 가는 길에 그런 곳을 경유지로 넣어 달라는 요청. remove_waypoint: 경유지를 빼라는 요청.
- go_tab(target): 화면 이동. home(홈), care(케어), grow(그로우), settings(설정).
- set_filter(target, on): 코스 추천 조건. pet(반려견), indoor(실내), nursing_room(수유실), stroller(유아차), kids_menu(키즈메뉴), playroom(놀이방), parking(주차). "반려견 빼고"는 target=pet,on=false.
- another_courses: 다른 코스 보기. open_course(number): 추천 코스 N번 열기. start_course: 코스 안내 시작.
- choose(number): 화면에 떠 있는 후보 중 N번 고르기("1번으로 가줘", 화면의 has_options가 true일 때). cancel: 아니야·취소. confirm: 응·네·그래. keep_going: 그대로 가기.
- query(target): 앱이 가진 정보 묻기. eta(언제 도착), next_feed(다음 수유까지), last_feed, weather(날씨), climate(지금 공조).
- grow_show(period, target): 그로우(아이 경험 우주)에서 다녀온 곳·경험 보기. period는 this_month(이번 달)·last_month(지난달)만,
  target은 경험 분류: art(전시·미술·유니콘), science(과학·박물관·공룡), nature(자연·숲·공원·곰), animal(동물·강아지), water(물놀이·카피바라),
  move(몸놀이·키즈카페·놀이터), music(음악·공연·고양이), book(책·도서관·웜뱃), food(맛 탐험·식당·카페·햄스터), friend(친구·펭귄),
  make(만들기·공방·체험·비버), travel(여행·수달). 말한 것만 채운다. "경험 우주 전체 보여줘"는 둘 다 비운다.
  "지난달 간 곳 보여줘"·"이번 달 어디 갔어?"·"공룡 별 보여줘". 다른 기간(작년, 3월 등)은 unknown. 아이 이름은 무시한다.
- grow_revisit: "다시 가볼 곳 알려줘"처럼 예전에 간 곳 중 다시 갈 곳을 골라 달라는 요청.
- grow_go(place): "○○ 다시 가자"처럼 다녀온 곳으로 다시 가자는 요청. place는 화면 상태 places 중 하나(글자 그대로). 목록에 없으면 unknown.
- unknown: 위에 없는 요청, 일반 대화, 위험하거나 모호한 요청.

규칙:
- 지금 화면 상태(JSON)는 참고만 한다. 요청에 없는 값은 지어내지 않는다.
- reply는 사용자에게 읽어 줄 한 문장(존댓말, 40자 안팎). 숫자·시각·장소 같은 사실은 쓰지 말고 앱이 채운다. 예: "에어컨 온도를 낮출게요."
- 문장이 모호하면 action=unknown, confidence를 낮게.
"""


_TARGETS = {
    Action.CARE_SITUATION: Situation,
    Action.FIND_PLACE: PlaceKind,
    Action.ADD_WAYPOINT: PlaceKind,
    Action.GO_TAB: Tab,
    Action.SET_FILTER: CourseFilter,
    Action.QUERY: Topic,
}


def normalize(result: Interpretation, places: list[str] | None = None) -> Interpretation | None:
    """모델 결과를 동작별 필수 인자와 범위로 검증한다. 맞지 않으면 None (앱이 되묻는다). [places]는 grow_go가 고를 수 있는 이름."""
    action = getattr(result.action, "value", result.action)
    if action not in {a.value for a in Action}:
        return None
    a = Action(action)
    result.action = a.value
    if not 0 <= result.confidence <= 1 or result.confidence < MIN_CONFIDENCE:
        return None
    allowed = _TARGETS.get(a)
    if a == Action.GROW_SHOW:
        target = getattr(result.target, "value", result.target) or None
        if target is not None and target not in GROW_KINDS:
            return None
        period = getattr(result.period, "value", result.period) or None
        if period is not None and period not in {p.value for p in Period}:
            return None
        result.target, result.period = target, period
    elif allowed is not None:
        target = getattr(result.target, "value", result.target)
        if target not in {m.value for m in allowed}:
            return None
        result.target = target
    else:
        result.target = None
    if a != Action.GROW_SHOW:
        result.period = None
    if a == Action.GROW_GO:
        # 모델이 이름을 조금 바꿔 말해도 목록의 이름으로 맞춘다. 목록에 없는 곳은 받지 않는다.
        place = result.place if result.place in (places or []) else match_place(result.place or "", places or [])
        if place is None:
            return None
        result.place = place
    else:
        result.place = None
    if a in (Action.SLEEP_MODE, Action.BABY_CARE, Action.SET_FILTER) and result.on is None:
        return None
    if a == Action.CLIMATE:
        fields = [result.temperature, result.temperature_delta, result.number, result.ac,
                  result.driver_seat_heat, result.passenger_seat_heat, result.steering_heat]
        if all(f is None for f in fields):
            return None
        if result.temperature is not None:
            result.temperature = min(max(result.temperature, TEMP_MIN), TEMP_MAX)
        if result.temperature_delta is not None:
            result.temperature_delta = min(max(result.temperature_delta, -DELTA_MAX), DELTA_MAX)
        if result.number is not None:
            result.number = min(max(result.number, FAN_MIN), FAN_MAX)
        for name in ("driver_seat_heat", "passenger_seat_heat", "steering_heat"):
            v = getattr(result, name)
            if v is not None:
                setattr(result, name, min(max(v, 0), HEAT_MAX))
    elif a == Action.FEED_INTERVAL:
        if result.number is None:
            return None
        # 가장 가까운 허용 간격으로 맞춘다 (앱의 간격 선택지와 같다).
        result.number = min(INTERVALS, key=lambda m: abs(m - result.number))
    elif a in (Action.CHOOSE, Action.OPEN_COURSE):
        if result.number is None or not 1 <= result.number <= 3:
            return None
    result.reply = result.reply.strip()[:MAX_REPLY_CHARS]
    return result


class VoiceInterpreter:
    def __init__(self, llm: LlmClient):
        self._llm = llm

    def interpret(self, req: InterpretRequest) -> VoiceResult:
        started = time.perf_counter()
        context = f"지금 화면 상태: {req.screen.model_dump_json()}\n사용자 말: {req.text}"
        raw = self._llm.structured("음성 해석", _SYSTEM, context, Interpretation, max_tokens=500)
        result = normalize(raw, req.screen.places) if raw is not None else None
        if result is None:
            rule = interpret_grow(req.text, req.screen.places, req.screen.tab == Tab.GROW)
            ruled = normalize(Interpretation(**rule), req.screen.places) if rule else None
            if ruled is not None:
                log.info("음성 해석 %d자 → %s (규칙, %.0fms)", len(req.text), ruled.action, (time.perf_counter() - started) * 1000)
                return VoiceResult(**ruled.model_dump(mode="json"), source="rule")
            log.info("음성 해석 %d자 → unknown (%s, %.0fms)", len(req.text), "llm 실패" if raw is None else "검증 실패", (time.perf_counter() - started) * 1000)
            return VoiceResult(action=Action.UNKNOWN.value, confidence=0.0, source="fallback")
        log.info("음성 해석 %d자 → %s (%.0fms)", len(req.text), result.action, (time.perf_counter() - started) * 1000)
        return VoiceResult(**result.model_dump(mode="json"), source="llm")


_interpreter: VoiceInterpreter | None = None


def get_interpreter(settings: Settings = Depends(get_settings)) -> VoiceInterpreter:
    global _interpreter
    if not settings.anthropic_api_key:
        raise HTTPException(503, "ANTHROPIC_API_KEY가 필요합니다")
    if _interpreter is None:
        _interpreter = VoiceInterpreter(LlmClient(settings.anthropic_api_key, settings.claude_model))
    return _interpreter


@router.post("/interpret", response_model=VoiceResult)
def interpret(req: InterpretRequest, interpreter: VoiceInterpreter = Depends(get_interpreter)) -> VoiceResult:
    return interpreter.interpret(req)

"""그로우(경험 우주) 음성 명령의 규칙 해석 (#185).

Haiku가 시간 안에 못 오거나 검증에 실패했을 때만 쓴다. 그로우 의도 세 가지만 다루고, 확실하지 않으면 None.
같은 문장은 늘 같은 결과다. 띄어쓰기는 무시하고 비교한다.
"""

# 12분류 (앱 grow/JellyModel.kt JellyKind와 같은 이름을 소문자로). 앞에서부터 처음 맞는 분류.
# 캐릭터 이름(공룡·곰 …)으로도 부른다. "말"(몸놀이 캐릭터)은 "말해줘"와 헷갈려 넣지 않는다.
GROW_KINDS: dict[str, tuple[str, ...]] = {
    "book": ("책", "도서관", "웜뱃", "이야기"),
    "water": ("물놀이", "수영", "카피바라", "워터"),
    "animal": ("동물", "강아지", "목장", "농장", "아쿠아리움"),
    "music": ("음악", "공연", "고양이", "뮤지컬"),
    "art": ("전시", "미술", "유니콘", "갤러리"),
    "science": ("과학", "박물관", "공룡"),
    "friend": ("친구", "펭귄", "어울림"),
    "make": ("만들기", "공방", "비버", "체험"),
    "move": ("몸놀이", "키즈카페", "놀이터", "놀이공원"),
    "food": ("맛탐험", "맛집", "식당", "카페", "햄스터"),
    "nature": ("자연", "숲", "공원", "수목원", "곰"),
    "travel": ("여행", "수달"),
}

_THIS_MONTH = ("이번달", "이달", "요번달")
_LAST_MONTH = ("지난달", "저번달", "전달")
# 다녀온 곳·경험 우주를 말하는 표현 (없으면 그로우 명령으로 보지 않는다)
_GROW_WORDS = ("간곳", "갔던", "갔어", "갔지", "갔나", "다녀온", "다녀왔", "가본", "경험", "별", "우주", "그로우", "기록")
_ALL_WORDS = ("전체", "전부", "다보여", "모두")
_REVISIT = ("다시가볼", "다시갈", "또갈", "또가볼", "다시가면좋", "다시갈만", "또갈만")
_GO = ("다시가", "또가", "가자", "가고싶", "안내해", "데려다", "길안내")


def _compact(text: str) -> str:
    return "".join(ch for ch in text if ch.isalnum())


def match_place(text: str, places: list[str]) -> str | None:
    """말 속에 들어 있는 다녀온 곳 이름. 가장 길게 맞는 것. 이름 전체가 없으면 앞 4글자 이상이 맞는 것."""
    said = _compact(text)
    best: tuple[int, str] | None = None
    for name in places:
        c = _compact(name)
        if not c:
            continue
        if c in said:
            score = len(c) * 2
        else:
            # "서울숲 다시 가자"처럼 이름 앞부분만 말하는 경우 ("서울숲공원")
            n = len(c)
            while n >= 3 and c[:n] not in said:
                n -= 1
            if n < 3 or n < min(4, len(c)):
                continue
            score = n
        if best is None or score > best[0]:
            best = (score, name)
    return best[1] if best else None


def grow_kind(text: str) -> str | None:
    said = _compact(text)
    for kind, words in GROW_KINDS.items():
        if any(w in said for w in words):
            return kind
    return None


def grow_period(text: str) -> str | None:
    said = _compact(text)
    if any(w in said for w in _LAST_MONTH):
        return "last_month"
    if any(w in said for w in _THIS_MONTH):
        return "this_month"
    return None


def interpret_grow(text: str, places: list[str], on_grow_tab: bool) -> dict | None:
    """그로우 명령이면 Interpretation 필드 dict, 아니면 None."""
    said = _compact(text)
    if not said:
        return None
    if any(w in said for w in _REVISIT):
        return {"action": "grow_revisit", "confidence": 0.8, "reply": "다시 가 볼 곳을 골라 볼게요."}
    place = match_place(text, places)
    if place and any(w in said for w in _GO):
        return {"action": "grow_go", "place": place, "confidence": 0.8, "reply": ""}
    period = grow_period(text)
    kind = grow_kind(text)
    grow_context = any(w in said for w in _GROW_WORDS)
    if not grow_context and not (on_grow_tab and (period or kind or any(w in said for w in _ALL_WORDS))):
        return None
    if period is None and kind is None and not any(w in said for w in _ALL_WORDS + ("우주", "그로우")):
        return None
    return {"action": "grow_show", "period": period, "target": kind, "confidence": 0.75, "reply": ""}

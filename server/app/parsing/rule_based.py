import re

from app import interests
from app.schemas import Theme, TripConditions

_THEME_KEYWORDS: dict[Theme, tuple[str, ...]] = {
    Theme.NATURE: ("자연", "숲", "공원", "산책", "바다", "계곡", "수목원"),
    Theme.EXPERIENCE: ("체험", "만들기", "농장", "목장", "공방"),
    Theme.EXHIBITION: ("전시", "박물관", "미술관", "과학관"),
    Theme.PLAY: ("놀이", "키즈카페", "놀이터", "놀이공원", "동물원", "아쿠아리움"),
}
_PET_KEYWORDS = ("강아지", "고양이", "반려", "댕댕", "애견")
_INDOOR_KEYWORDS = ("실내", "비 와도", "비와도", "비 오", "비오", "추워", "더워", "미세먼지")
_NEEDS_KEYWORDS = {
    "stroller": ("유모차", "유아차"),
    "nursing_room": ("수유실", "수유"),
    "kids_chair": ("아기의자", "유아의자", "아기 의자"),
    "kids_zone": ("키즈존", "놀이방"),
}

_AGE_RE = re.compile(r"(\d{1,2})\s*(살|세)")
_MONTH_RE = re.compile(r"(\d{1,2})\s*개월")
_RETURN_RE = re.compile(r"(오후|저녁)?\s*(\d{1,2})\s*시\s*(전|까지|이전)")
_DRIVE_RE = re.compile(r"(\d{1,3})\s*(분|시간)\s*(이내|안쪽|거리|안)")
# "공룡이랑 기차 좋아해", "우주에 관심 많아", "상어 보러"처럼 관심을 드러내는 말
_INTEREST_TRIGGER_RE = re.compile(r"좋아|관심|빠져|보러|보고\s*싶")
_CLAUSE_BREAK_RE = re.compile(r"[,.!?\n]|는데|지만")


class RuleBasedParser:
    name = "rule"

    def parse(self, text: str) -> TripConditions:
        ages = [int(m.group(1)) for m in _AGE_RE.finditer(text)]
        ages += [0 for _ in _MONTH_RE.finditer(text)]
        if not ages and re.search(r"아기|아가|신생아", text):
            ages = [0]

        themes = [t for t, words in _THEME_KEYWORDS.items() if any(w in text for w in words)]
        with_pet = any(w in text for w in _PET_KEYWORDS)
        if with_pet:
            themes.append(Theme.PET)

        return TripConditions(
            child_ages=ages,
            with_pet=with_pet,
            themes=themes,
            indoor_only=any(w in text for w in _INDOOR_KEYWORDS),
            return_by_hour=_parse_return_hour(text),
            max_drive_minutes=_parse_drive_minutes(text),
            needs=[k for k, words in _NEEDS_KEYWORDS.items() if any(_mentions(text, w) for w in words)],
            interests=_parse_interests(text),
        )


_NEGATION_RE = re.compile(r"^\s*(은|는|이|가|도)?\s*(필요\s*없|없어도|안\s*필요|괜찮|빼)")


def _mentions(text: str, word: str) -> bool:
    """word가 등장하고, 바로 뒤에 '필요 없'같은 부정이 붙지 않은 경우만 True."""
    for m in re.finditer(re.escape(word), text):
        if not _NEGATION_RE.match(text[m.end():m.end() + 8]):
            return True
    return False


def _parse_return_hour(text: str) -> int | None:
    m = _RETURN_RE.search(text)
    if not m:
        return None
    hour = int(m.group(2))
    # "6시 전"처럼 오전/오후가 없으면 나들이 맥락상 오후로 본다
    if hour < 12 and (m.group(1) or hour <= 9):
        hour += 12
    return min(hour, 23)


def _parse_drive_minutes(text: str) -> int | None:
    m = _DRIVE_RE.search(text)
    if not m:
        return None
    value = int(m.group(1))
    return value * 60 if m.group(2) == "시간" else value


def _parse_interests(text: str) -> list[str]:
    """관심 표현 바로 앞 구절에서 관심사 사전 단어를 찾아, 문장에 나온 순서대로 돌려준다."""
    hits: list[tuple[int, str]] = []
    for trigger in _INTEREST_TRIGGER_RE.finditer(text):
        start = max(0, trigger.start() - 20)
        window = text[start:trigger.start()]
        breaks = list(_CLAUSE_BREAK_RE.finditer(window))
        offset = start + (breaks[-1].end() if breaks else 0)
        window = text[offset:trigger.start()]
        for word in interests.vocabulary():
            # 앞에 한글이 붙은 경우('산책'의 '책')는 다른 낱말이라 건너뛴다.
            pattern = re.compile(rf"(?<![가-힣]){re.escape(word)}")
            for m in pattern.finditer(window):
                hits.append((offset + m.start(), word))
            window = pattern.sub(lambda m: " " * len(m.group()), window)
    found: list[str] = []
    for _, word in sorted(hits):
        name = interests.normalize(word)
        if name and name not in found:
            found.append(name)
    return found

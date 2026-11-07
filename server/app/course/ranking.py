"""점수와 방문 이력을 반영한 가중 선택. 모두 순수 함수라 시드만 같으면 결과가 같다."""

import math
import random
from collections.abc import Callable, Sequence
from datetime import date
from typing import TypeVar

from app.schemas import Theme, VisitSummary

T = TypeVar("T")

PICK_MARGIN = 1.5  # 1등과 이 점수 안쪽 후보만 가중 선택 대상 (반려견·필요 시설·테마 일치 가점 2~3점은 뒤집히지 않는다)
PICK_POOL = 12     # 가중 선택 대상 최대 수
TEMPERATURE = 1.0  # 클수록 점수 차이를 덜 따진다

DISTANCE_WEIGHT = 1.0  # 거리 감점 최대치 (예전 2.0). 가까운 곳이 늘 1등이 되지 않도록 낮췄다.


def weighted_order(items: Sequence[T], score: Callable[[T], float], rng: random.Random) -> list[T]:
    """점수 순으로 정렬한 뒤, 상위 후보를 점수 가중으로 무작위 섞은 순서를 돌려준다.

    1등과 PICK_MARGIN 이내인 후보(최대 PICK_POOL개)를 Efraimidis-Spirakis 방식으로 뽑는다.
    나머지는 점수 순으로 뒤에 붙인다. 같은 rng 상태면 같은 순서다.
    """
    ordered = sorted(items, key=score, reverse=True)
    if len(ordered) < 2:
        return ordered
    top = score(ordered[0])
    pool = [c for c in ordered[:PICK_POOL] if top - score(c) <= PICK_MARGIN]
    keyed = []
    for c in pool:
        weight = math.exp((score(c) - top) / TEMPERATURE)
        keyed.append((math.log(1 - rng.random()) / weight, c))  # log(u)/w, 클수록 먼저
    keyed.sort(key=lambda kc: kc[0], reverse=True)
    picked = [c for _, c in keyed]
    return picked + ordered[len(pool):]


def visit_adjustment(place_key: str, theme: Theme | None, visits: Sequence[VisitSummary], today: date) -> float:
    """최근 다녀온 장소는 감점, 최근에 안 간 분야는 가점."""
    adjust = 0.0
    theme_visits = 0
    any_recent = False
    for v in visits:
        days = (today - date.fromisoformat(v.date)).days
        if days < 0:
            continue
        if days <= 30:
            any_recent = True
        if place_key and v.place_key == place_key:
            adjust -= 5.0 if days <= 14 else 3.0 if days <= 45 else 1.0 if days <= 90 else 0.0
        if theme is not None and v.theme == theme and days <= 30:
            theme_visits += 1
    if theme is not None and any_recent:
        adjust += 1.0 if theme_visits == 0 else -0.5 * min(theme_visits, 3)
    return adjust


def eatery_score(distance_m: float, radius_m: int, category: str, name: str, role_words: dict[str, float]) -> float:
    """식사·카페 후보 점수: 거리, 아이 동반 편의(키즈·가족 메뉴), 분류 적합성."""
    score = -1.5 * min(distance_m / radius_m, 1)
    text = f"{category} {name}"
    for word, bonus in role_words.items():
        if word in text:
            score += bonus
    return score


MEAL_WORDS = {
    "키즈": 1.5, "패밀리": 1.0, "한식": 0.4, "샤브샤브": 0.5, "돈까스": 0.5, "국수": 0.4,
    "죽": 0.4, "뷔페": 0.5, "분식": -0.3, "치킨": -0.3, "패스트푸드": -0.5,
}
CAFE_WORDS = {
    "키즈": 1.5, "브런치": 0.6, "베이커리": 0.4, "디저트": 0.3, "아이스크림": 0.2, "테마카페": -0.5,
}

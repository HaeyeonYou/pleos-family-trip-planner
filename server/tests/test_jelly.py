"""그로우 젤리 12분류 판정 (#186). 실제 API 응답 저장본(tests/api_cache)의 장소로 확인한다."""

import json
from functools import cache

import pytest

from app.course import jelly
from app.schemas import Jelly, Place, Theme
from app.sources import kakao, tour
from tests.api_cache import CACHE_DIR

J = Jelly


@cache
def cached_places() -> dict[str, tuple[str, str]]:
    """저장본에 든 카카오·관광공사 장소: 이름 → (분류 원문, 출처). 관광공사는 cat3, 없으면 lclsSystm3(행사)."""
    found: dict[str, tuple[str, str]] = {}
    for path in CACHE_DIR.glob("*.json"):
        try:
            body = json.loads(json.loads(path.read_text(encoding="utf-8"))["body"])
        except (ValueError, KeyError):
            continue
        if isinstance(body, dict) and "documents" in body:
            for doc in body["documents"]:
                if "category_name" in doc and "y" in doc:
                    p = kakao._to_place(doc)
                    found.setdefault(p.name, (p.category, "kakao"))
            continue
        try:
            items = body["response"]["body"]["items"]["item"]
        except (TypeError, KeyError):
            continue
        for item in items if isinstance(items, list) else [items]:
            if "mapy" in item and "contentid" in item:
                p = tour._to_place(item)
                found.setdefault(p.title, (p.cat3 or p.lcls3, "tour"))
    return found


# 저장본에 실제로 있는 장소와 기대 분류
REAL = [
    ("블루타이거 서초", J.MOVE),                     # 가정,생활 > 유아 > 놀이시설 > 키즈카페
    ("타임빌라스 수원점", J.TRAVEL),                  # 가정,생활 > 복합쇼핑몰 (예전 앱 규칙: play → 몸놀이)
    ("현대백화점 천호점", J.TRAVEL),                  # 가정,생활 > 백화점
    ("챔피언 NC수원터미널점", J.MOVE),                # 교육,학문 > 유아교육 > 놀이교육
    ("서울특별시교육청 강동도서관", J.BOOK),            # 교육,학문 > 학습시설 > 도서관
    ("국립한글박물관", J.SCIENCE),                    # 문화,예술 > 문화시설 > 박물관
    ("서울하수도과학관", J.SCIENCE),                  # 문화시설 > 과학관
    ("별숲그리다", J.MAKE),                           # 문화,예술 > 미술,공예
    ("하남시육아종합지원센터 감일점", J.FRIEND),        # 사회복지시설 > 아동복지시설 > 육아종합지원센터
    ("강동어린이회관", J.FRIEND),                      # 아동복지시설
    ("더왈츠앤조이피아", J.ANIMAL),                    # 음식점 > 카페 > 테마카페 > 애견카페
    ("하마커피", J.FOOD),                             # 음식점 > 카페
    ("콩두주백 양재직영점", J.FOOD),                   # 음식점 > 한식
    ("서울어린이대공원 놀이동산 디노기차", J.MOVE),     # 여행 > 관광,명소 > 테마파크
    ("성심어린이공원", J.NATURE),                      # 여행 > 공원 > 도시근린공원
    ("해움미술관", J.ART),                            # A02060500 미술관
    ("역삼도서관", J.BOOK),                           # A02060900 도서관
    ("별책부록", J.BOOK),                             # A02061000 대형서점
    ("전쟁기념관", J.SCIENCE),                        # A02060200 기념관
    ("역삼개나리공원", J.NATURE),                      # A02020700 공원
    ("마루공원", J.MOVE),                             # A02020600 테마공원
    ("구룡산(서울)", J.NATURE),                       # A01 자연관광지
    ("팔달문", J.TRAVEL),                             # A0201 역사관광지
    ("한강시민공원 뚝섬수영장(실외)", J.WATER),        # A03030700 물놀이·수영장
    ("목동아이스링크(실내)", J.MOVE),                  # A03021300 스케이트장
    ("광주버스킹월드컵", J.MUSIC),                     # EV020700 공연 (행사는 cat3 없이 lclsSystm3만)
    ("명주인형극제", J.MUSIC),                         # EV020200
    ("당진국화전시회", J.ART),                         # EV030100 전시회
    ("강릉 국수 축제", J.FOOD),                        # EV010300 지역특산물 축제
    ("강남페스티벌", J.TRAVEL),                        # EV010200 일반 축제
]


@pytest.mark.parametrize(("name", "expected"), REAL)
def test_real_places_are_judged_by_their_category(name, expected):
    category, source = cached_places()[name]
    assert jelly.judge(name, category) == expected, f"{name} ({source}: {category})"
    # Place를 만들면 같은 값이 채워진다 (코스 응답에 실림)
    assert Place(name=name, latitude=37.5, longitude=127.0, category=category, source=source, source_id="x").jelly == expected


def test_category_beats_name_words_and_name_beats_theme():
    # 이름에 '랜드'가 있어도 분류가 동물원이면 동물·교감
    assert jelly.judge("주렁주렁 애니멀랜드", "여행 > 관광,명소 > 동물원") == J.ANIMAL
    # 분류가 넓으면(관광,명소) 이름을 먼저 본다
    assert jelly.judge("하늘 전망대", "여행 > 관광,명소") == J.TRAVEL
    assert jelly.judge("국립 아쿠아리움", "여행 > 관광,명소") == J.ANIMAL
    # 분류가 없으면 이름 → 5분야 → 여행 (분류 원문이 없는 예전 방문 기록)
    assert jelly.judge("서울숲", "", "nature") == J.NATURE
    assert jelly.judge("어딘가", "", Theme.EXHIBITION) == J.SCIENCE
    assert jelly.judge("어딘가", "", "play") == J.MOVE
    assert jelly.judge("어딘가") == J.TRAVEL


def test_role_meal_and_cafe_are_food_unless_book_or_category_says_otherwise():
    assert jelly.judge("동네 서점카페", "", role="cafe") == J.BOOK
    assert jelly.judge("공룡떡볶이", "", role="meal") == J.FOOD  # 이름의 '공룡'에 끌려가지 않는다
    assert jelly.judge("어디든", "음식점 > 카페 > 테마카페 > 북카페", role="cafe") == J.BOOK


def test_public_data_groups_and_events():
    assert jelly.judge("국립민속박물관", "박물관") == J.SCIENCE
    assert jelly.judge("어린이공원(아이파크더샵)", "도시공원") == J.NATURE
    assert jelly.judge("가족 그림 전시", "전시 행사") == J.ART
    assert jelly.judge("어린이 음악회", "음악 행사") == J.MUSIC
    assert jelly.judge("가족 나들이", "기타 행사") == J.TRAVEL


def test_home_has_no_jelly_and_given_value_is_kept():
    assert Place(name="귀가", latitude=37.5, longitude=127.0, source="user", source_id="home").jelly is None
    kept = Place(name="서울숲", latitude=37.5, longitude=127.0, source="tour", source_id="1", jelly="book")
    assert kept.jelly == J.BOOK


def test_every_jelly_has_labels_and_stage_names():
    assert set(jelly.LABELS) == set(Jelly)
    assert [jelly.stage(n) for n in (0, 1, 3, 4, 9, 10)] == ["none", "drop", "drop", "puff", "puff", "born"]
    assert jelly.stage_name(J.NATURE, 2) == "곰 방울" and jelly.stage_name(J.NATURE, 12) == "푸근한 숲 곰형"

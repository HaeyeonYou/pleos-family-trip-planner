"""장소를 테마·실내 여부로 분류하고 아이 동반 나들이에 맞는지 점수를 매긴다."""

from app.schemas import Theme
from app.sources.kakao import KakaoPlace
from app.sources.tour import CONTENT_TYPE_CULTURE, TourPlace

# TourAPI 서비스분류(cat) 기준. 앞부분이 일치하면 해당 테마로 본다.
_TOUR_THEMES: list[tuple[str, Theme]] = [
    ("A02020600", Theme.PLAY),        # 테마공원
    ("A02020700", Theme.NATURE),      # 공원
    ("A0202", Theme.NATURE),          # 휴양관광지 (수목원, 자연휴양림 등)
    ("A01", Theme.NATURE),            # 자연관광지
    ("A0203", Theme.EXPERIENCE),      # 체험관광지 (농·산·어촌 체험, 전통체험)
    ("A0206", Theme.EXHIBITION),      # 문화시설 (박물관, 전시관, 미술관, 과학관)
]

# 테마 분류보다 먼저 거르는 세부 분류: 이색체험·이색거리(가로수길 같은 거리)와
# 문화시설 중 아이와 갈 곳이 아닌 것(컨벤션, 공연장, 문화원, 영화관, 어학당, 학교 등)
_EXCLUDED_CAT3_PREFIXES = ("A020304", "A020306")
_ALLOWED_CULTURE_CAT3 = (
    "A02060100",  # 박물관
    "A02060200",  # 기념관
    "A02060300",  # 전시관
    "A02060500",  # 미술관·화랑
    "A02060900",  # 도서관
    "A02061000",  # 대형서점
)

# 아이와 가기 어려운 장소 (분류만으로 거르기 어려운 것)
_EXCLUDED_TITLE_WORDS = ("묘역", "왕릉", "비석", "성터", "절터", "공연장", "극장", "컨벤션", "골프")

PLAY_KEYWORDS = ("키즈카페", "실내놀이터")


def tour_theme(place: TourPlace) -> Theme | None:
    for prefix, theme in _TOUR_THEMES:
        if place.cat3.startswith(prefix) or place.cat2.startswith(prefix) or place.cat1 == prefix:
            return theme
    return None


def tour_is_indoor(place: TourPlace) -> bool:
    return place.content_type_id == CONTENT_TYPE_CULTURE or place.cat2 == "A0206"


def tour_is_suitable(place: TourPlace) -> bool:
    if place.cat3.startswith(_EXCLUDED_CAT3_PREFIXES):
        return False
    if place.cat2 == "A0206" and place.cat3 not in _ALLOWED_CULTURE_CAT3:
        return False
    return tour_theme(place) is not None and not any(w in place.title for w in _EXCLUDED_TITLE_WORDS)


def restaurant_is_suitable(place: KakaoPlace) -> bool:
    # 식사 자리라서 술집과 간식(제과, 아이스크림 등)은 뺀다.
    return not any(w in place.category for w in ("술집", "호프", "요리주점", "칵테일", "바(BAR)", "간식"))


def cafe_is_suitable(place: KakaoPlace) -> bool:
    return not any(w in place.category for w in ("스터디카페", "보드게임", "만화카페", "룸카페"))

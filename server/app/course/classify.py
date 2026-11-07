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

# 레포츠(contentTypeId 28) 중 아이와 갈 만한 세부 분류 → (테마, 실내 여부 기본값)
_LEISURE_ALLOWED: dict[str, tuple[Theme, bool]] = {
    "A03020400": (Theme.PLAY, True),   # 롤러스케이트장
    "A03021300": (Theme.PLAY, True),   # 스케이트장
    "A03021200": (Theme.PLAY, False),  # 눈썰매장
    "A03030700": (Theme.PLAY, False),  # 물놀이·수영장
    "A03050100": (Theme.PLAY, True),   # 복합 레포츠
}
_LEISURE_EXCLUDED_WORDS = ("사격", "총", "낚시", "골프", "클럽")

# 주류·성인 대상 행사는 아이 동반 코스에서 뺀다
# 밤에 여는 행사도 낮 코스에 맞지 않아 뺀다 (행사 시각을 API가 주지 않는다)
_EVENT_EXCLUDED_WORDS = ("맥주", "옥토버", "와인", "막걸리", "주류", "위스키", "성인", "나이트", "야간", "야행", "야경", "밤길", "별빛", "달빛", "심야")

PLAY_KEYWORDS = ("키즈카페", "실내놀이터")

# 관심사 키워드로 카카오에서 찾은 장소 중 나들이로 쓸 분류 → (테마, 실내)
_KAKAO_OUTING_CATEGORIES: list[tuple[str, Theme, bool]] = [
    ("가정,생활 > 유아 > 놀이시설", Theme.PLAY, True),
    ("교육,학문 > 유아교육 > 놀이교육", Theme.PLAY, True),  # 쇼핑몰 안 놀이 공간 (플레이타임 등)
    ("가정,생활 > 복합쇼핑몰", Theme.PLAY, True),
    ("가정,생활 > 백화점", Theme.PLAY, True),
    ("문화,예술 > 문화시설 > 박물관", Theme.EXHIBITION, True),
    ("문화,예술 > 문화시설 > 전시관", Theme.EXHIBITION, True),
    ("문화,예술 > 문화시설 > 과학관", Theme.EXHIBITION, True),
    ("문화,예술 > 문화시설 > 미술관", Theme.EXHIBITION, True),
    ("문화,예술 > 문화시설 > 도서관", Theme.EXHIBITION, True),
    ("문화,예술 > 문화시설 > 아쿠아리움", Theme.NATURE, True),
    ("여행 > 관광,명소 > 테마파크", Theme.PLAY, False),
    ("여행 > 관광,명소 > 동물원", Theme.NATURE, False),
    ("여행 > 관광,명소 > 아쿠아리움", Theme.NATURE, True),
    ("여행 > 관광,명소 > 수목원,식물원", Theme.NATURE, False),
    ("여행 > 공원", Theme.NATURE, False),
    ("여행 > 관광,명소", Theme.NATURE, False),
]


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
    if any(w in place.name for w in ("맥주", "포차", "주점", "호프", "와인바", "이자카야")):
        return False
    return not any(w in place.category for w in ("술집", "호프", "요리주점", "칵테일", "바(BAR)", "간식"))


def cafe_is_suitable(place: KakaoPlace) -> bool:
    return not any(w in place.category for w in ("스터디카페", "보드게임", "만화카페", "룸카페"))


def kakao_outing_kind(place: KakaoPlace) -> tuple[Theme, bool] | None:
    """카카오 장소가 나들이 장소이면 (테마, 실내 여부)."""
    for prefix, theme, indoor in _KAKAO_OUTING_CATEGORIES:
        if place.category.startswith(prefix):
            return theme, indoor
    return None


def leisure_kind(place: TourPlace) -> tuple[Theme, bool] | None:
    """레포츠 장소가 아이 나들이에 맞으면 (테마, 실내 여부)."""
    kind = _LEISURE_ALLOWED.get(place.cat3)
    if kind is None or any(w in place.title for w in _LEISURE_EXCLUDED_WORDS):
        return None
    theme, indoor = kind
    return theme, indoor or "실내" in place.title


def event_is_suitable(place: TourPlace) -> bool:
    return not any(w in place.title for w in _EVENT_EXCLUDED_WORDS)

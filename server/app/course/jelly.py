"""그로우 젤리 B안 12분류 판정 (#186).

앱 `grow/JellyModel.kt`가 장소 이름 낱말로 추정하던 것을 서버가 분류 근거로 정한다.

판정 순서:
1. 분명한 분류: 관광공사 서비스분류 cat3(예: A02060500 미술관)·새 분류체계 lclsSystm3(행사 EV…),
   카카오 category_name(예: "문화,예술 > 문화시설 > 미술관"), 공공데이터 분류(박물관·미술관·도서관 등).
   분류 코드의 앞부분이 contentTypeId를 대신한다(A0206 = 문화시설 14, A03 = 레포츠 28, EV = 행사 15).
2. 장소 이름 낱말 (앱 규칙과 같은 목록·순서).
3. 넓은 분류: 일반 축제·행사, 카카오 '여행 > 관광,명소'처럼 무엇을 하는 곳인지 모르는 분류.
4. 서버 5분야(nature·exhibition·experience·play·pet).
5. 그 밖은 여행.

방문 기록처럼 분류 원문이 없는 예전 데이터는 2·4·5단계로 정해진다(앱의 예전 규칙과 같은 결과).
"""

import re

from app.schemas import Jelly, Theme

J = Jelly

# ── 관광공사 분류 코드 (cat3 또는 lclsSystm3). 앞에서부터 처음 맞는 접두어를 쓴다. 좁은 코드가 먼저 온다.
_TOUR_STRONG: list[tuple[str, Jelly]] = [
    ("A01010900", J.WATER),    # 계곡
    ("A01011200", J.WATER),    # 해수욕장
    ("A01", J.NATURE),         # 자연관광지 (산·숲·호수·강·공원형 자연)
    ("A0201", J.TRAVEL),       # 역사관광지 (고궁·성·문·고택·사찰)
    ("A02020200", J.TRAVEL),   # 관광단지·관광특구
    ("A02020300", J.WATER),    # 온천·욕장·스파
    ("A02020400", J.WATER),    # 이색찜질방
    ("A02020600", J.MOVE),     # 테마공원
    ("A02020700", J.NATURE),   # 공원
    ("A02020800", J.TRAVEL),   # 유람선·잠수함
    ("A0202", J.NATURE),       # 휴양관광지 (수목원·자연휴양림)
    ("A02030300", J.TRAVEL),   # 산사체험
    ("A0203", J.MAKE),         # 체험관광지 (농·산·어촌 체험, 전통체험)
    ("A02040600", J.MAKE),     # 공예·공방 산업관광
    ("A0204", J.SCIENCE),      # 산업관광지 (발전소·공장 견학)
    ("A0205", J.TRAVEL),       # 건축·조형물 (다리·전망대·탑)
    ("A02060500", J.ART),      # 미술관·화랑
    ("A02060600", J.MUSIC),    # 공연장
    ("A02060900", J.BOOK),     # 도서관
    ("A02061000", J.BOOK),     # 대형서점
    ("A02061200", J.MUSIC),    # 영화관
    ("A02060700", J.MAKE),     # 문화원 (강좌·만들기)
    ("A0206", J.SCIENCE),      # 문화시설 (박물관·기념관·전시관)
    ("A02080500", J.ART),      # 공연·행사 > 전시회
    ("A02080600", J.SCIENCE),  # 박람회
    ("A02081200", J.MOVE),     # 스포츠경기
    ("A0208", J.MUSIC),        # 공연·행사 (전통공연·연극·뮤지컬·음악회)
    ("A03021700", J.NATURE),   # 캠핑
    ("A03022700", J.NATURE),   # 트레킹·둘레길
    ("A03030500", J.NATURE),   # 낚시
    ("A0303", J.WATER),        # 수상 레포츠 (수영장·물놀이·카누)
    ("A03", J.MOVE),           # 육상·항공·복합 레포츠 (스케이트·롤러·눈썰매·클라이밍)
    # 새 분류체계 행사 (contentTypeId 15)
    ("EV0103", J.FOOD),        # 지역특산물 축제
    ("EV0105", J.NATURE),      # 자연·꽃 축제
    ("EV02", J.MUSIC),         # 공연 (국악·인형극·음악회·버스킹)
    ("EV0301", J.ART),         # 전시회
    ("EV0302", J.SCIENCE),     # 박람회
    ("EV0303", J.MOVE),        # 스포츠 행사
]
# 무엇을 하는지 이름을 봐야 아는 행사 코드: 일반 축제·기타 행사
_TOUR_WEAK: list[tuple[str, Jelly]] = [
    ("A0207", J.TRAVEL),  # 축제
    ("EV", J.TRAVEL),
]

# ── 카카오 category_name 접두어. 좁은 것이 먼저.
_KAKAO_STRONG: list[tuple[str, Jelly]] = [
    ("음식점 > 카페 > 테마카페 > 북카페", J.BOOK),
    ("음식점 > 카페 > 테마카페 > 키즈카페", J.MOVE),
    ("음식점 > 카페 > 테마카페 > 애견카페", J.ANIMAL),
    ("음식점 > 카페 > 테마카페 > 고양이카페", J.ANIMAL),
    ("음식점 > 카페 > 테마카페 > 동물카페", J.ANIMAL),
    ("음식점", J.FOOD),
    ("가정,생활 > 유아 > 놀이시설", J.MOVE),               # 키즈카페·실내놀이터
    ("교육,학문 > 유아교육 > 놀이교육", J.MOVE),           # 쇼핑몰 안 놀이 공간 (플레이타임 등)
    ("가정,생활 > 복합쇼핑몰", J.TRAVEL),                  # 몰은 몸놀이가 아니라 둘러보기
    ("가정,생활 > 백화점", J.TRAVEL),
    ("가정,생활 > 대형마트", J.TRAVEL),
    ("가정,생활 > 반려동물", J.ANIMAL),
    ("가정,생활 > 여가시설 > 보드카페", J.FRIEND),
    ("교육,학문 > 학습시설 > 도서관", J.BOOK),
    ("문화,예술 > 문화시설 > 도서관", J.BOOK),
    ("문화,예술 > 문화시설 > 미술관", J.ART),
    ("문화,예술 > 문화시설 > 아쿠아리움", J.ANIMAL),
    ("문화,예술 > 문화시설 > 공연장", J.MUSIC),
    ("문화,예술 > 문화시설 > 영화관", J.MUSIC),
    ("문화,예술 > 문화시설 > 박물관", J.SCIENCE),
    ("문화,예술 > 문화시설 > 과학관", J.SCIENCE),
    ("문화,예술 > 문화시설 > 전시관", J.SCIENCE),
    ("문화,예술 > 공연장", J.MUSIC),
    ("문화,예술 > 미술,공예", J.MAKE),                      # 공방·만들기
    ("교육,학문 > 학원 > 미술학원", J.MAKE),
    ("교육,학문 > 학원 > 요리학원", J.MAKE),
    ("교육,학문 > 학원 > 음악학원", J.MUSIC),
    ("사회,공공기관 > 단체,협회 > 사회복지시설 > 아동복지시설", J.FRIEND),  # 육아종합지원센터·어린이회관
    ("여행 > 관광,명소 > 테마파크", J.MOVE),
    ("여행 > 관광,명소 > 동물원", J.ANIMAL),
    ("여행 > 관광,명소 > 아쿠아리움", J.ANIMAL),
    ("여행 > 관광,명소 > 수목원,식물원", J.NATURE),
    ("여행 > 관광,명소 > 계곡", J.WATER),
    ("여행 > 관광,명소 > 해수욕장,해변", J.WATER),
    ("여행 > 관광,명소 > 자연휴양림", J.NATURE),
    ("여행 > 관광,명소 > 전망대", J.TRAVEL),
    ("여행 > 공원", J.NATURE),
    ("스포츠,레저 > 수영장", J.WATER),
    ("스포츠,레저 > 워터파크", J.WATER),
    ("스포츠,레저 > 수상스포츠", J.WATER),
    ("스포츠,레저 > 승마", J.ANIMAL),
    ("스포츠,레저 > 캠핑장", J.NATURE),
    ("스포츠,레저 > 낚시", J.NATURE),
    ("스포츠,레저", J.MOVE),
    ("교통,수송 > 기차,철도", J.TRAVEL),
]
_KAKAO_WEAK: list[tuple[str, Jelly]] = [
    ("문화,예술 > 문화시설", J.SCIENCE),
    ("여행 > 관광,명소", J.TRAVEL),
    ("가정,생활 > 유아", J.FRIEND),  # 문화센터·육아 모임
]

# ── 공공데이터포털 분류 (place.category에 그대로 들어간 값)
_PUBLIC_GROUPS: dict[str, Jelly] = {
    "박물관": J.SCIENCE,
    "미술관": J.ART,
    "도서관": J.BOOK,
    "자연휴양림": J.NATURE,
    "도시공원": J.NATURE,
}
# 공공데이터 문화행사 분야 ("{realmName} 행사")
_REALM_WORDS: list[tuple[tuple[str, ...], Jelly]] = [
    (("전시", "미술"), J.ART),
    (("공연", "음악", "연극", "뮤지컬", "무용", "국악", "클래식", "오페라", "콘서트", "인형극"), J.MUSIC),
    (("교육", "체험"), J.MAKE),
]

# ── 장소 이름 낱말 (앱 `JellyModel.kt`와 같은 목록·순서). 좁은 말이 넓은 말보다 먼저.
_NAME_WORDS: list[tuple[tuple[str, ...], Jelly]] = [
    (("도서관", "북카페", "서점", "책방", "그림책", "동화"), J.BOOK),
    (("워터", "물놀이", "수영장", "해수욕", "풀장", "스파", "계곡"), J.WATER),
    (("동물원", "아쿠아리움", "수족관", "목장", "농장", "동물", "애견", "반려", "펫", "승마"), J.ANIMAL),
    (("공연", "극장", "아트홀", "콘서트", "뮤지컬", "인형극", "음악", "오페라", "국악"), J.MUSIC),
    (("미술관", "갤러리", "미술", "아트", "전시"), J.ART),
    (("과학", "박물관", "천문대", "기념관", "역사관", "발명", "우주", "공룡"), J.SCIENCE),
    (("육아종합지원센터", "육아지원센터", "공동육아", "육아나눔", "문화센터", "어린이집", "키즈클럽"), J.FRIEND),
    (("클래스", "공방", "만들기", "쿠킹", "체험관", "체험장", "도예", "목공"), J.MAKE),
    (("놀이터", "키즈카페", "트램폴린", "놀이공원", "테마파크", "랜드", "방방", "볼풀", "실내놀이", "놀이"), J.MOVE),
    (("카페", "식당", "베이커리", "레스토랑", "맛집", "브런치"), J.FOOD),
    (("공원", "숲", "수목원", "생태", "휴양림", "정원", "식물원", "둘레길", "산림", "캠핑"), J.NATURE),
    (("전망대", "마을", "레일바이크", "케이블카", "유적", "한옥", "관광", "항구", "기차", "섬"), J.TRAVEL),
]

_THEME_FALLBACK: dict[str, Jelly] = {
    Theme.NATURE.value: J.NATURE,
    Theme.EXHIBITION.value: J.SCIENCE,
    Theme.EXPERIENCE.value: J.MAKE,
    Theme.PLAY.value: J.MOVE,
    Theme.PET.value: J.ANIMAL,
}

_FOOD_ROLES = ("meal", "cafe")


_TOUR_CODE = re.compile(r"^[A-Z]{1,2}\d{2,}$")  # A02060500 (cat3), EV020700 (lclsSystm3)


def _is_tour_code(category: str) -> bool:
    return bool(_TOUR_CODE.match(category))


def _by_prefix(text: str, table: list[tuple[str, Jelly]]) -> Jelly | None:
    return next((kind for prefix, kind in table if text.startswith(prefix)), None)


def _by_name(name: str) -> Jelly | None:
    compact = name.replace(" ", "")
    return next((kind for words, kind in _NAME_WORDS if any(w in compact for w in words)), None)


def _category_kind(category: str) -> tuple[Jelly | None, Jelly | None]:
    """분류 원문 → (분명한 분류, 넓은 분류)."""
    category = category.strip()
    if not category:
        return None, None
    if _is_tour_code(category):
        return _by_prefix(category, _TOUR_STRONG), _by_prefix(category, _TOUR_WEAK)
    if " > " in category or category in ("음식점", "여행", "문화,예술"):
        return _by_prefix(category, _KAKAO_STRONG), _by_prefix(category, _KAKAO_WEAK)
    if category in _PUBLIC_GROUPS:
        return _PUBLIC_GROUPS[category], None
    if category.endswith("행사"):  # 공공데이터 문화행사 "{분야} 행사"
        realm = next((kind for words, kind in _REALM_WORDS if any(w in category for w in words)), None)
        return realm, J.TRAVEL
    return None, None


def judge(name: str, category: str = "", theme: str | None = None, role: str | None = None) -> Jelly:
    """장소 하나의 젤리 12분류.

    [category]는 장소 분류 원문(관광공사 cat3·lclsSystm3, 카카오 category_name, 공공데이터 분류).
    [role]이 식사·카페이면 북카페·키즈카페·애견카페처럼 분류가 분명한 곳이 아니면 맛 탐험이다.
    """
    strong, weak = _category_kind(category)
    if strong is not None:
        return strong
    by_name = _by_name(name)
    if by_name == J.BOOK:
        return by_name
    if role in _FOOD_ROLES:
        return J.FOOD
    if by_name is not None:
        return by_name
    if weak is not None:
        return weak
    theme_value = theme.value if isinstance(theme, Theme) else theme
    return _THEME_FALLBACK.get(theme_value or "", J.TRAVEL)


# ── 화면 표기 (앱 JellyKind와 같은 이름)
# 분류 → (분류 이름, 캐릭터, 탄생한 유형 이름)
LABELS: dict[Jelly, tuple[str, str, str]] = {
    J.ART: ("전시·미술", "유니콘", "상상의 유니콘형"),
    J.SCIENCE: ("과학·박물관", "공룡", "호기심 공룡형"),
    J.NATURE: ("자연·숲", "곰", "푸근한 숲 곰형"),
    J.ANIMAL: ("동물·교감", "강아지", "다정한 강아지형"),
    J.WATER: ("물놀이", "카피바라", "느긋한 카피바라형"),
    J.MOVE: ("몸놀이", "말", "씩씩한 달리는 말형"),
    J.MUSIC: ("음악·공연", "고양이", "감성 고양이형"),
    J.BOOK: ("책·이야기", "웜뱃", "똑똑한 웜뱃형"),
    J.FOOD: ("맛 탐험", "햄스터", "미식가 햄스터형"),
    J.FRIEND: ("친구·어울림", "펭귄", "골목대장 펭귄형"),
    J.MAKE: ("만들기", "비버", "뚝딱이 비버형"),
    J.TRAVEL: ("여행", "수달", "모험가 수달형"),
}

MIN_STAY_MINUTES = 10  # 경험 1개 = 10분 이상 머문 방문 (머문 시간을 모르면 센다, 앱과 같음)
# 성장 단계: 누적 경험 1~3 방울, 4~9 몽글, 10 이상 탄생
_STAGES = ((10, "born"), (4, "puff"), (1, "drop"))

# 비어 있는 별에서 코스를 받을 때 쓸 5분야 (분야로 옮길 수 없는 분류는 없음)
TO_THEME: dict[Jelly, Theme] = {
    J.NATURE: Theme.NATURE,
    J.ANIMAL: Theme.NATURE,
    J.SCIENCE: Theme.EXHIBITION,
    J.ART: Theme.EXHIBITION,
    J.BOOK: Theme.EXHIBITION,
    J.MAKE: Theme.EXPERIENCE,
    J.MOVE: Theme.PLAY,
    J.WATER: Theme.PLAY,
}


def stage(count: int) -> str:
    """none / drop / puff / born"""
    return next((name for least, name in _STAGES if count >= least), "none")


def stage_name(kind: Jelly, count: int) -> str:
    """"곰 방울" / "곰 몽글" / "푸근한 숲 곰형" / "비어 있는 별" """
    _, character, type_name = LABELS[kind]
    return {"drop": f"{character} 방울", "puff": f"{character} 몽글", "born": type_name}.get(stage(count), "비어 있는 별")

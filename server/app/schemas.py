from enum import Enum

from pydantic import BaseModel, Field


class Theme(str, Enum):
    NATURE = "nature"        # 자연
    EXPERIENCE = "experience"  # 체험
    EXHIBITION = "exhibition"  # 전시
    PLAY = "play"            # 쇼핑·놀이 (키즈카페, 테마파크, 복합쇼핑몰)
    PET = "pet"              # 반려동물 동반


class TripConditions(BaseModel):
    """사용자 요청을 구조화한 코스 조건. 비어 있는 값은 기본값/차량 정보로 채운다."""

    child_ages: list[int] = Field(default_factory=list, description="아이 나이(만 나이, 개월 수는 0)")
    child_age_months: list[int] = Field(
        default_factory=list,
        description="아이 나이(개월). 앱의 가족 프로필에서 오며, 있으면 child_ages보다 정확하다",
    )
    adults: int | None = None
    with_pet: bool = False
    themes: list[Theme] = Field(default_factory=list)
    indoor_only: bool = False
    return_by_hour: int | None = Field(None, ge=0, le=23, description="귀가 희망 시각(시)")
    max_drive_minutes: int | None = Field(None, description="편도 최대 이동 시간(분)")
    needs: list[str] = Field(
        default_factory=list,
        description="육아 편의 조건: stroller, nursing_room, kids_chair, kids_zone, parking",
    )
    interests: list[str] = Field(
        default_factory=list,
        description="아이 관심사 한 단어씩 (예: 공룡, 기차, 동물, 우주, 물놀이)",
    )


    def youngest_months(self) -> int | None:
        """가장 어린 아이의 개월 수. 개월 정보가 없으면 만 나이로 어림한다."""
        if self.child_age_months:
            return min(self.child_age_months)
        if self.child_ages:
            return min(self.child_ages) * 12
        return None

    def has_infant(self) -> bool:
        """만 2세 이하(36개월 미만) 아이가 있는지. 실내 선호·짧은 체류 판단에 쓴다."""
        months = self.youngest_months()
        return months is not None and months < 36


class ParseRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)


class ParseResponse(BaseModel):
    conditions: TripConditions
    parser: str


class StopRole(str, Enum):
    MEAL = "meal"      # 식사
    OUTING = "outing"  # 나들이
    CAFE = "cafe"      # 카페
    HOME = "home"      # 귀가


class Amenity(str, Enum):
    """육아 편의 표시. 데이터 대부분이 자유 텍스트라 '없음'으로 단정하지 않는다."""

    AVAILABLE = "available"  # 있음
    UNKNOWN = "unknown"      # 정보 없음


class LatLng(BaseModel):
    latitude: float = Field(..., ge=33, le=39, description="위도 (국내)")
    longitude: float = Field(..., ge=124, le=132, description="경도 (국내)")


class Place(BaseModel):
    name: str
    latitude: float
    longitude: float
    address: str = ""
    category: str = ""
    source: str = Field(..., description="tour / kakao")
    source_id: str
    image_url: str = ""
    amenities: dict[str, Amenity] = Field(default_factory=dict, description="stroller, nursing_room, kids_chair, kids_zone, parking, pet(반려견 동반)")
    amenity_notes: dict[str, str] = Field(default_factory=dict, description="편의 정보 원문")
    event_period: str = Field("", description="행사·축제면 기간 (예: 2026-11-01~2026-11-10)")
    theme: Theme | None = Field(
        None,
        description="나들이 장소의 경험 분야 (TourAPI 분류코드·카카오 분류에서). 앱 경험 지도가 쓴다",
    )


class Stop(BaseModel):
    role: StopRole
    place: Place
    arrive_at: str = Field(..., description="도착 시각 HH:MM")
    depart_at: str | None = Field(None, description="출발 시각 HH:MM, 귀가는 없음")
    stay_minutes: int = 0
    drive_minutes: int = Field(..., description="이전 지점에서 오는 이동 시간(분)")
    drive_distance_m: int


class VisitSummary(BaseModel):
    """최근 방문 요약. 개인을 알 수 있는 정보 없이 장소 키·분야·날짜만 받는다."""

    place_key: str = Field("", max_length=80, description="source:source_id. 모르면 비움")
    theme: Theme | None = Field(None, description="나들이 장소의 경험 분야")
    date: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$", description="방문일 YYYY-MM-DD")


class CourseRequest(BaseModel):
    conditions: TripConditions
    origin: LatLng
    home: LatLng | None = Field(None, description="귀가 지점. 없으면 출발지")
    departure_time: str | None = Field(None, pattern=r"^\d{2}:\d{2}$", description="출발 시각 HH:MM. 없으면 지금")
    visits: list[VisitSummary] = Field(default_factory=list, max_length=200, description="최근 방문 요약. 다녀온 곳은 감점, 안 가 본 분야는 가점")
    exclude_place_keys: list[str] = Field(
        default_factory=list, max_length=200, description="이미 보여 준 나들이 장소 키. '다른 코스 보기'에서 뺀다"
    )
    seed: int | None = Field(None, description="후보 가중 선택의 시드. 같은 조건·시드면 같은 결과. 없으면 매번 다르게")


class ScheduleStop(BaseModel):
    role: StopRole
    place: Place
    stay_minutes: int | None = Field(None, ge=0, le=480, description="머무는 시간(분). 없으면 역할 기본값")


class ScheduleRequest(BaseModel):
    """장소를 바꾼 코스의 시간표만 다시 계산한다. 장소는 다시 찾지 않는다."""

    conditions: TripConditions
    origin: LatLng
    home: LatLng | None = Field(None, description="귀가 지점. 없으면 출발지")
    departure_time: str | None = Field(None, pattern=r"^\d{2}:\d{2}$", description="출발 시각 HH:MM. 없으면 지금")
    stops: list[ScheduleStop] = Field(..., min_length=1, description="귀가를 뺀 방문 순서")
    suggest_nearby: bool = Field(False, description="나들이 장소 근처 식사·카페 후보를 새로 찾아 alternatives로 준다")


class CourseResponse(BaseModel):
    departure_at: str = Field("", description="출발 시각 HH:MM")
    stops: list[Stop]
    total_drive_minutes: int
    return_at: str
    route_source: str = Field(..., description="kakao: 실제 길찾기 / estimate: 직선거리 추정")
    path: list[LatLng] = Field(default_factory=list, description="출발부터 귀가까지 지도에 그릴 경로 좌표")
    alternatives: dict[StopRole, list[Place]] = Field(default_factory=dict, description="장소 교체 후보")
    warnings: list[str] = Field(default_factory=list)


class CourseOption(BaseModel):
    """홈에 보여 줄 추천 코스 하나."""

    name: str = Field(..., description="코스 이름 (분야 · 나들이 장소)")
    summary: str = Field(..., description="경유 요약 (A → B → C)")
    total_minutes: int = Field(..., description="출발부터 귀가까지 총 시간(분)")
    first_drive_minutes: int = Field(..., description="첫 경유지까지 이동 시간(분)")
    place_key: str = Field(..., description="'지금 N대 가는 중' 집계에 쓰는 나들이 장소 키 (source:source_id)")
    indoor: bool = False
    course: CourseResponse


class SuggestResponse(BaseModel):
    options: list[CourseOption]

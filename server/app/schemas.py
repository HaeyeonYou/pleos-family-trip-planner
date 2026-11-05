from enum import Enum

from pydantic import BaseModel, Field


class Theme(str, Enum):
    NATURE = "nature"        # 자연
    EXPERIENCE = "experience"  # 체험
    EXHIBITION = "exhibition"  # 전시
    PLAY = "play"            # 놀이
    PET = "pet"              # 반려동물 동반


class TripConditions(BaseModel):
    """사용자 요청을 구조화한 코스 조건. 비어 있는 값은 기본값/차량 정보로 채운다."""

    child_ages: list[int] = Field(default_factory=list, description="아이 나이(만 나이, 개월 수는 0)")
    adults: int | None = None
    with_pet: bool = False
    themes: list[Theme] = Field(default_factory=list)
    indoor_only: bool = False
    return_by_hour: int | None = Field(None, ge=0, le=23, description="귀가 희망 시각(시)")
    max_drive_minutes: int | None = Field(None, description="편도 최대 이동 시간(분)")
    needs: list[str] = Field(
        default_factory=list,
        description="육아 편의 조건: stroller, nursing_room, kids_chair, kids_zone",
    )


class ParseRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)


class ParseResponse(BaseModel):
    conditions: TripConditions
    parser: str

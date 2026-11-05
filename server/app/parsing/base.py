from typing import Protocol

from app.schemas import TripConditions


class ConditionParser(Protocol):
    """자연어 요청을 TripConditions로 바꾸는 구현체 공통 인터페이스.

    LLM은 이 변환에만 쓰고, 운영시간·경로·에너지 검증은 코스 생성 단계의 규칙이 맡는다.
    """

    name: str

    def parse(self, text: str) -> TripConditions: ...

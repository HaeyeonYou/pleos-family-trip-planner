from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict

# 저장소 루트의 .env (server/ 의 상위)
ROOT_ENV = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=ROOT_ENV, extra="ignore")

    data_go_kr_key: str = ""
    kakao_rest_api_key: str = ""
    kma_api_hub_key: str = ""
    anthropic_api_key: str = ""

    # 자연어 → 조건 변환 방식. rule: 키워드 규칙, claude: Claude API
    parser: Literal["rule", "claude"] = "rule"
    claude_model: str = "claude-haiku-5-5"

    # 외부 API 대신 고정 데이터로 응답 (PT 현장 장애 대비)
    demo_mode: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()

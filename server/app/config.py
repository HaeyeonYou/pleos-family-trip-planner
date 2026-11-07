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

    # 자연어 → 조건 변환 방식. auto: 키가 있으면 Claude(실패 시 규칙), rule: 키워드 규칙만, claude: Claude 필수
    parser: Literal["auto", "rule", "claude"] = "auto"
    claude_model: str = "claude-haiku-5-5"

    # 외부 API 대신 고정 데이터로 응답 (PT 현장 장애 대비)
    demo_mode: bool = False

    # 회원·세션 DB. 로컬은 Homebrew PostgreSQL(소켓 인증), NAS는 Docker
    database_url: str = "postgresql+asyncpg://localhost/pleos"
    # 동시 요청 수에 맞춰 연결을 재사용한다 (풀 크기 + 넘칠 때 추가 연결)
    db_pool_size: int = 10
    db_max_overflow: int = 20

    # 폰이 접속할 서버 주소 (차 QR에 넣는다). 비우면 맥의 사설 IP:8000
    public_base_url: str = ""
    # 로그인 유지 기간, 차 QR 로그인 요청 유효 시간
    session_days: int = 90
    car_login_seconds: int = 300
    # 쿠키를 HTTPS로만 보낼지 (NAS HTTPS 배포에서 켠다)
    secure_cookies: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()

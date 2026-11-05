from fastapi import Depends, FastAPI

from app.config import Settings, get_settings
from app.parsing import get_parser
from app.schemas import ParseRequest, ParseResponse

app = FastAPI(title="오늘, 어디 가지? API", version="0.1.0")


@app.get("/health")
def health(settings: Settings = Depends(get_settings)) -> dict:
    return {
        "status": "ok",
        "parser": settings.parser,
        "demo_mode": settings.demo_mode,
        "keys": {
            "data_go_kr": bool(settings.data_go_kr_key),
            "kakao": bool(settings.kakao_rest_api_key),
            "kma": bool(settings.kma_api_hub_key),
            "anthropic": bool(settings.anthropic_api_key),
        },
    }


@app.post("/parse", response_model=ParseResponse)
def parse(req: ParseRequest, settings: Settings = Depends(get_settings)) -> ParseResponse:
    parser = get_parser(settings)
    return ParseResponse(conditions=parser.parse(req.text), parser=parser.name)

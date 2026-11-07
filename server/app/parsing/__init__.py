from functools import lru_cache

from app.config import Settings
from app.parsing.base import ConditionParser
from app.parsing.rule_based import RuleBasedParser


@lru_cache
def _claude(api_key: str, model: str) -> ConditionParser:
    from app.parsing.claude import ClaudeParser

    return ClaudeParser(api_key=api_key, model=model)


def get_parser(settings: Settings) -> ConditionParser:
    """auto(기본): 키가 있으면 Claude, 없으면 규칙. claude: 키 필수. rule: 항상 규칙."""
    if settings.parser == "claude" and not settings.anthropic_api_key:
        raise RuntimeError("PARSER=claude 이지만 ANTHROPIC_API_KEY가 비어 있습니다")
    if settings.parser in ("claude", "auto") and settings.anthropic_api_key:
        return _claude(settings.anthropic_api_key, settings.claude_model)
    return RuleBasedParser()

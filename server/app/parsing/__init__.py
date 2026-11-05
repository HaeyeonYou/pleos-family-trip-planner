from app.config import Settings
from app.parsing.base import ConditionParser
from app.parsing.rule_based import RuleBasedParser


def get_parser(settings: Settings) -> ConditionParser:
    if settings.parser == "claude":
        if not settings.anthropic_api_key:
            raise RuntimeError("PARSER=claude 이지만 ANTHROPIC_API_KEY가 비어 있습니다")
        from app.parsing.claude import ClaudeParser

        return ClaudeParser(api_key=settings.anthropic_api_key, model=settings.claude_model)
    return RuleBasedParser()

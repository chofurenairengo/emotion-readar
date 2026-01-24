class ERAException(Exception):
    """Base exception for ERA."""


class LLMError(ERAException):
    """LLM推論エラー."""


class LLMRateLimitError(LLMError):
    """レートリミットエラー."""


class LLMResponseParseError(LLMError):
    """レスポンスパースエラー."""

class CommXRException(Exception):
    """Base exception for Comm-XR."""


class LLMError(CommXRException):
    """LLM推論エラー."""


class LLMRateLimitError(LLMError):
    """レートリミットエラー."""


class LLMResponseParseError(LLMError):
    """レスポンスパースエラー."""

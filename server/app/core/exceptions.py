class ERAException(Exception):
    """Base exception for ERA."""


class LLMError(ERAException):
    """LLM推論エラー."""


class LLMRateLimitError(LLMError):
    """LLMレートリミットエラー."""


class LLMResponseParseError(LLMError):
    """LLMレスポンスパースエラー."""


class STTError(ERAException):
    """音声認識エラー."""


class SessionPermissionError(ERAException):
    """セッションへのアクセス権限がない."""

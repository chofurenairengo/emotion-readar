"""アプリケーション例外定義."""


class STTError(Exception):
    """音声認識エラー."""

    pass


class LLMError(Exception):
    """LLM推論エラー."""

    pass


class LLMRateLimitError(LLMError):
    """レートリミットエラー."""

    pass


class LLMResponseParseError(LLMError):
    """レスポンスパースエラー."""

    pass
class CommXRException(Exception):
    """Base exception for Comm-XR."""


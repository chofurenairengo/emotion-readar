# Implementation Plan: セッション所有者検証 (Issue #56)

## Overview

セッションへのアクセス権限を検証し、所有者以外のアクセスを403エラーで拒否する機能を実装する。セッション作成時にFirebase UIDを`owner_id`として記録し、すべてのセッション操作（HTTP API・WebSocket）で所有者検証を行う。

## Requirements

- セッション作成時に `owner_id`（Firebase UID）を記録
- セッション操作時に所有者を検証
- 他人のセッションへのアクセスは403エラー
- WebSocket接続でもセッション所有者を検証

## Files to Modify/Create

### Existing Files
- `server/app/models/session.py` - `owner_id` フィールドを追加
- `server/app/dto/session.py` - `owner_id` をモデル変換に追加
- `server/app/services/session_service.py` - 所有者検証ロジックを追加
- `server/app/api/routers/sessions.py` - 各エンドポイントで所有者検証を実行
- `server/app/api/routers/features.py` - セッション所有者検証を追加
- `server/app/api/routers/realtime.py` - WebSocket認証と所有者検証を追加
- `server/app/core/exceptions.py` - `SessionPermissionError` を追加
- `server/app/api/auth.py` - WebSocket用のトークン検証関数を追加

### New Files
なし

## Implementation Steps

### Phase 1: モデルとDTO更新

#### Step 1.1: Session モデルに owner_id を追加
**File**: `server/app/models/session.py`
**Action**:
- `owner_id: str` フィールドを追加

**Code Changes**:
```python
from dataclasses import dataclass
from datetime import datetime


@dataclass
class Session:
    id: str
    owner_id: str  # 追加: Firebase UID
    status: str
    started_at: datetime
    ended_at: datetime | None = None
```

#### Step 1.2: SessionResponse DTO を更新
**File**: `server/app/dto/session.py`
**Action**:
- `from_model` メソッドで `owner_id` を処理（レスポンスには含めない）

**Code Changes**:
```python
# owner_id はレスポンスに含めない（セキュリティ上）
# 変更不要だが、from_model が Session の新しいフィールドに対応していることを確認
```

### Phase 2: 例外クラス追加

#### Step 2.1: SessionPermissionError を追加
**File**: `server/app/core/exceptions.py`
**Action**:
- 所有者検証失敗時の例外クラスを追加

**Code Changes**:
```python
class SessionPermissionError(ERAException):
    """セッションへのアクセス権限がない."""
```

### Phase 3: SessionService 更新

#### Step 3.1: create_session に owner_id を追加
**File**: `server/app/services/session_service.py`
**Action**:
- `create_session` メソッドに `owner_id` パラメータを追加
- セッション作成時に `owner_id` を設定

**Code Changes**:
```python
def create_session(self, owner_id: str) -> Session:
    session_id = str(uuid.uuid4())
    session = Session(
        id=session_id,
        owner_id=owner_id,  # 追加
        status=SESSION_STATUS_ACTIVE,
        started_at=datetime.now(timezone.utc),
        ended_at=None,
    )
    self._repository.save(session)
    return session
```

#### Step 3.2: 所有者検証メソッドを追加
**File**: `server/app/services/session_service.py`
**Action**:
- `verify_owner` メソッドを追加

**Code Changes**:
```python
from app.core.exceptions import SessionPermissionError

def verify_owner(self, session_id: str, user_id: str) -> Session:
    """セッションの所有者を検証する.

    Args:
        session_id: セッションID
        user_id: 検証するユーザーID（Firebase UID）

    Returns:
        Session: 検証成功時のセッション

    Raises:
        LookupError: セッションが見つからない場合
        SessionPermissionError: 所有者でない場合
    """
    session = self._repository.get(session_id)
    if session is None:
        raise LookupError("session_not_found")
    if session.owner_id != user_id:
        raise SessionPermissionError("You don't have permission to access this session")
    return session
```

### Phase 4: HTTP API エンドポイント更新

#### Step 4.1: sessions.py - セッション作成を更新
**File**: `server/app/api/routers/sessions.py`
**Action**:
- `start_session` で `owner_id` を渡す

**Code Changes**:
```python
@router.post(
    "/sessions",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,
)
def start_session(
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> SessionResponse:
    session = session_service.create_session(owner_id=current_user["uid"])
    return SessionResponse.from_model(session)
```

#### Step 4.2: sessions.py - セッション終了に所有者検証を追加
**File**: `server/app/api/routers/sessions.py`
**Action**:
- `finish_session` で所有者検証を追加
- 403エラーハンドリングを追加

**Code Changes**:
```python
from app.core.exceptions import SessionPermissionError

@router.post(
    "/sessions/{session_id}/end",
    response_model=SessionResponse,
    status_code=status.HTTP_200_OK,
)
def finish_session(
    session_id: str,
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> SessionResponse:
    try:
        session_service.verify_owner(session_id, current_user["uid"])
    except LookupError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    except SessionPermissionError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(exc),
        )

    session = session_service.end_session(session_id)
    if session is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return SessionResponse.from_model(session)
```

#### Step 4.3: sessions.py - セッション取得に所有者検証を追加
**File**: `server/app/api/routers/sessions.py`
**Action**:
- `read_session` で所有者検証を追加

**Code Changes**:
```python
@router.get(
    "/sessions/{session_id}",
    response_model=SessionResponse,
    status_code=status.HTTP_200_OK,
)
def read_session(
    session_id: str,
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> SessionResponse:
    try:
        session = session_service.verify_owner(session_id, current_user["uid"])
    except LookupError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    except SessionPermissionError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(exc),
        )
    return SessionResponse.from_model(session)
```

#### Step 4.4: features.py - 所有者検証を追加
**File**: `server/app/api/routers/features.py`
**Action**:
- `receive_features` で所有者検証を追加

**Code Changes**:
```python
from app.core.exceptions import SessionPermissionError

@router.post(
    "/features",
    response_model=FeatureResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
def receive_features(
    payload: FeatureRequest,
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> FeatureResponse:
    # 所有者検証
    try:
        session_service.verify_owner(payload.session_id, current_user["uid"])
    except LookupError:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found",
        )
    except SessionPermissionError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(exc),
        )

    try:
        log = record_features(payload, session_service)
    except LookupError as exc:
        if str(exc) == "session_not_found":
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Session not found",
            ) from exc
        raise
    return FeatureResponse.from_model(log)
```

### Phase 5: WebSocket 認証と所有者検証

#### Step 5.1: auth.py - WebSocket用トークン検証関数を追加
**File**: `server/app/api/auth.py`
**Action**:
- WebSocket用の `verify_websocket_token` 関数を追加

**Code Changes**:
```python
def verify_websocket_token(token: str) -> dict[str, Any]:
    """WebSocket接続用のトークン検証.

    Args:
        token: Firebase ID Token

    Returns:
        dict: ユーザー情報 {"uid": str, "email": str | None}

    Raises:
        ValueError: トークンが無効または期限切れの場合
    """
    if not token:
        raise ValueError("Missing authentication token")

    try:
        decoded = verify_id_token(token)
    except Exception as exc:
        logger.warning("WebSocket token verification failed: %s", exc)
        raise ValueError("Invalid or expired token") from exc

    return {
        "uid": decoded.get("uid", ""),
        "email": decoded.get("email"),
    }
```

#### Step 5.2: realtime.py - WebSocket認証と所有者検証を追加
**File**: `server/app/api/routers/realtime.py`
**Action**:
- `token` クエリパラメータを追加
- 接続時にトークン検証とセッション所有者検証を実行
- 認証失敗時はWebSocketを閉じる

**Code Changes**:
```python
from app.api.auth import verify_websocket_token
from app.api.dependencies import get_session_service

@router.websocket("/realtime")
async def realtime(
    websocket: WebSocket,
    session_id: str = Query(...),
    token: str = Query(...),
    connection_manager: ConnectionManager = Depends(get_connection_manager),
) -> None:
    # トークン検証
    try:
        user_info = verify_websocket_token(token)
    except ValueError as exc:
        await websocket.close(code=4001, reason=str(exc))
        return

    # セッション所有者検証
    session_service = get_session_service()
    session = session_service.get_session(session_id)
    if session is None:
        await websocket.close(code=4004, reason="Session not found")
        return
    if session.owner_id != user_info["uid"]:
        await websocket.close(code=4003, reason="You don't have permission to access this session")
        return

    await connection_manager.connect(websocket, session_id)
    try:
        # ... 既存のメッセージ処理ループ ...
```

## Testing Requirements

- [ ] Unit test: `SessionService.create_session` に `owner_id` が設定されることを確認
- [ ] Unit test: `SessionService.verify_owner` が正しい所有者で成功することを確認
- [ ] Unit test: `SessionService.verify_owner` が異なる所有者で `SessionPermissionError` を発生させることを確認
- [ ] Integration test: POST `/sessions` がセッションを作成し `owner_id` を記録することを確認
- [ ] Integration test: 他人のセッションへのアクセスが403を返すことを確認
- [ ] Integration test: WebSocket接続で無効なトークンが拒否されることを確認
- [ ] Integration test: WebSocket接続で他人のセッションへのアクセスが拒否されることを確認
- [ ] Manual verification: Postmanでセッション作成→別ユーザーでアクセス→403確認

## Risks & Considerations

- **Risk**: 既存のテストが `create_session` の引数変更で失敗する可能性 → **Mitigation**: テストも同時に更新
- **Risk**: WebSocketの認証がトークン漏洩に繋がる可能性（URLにトークンが含まれる） → **Mitigation**: 本番環境ではTLS必須、ログからトークンを除外

## Success Criteria

- [ ] セッション作成時に `owner_id` が保存される
- [ ] 所有者以外が `GET /sessions/{id}` にアクセスすると403が返る
- [ ] 所有者以外が `POST /sessions/{id}/end` にアクセスすると403が返る
- [ ] 所有者以外が `POST /features` にアクセスすると403が返る
- [ ] 所有者以外がWebSocket `/realtime` に接続すると4003で切断される
- [ ] 無効なトークンでWebSocket接続すると4001で切断される

## Error Response Format

非所有者アクセス時:
```json
{"detail": "You don't have permission to access this session"}
```
ステータスコード: 403

WebSocket切断コード:
- `4001`: 認証エラー（無効なトークン）
- `4003`: 権限エラー（所有者でない）
- `4004`: セッションが見つからない

# /update-spec - 仕様書自動更新

コードベースの変更に基づいて仕様書を自動更新します。

## 使い方

```
@workspace /update-spec
@workspace /update-spec server   # サーバー仕様のみ更新
@workspace /update-spec api      # API仕様のみ更新
@workspace /update-spec models   # データモデル仕様のみ更新
@workspace /update-spec services # サービス層仕様のみ更新
```

## 処理フロー

### Step 1: コード解析

以下のファイルを読み取り、現在の実装を把握します：

- **server**: `server/app/core/config.py`, `server/app/core/exceptions.py`
- **api**: `server/app/api/routers/*.py`
- **models**: `server/app/dto/*.py`, `server/app/models/*.py`
- **services**: `server/app/services/*.py`

### Step 2: 仕様書との差分検出

現在の仕様書を読み取り、実装との差分を検出します：

- `docs/spec/server.md` - ディレクトリ構成、環境変数、例外階層
- `docs/spec/api.md` - REST API/WebSocket API
- `docs/spec/models.md` - DTO定義、ドメインモデル
- `docs/spec/services.md` - サービスのメソッド、処理フロー

### Step 3: 仕様書更新

検出された差分に基づき、該当する仕様ファイルを更新します。

**更新ルール:**
1. 既存の仕様書のフォーマットを維持する
2. コードから抽出した型情報を正確に反映する
3. 日本語で記述する
4. コード内のdocstringやコメントを参考にする

### Step 4: 結果報告

更新内容をサマリーとして報告します。

## 出力形式

```
📋 仕様書更新レポート

### 検出された変更:
- [api] 新しいエンドポイント追加: POST /api/sessions/{id}/pause
- [models] 新しいDTO追加: PauseRequest
- [services] メソッド追加: SessionService.pause()

### 更新されたファイル:
- docs/spec/api.md (エンドポイント追加)
- docs/spec/models.md (DTO追加)
- docs/spec/services.md (メソッド追加)

✅ 仕様書の更新が完了しました
```

## 重要なルール

1. **仕様のみを更新** - コードは変更しない
2. **差分のみを更新** - 全体を書き換えない
3. **確認が必要な場合は質問** - 不明な変更は保留する
4. **バックアップは取らない** - Gitで管理されている前提

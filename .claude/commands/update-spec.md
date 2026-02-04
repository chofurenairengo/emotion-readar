# /update-spec - 仕様書自動更新コマンド

コードベースの変更に基づいて仕様書を自動更新します。

## 使用方法

```
/update-spec
/update-spec server   # サーバー仕様のみ更新
/update-spec api      # API仕様のみ更新
/update-spec models   # データモデル仕様のみ更新
/update-spec services # サービス層仕様のみ更新
```

## 処理フロー

### 1. コード解析

以下のファイルを読み取り、現在の実装を把握します：

| 対象仕様 | 解析対象ファイル |
|---------|----------------|
| server  | `server/app/core/config.py`, `server/app/core/exceptions.py` |
| api     | `server/app/api/routers/*.py` |
| models  | `server/app/dto/*.py`, `server/app/models/*.py` |
| services| `server/app/services/*.py` |

### 2. 差分検出

現在の仕様書と実装を比較し、以下を検出します：
- 新しいエンドポイント
- 新しいDTO/モデル
- 新しいサービス/メソッド
- 変更されたパラメータ
- 削除された機能

### 3. 仕様書更新

検出された差分に基づき、以下のファイルを更新します：

| 仕様ファイル | 更新内容 |
|------------|---------|
| `docs/spec/server.md` | ディレクトリ構成、環境変数、例外階層 |
| `docs/spec/api.md` | REST API/WebSocket APIのエンドポイント、リクエスト/レスポンス形式 |
| `docs/spec/models.md` | DTO定義、ドメインモデル |
| `docs/spec/services.md` | サービスのメソッド、処理パイプライン |

### 4. SPECIFICATION.md同期

分割ファイルの要約を `SPECIFICATION.md` に反映します。

## 更新ルール

1. **既存形式の維持**: 既存の仕様書のフォーマットを維持
2. **型情報の正確性**: コードから抽出した型情報を正確に反映
3. **日本語での記述**: 仕様書は日本語で記述
4. **コメントの活用**: コード内のdocstringやコメントを参考にする

## 出力例

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
- SPECIFICATION.md (要約更新)

✅ 仕様書の更新が完了しました
```

## 注意事項

- 自動更新された内容は必ず目視で確認してください
- 実装の意図が不明な場合は、更新を保留してユーザーに確認します
- 大幅な構造変更がある場合は、手動での調整が必要な場合があります

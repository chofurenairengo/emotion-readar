import vertexai
from vertexai.generative_ai import tuning
from vertexai.generative_ai import GenerativeModel

# === 設定項目 ===
PROJECT_ID = "YOUR_PROJECT_ID"  # あなたのプロジェクトID
LOCATION = "us-central1"        # 学習ジョブを投げるリージョン
# チューニング済みのデータセット（GCSパス）
TRAINING_DATA_URI = "gs://your-bucket-name/training_data_gemini_formatted.jsonl"

def main():
    # Vertex AIの初期化
    vertexai.init(project=PROJECT_ID, location=LOCATION)

    print("チューニングジョブを送信中...")
    
    # チューニングジョブの作成
    # 依存関係は uv によって管理されているため、ライブラリのインポートエラーは起きません
    tuning_job = tuning.train(
        source_model="gemini-2.0-flash-002",
        train_dataset=TRAINING_DATA_URI,
        tuned_model_display_name="my_psychology_model_v1",
        epochs=4,
        learning_rate_multiplier=1.0,
        adapter_size=16, # LoRAのアダプターサイズ（4, 8, 16など）
    )

    print(f"ジョブが開始されました: {tuning_job.resource_name}")
    print("完了まで待機します...")

    # 完了までブロック（ターミナルを閉じずに待つ場合）
    tuning_job.result() 

    print("チューニング完了！")
    print(f"モデル名: {tuning_job.tuned_model_name}")
    print(f"エンドポイント: {tuning_job.tuned_model_endpoint_name}")

if __name__ == "__main__":
    main()
import json
import random

input_file = 'training_data_gemini_formatted.jsonl'
train_output = 'train_split.jsonl'
val_output = 'val_split.jsonl'

# 1. 全データを読み込む
with open(input_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# 2. データをシャッフルする（偏りを防ぐため）
random.seed(42) # 再現性を保つための固定値
random.shuffle(lines)

# 3. 分割（例：全体の10%を検証用にする）
split_index = int(len(lines) * 0.9)
train_lines = lines[:split_index]
val_lines = lines[split_index:]

# 4. 保存
with open(train_output, 'w', encoding='utf-8') as f:
    f.writelines(train_lines)
with open(val_output, 'w', encoding='utf-8') as f:
    f.writelines(val_lines)

print(f"トレーニング用: {len(train_lines)}件 / 検証用: {len(val_lines)}件 に分割しました。")
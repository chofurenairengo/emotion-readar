import json

# 元のファイル名と出力ファイル名
input_file = 'training_data_v2.jsonl'
output_file = 'training_data_gemini_formatted.jsonl'

def convert_to_gemini_format(input_path, output_path):
    with open(input_path, 'r', encoding='utf-8') as infile, \
         open(output_path, 'w', encoding='utf-8') as outfile:
        
        for line in infile:
            if not line.strip(): continue
            
            # 元のデータを読み込む
            original_data = json.loads(line)
            
            # Gemini形式へ変換
            gemini_data = {
                "contents": [
                    {
                        "role": "user",
                        "parts": [{"text": original_data["input"]}]
                    },
                    {
                        "role": "model",
                        "parts": [{"text": original_data["output"]}]
                    }
                ]
            }
            
            # 書き込み
            outfile.write(json.dumps(gemini_data, ensure_ascii=False) + '\n')

    print(f"変換が完了しました: {output_file}")

# 実行（実際の環境で実行してください）
convert_to_gemini_format(input_file, output_file)   # 実行

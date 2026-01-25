using System.Collections;
using System;
using System.Collections.Generic;
using UnityEngine;
using Newtonsoft.Json;

/*
ファイル名とクラス名は必ずしも一致しなくても良い！
こんな感じのjsonが送信されるよ。
 {
    "type": "face",
    "timestampMs": 123456789,
    "blendshapes": [
      { "name": "mouthSmileLeft", "score": 0.123 },
      { "name": "eyeBlinkRight", "score": 0.456 }
    ],
    "landmarks": [
      [0.5, 0.5, -0.01],
      [0.52, 0.48, -0.02]
    ]
  }
typeは固定でface
timestampMsは時刻
blendshapesは名前とそのスコアの配列
landmarksは[x, y, z] の配列
*/

/*
大まかな流れ

WebSocketで文字（JSON）を受け取る

Newtonsoft.Json で FaceMessage に変換

landmarks を Vector3[] に変換（Unityで使いやすくする）

blendshapes を辞書に変換（名前で引けるようにする）

FaceFrame にまとめて保存
*/


[Serializable]
public class FaceMessage
{
    [JsonProperty("type")] public string Type;
    [JsonProperty("timestampMs")] public long TimestampMs;
    //long型とは、64ビット符号付き整数型のこと。用は、-2^63から2^63-1までの整数を扱える
    [JsonProperty("blendshapes")] public List<BlendshapeItem> Blendshapes;
    //この変数の型はList<BlendshapeItem>。BlendshapeItemは下で定義されているクラス
    [JsonProperty("landmarks")] public List<List<float>> Landmarks;
    //最初のはラベルでjsonのどの名前と対応してるよ、みたいな。
    //変数のかたはfloatのリストのリスト。floatのリストってのが少数が並んでる箱、さらにその箱がもっとあるよ。的な。
    //Vector3型は使えない。jsonに対応してないから。


    //JsonUtilityはList<List<float>>を扱えないので、Newtonsoft.Jsonを使う。
}

[Serializable]
public class BlendshapeItem
{
    [JsonProperty("name")] public string Name;
    [JsonProperty("score")] public float Score;
}

public class FaceFrame
{
    public long TimestampMs;
    public Dictionary<string, float> BlendshapeMap;
    //これは辞書型。キーがstring、値がfloatのペアを複数持てる。
    /*

    例
    "mouthSmileLeft" → 0.123
    "eyeBlinkLeft"   → 0.045
    なら、BlendshapeMap["mouthSmileLeft"]とすると0.123が取れる。

    */

    public Vector3[] Landmarks;

    public FaceFrame(long timestampMs, Dictionary<string, float> blendshapeMap, Vector3[] landmarks)
    {
        TimestampMs = timestampMs;
        BlendshapeMap = blendshapeMap;
        Landmarks = landmarks;
    }
    //コンストラクタと呼ばれていて、関数ではない。作り方みたいな感じ。
    //public long TimestampMs　= timestampMs;ってやればいいじゃん、って思うけど、そうはいかない。
    //classは設計図で、まだtimestampMs って値は無い。classを作る瞬間にコンストラクタで値を入れてる。
}

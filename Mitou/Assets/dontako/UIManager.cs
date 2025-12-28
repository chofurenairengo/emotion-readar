using UnityEngine;
using UnityEngine.UI;
using System;

public class UI : MonoBehaviour
{
    public TestChat testChat;
    public InputField partnerInput;
    public Button requestButton;
    public Text resultText1;
    public Text resultText2;

    [Header("Long text split settings")]
    [SerializeField] private int splitLength = 700;   // エラー文をText1/Text2に分ける境界
    [SerializeField] private int clipLength = 6000;   // 1つのTextに入れる最大文字数（重い時の保険）

    public void OnClick()
    {
        string partnerUtterance = partnerInput.text;

        requestButton.interactable = false;
        SetTexts("Loading...", "Loading...");

        try
        {
            testChat.RequestChoices(partnerUtterance, (choices, error) =>
            {
                requestButton.interactable = true;

                // errorが string の場合を想定（もし Exception なら下の分岐で拾う）
                if (error != null)
                {
                    ShowError(error);
                    return;
                }

                // choicesの安全対策
                if (choices == null || choices.Length == 0)
                {
                    ShowError("choices is null or empty.");
                    return;
                }

                string c1 = choices.Length >= 1 ? choices[0] : "";
                string c2 = choices.Length >= 2 ? choices[1] : "(choice[1] is missing)";

                SetTexts(Clip(c1), Clip(c2));
            });
        }
        catch (Exception ex)
        {
            // コールバック到達前に例外が起きた場合も拾う
            Debug.LogException(ex);
            ShowError(ex.ToString());
            requestButton.interactable = true;
        }
    }

    private void ShowError(object errorObj)
    {
        // 文字列化（ExceptionでもToStringで全文出る）
        string msg = errorObj != null ? errorObj.ToString() : "(null error)";

        // Consoleに必ず出す（これで原因追える）
        Debug.LogError(msg);

        // 長文を2つに分割してTextへ
        string full = "Error: " + msg;

        if (full.Length <= splitLength)
        {
            SetTexts(Clip(full), "");
        }
        else
        {
            string part1 = full.Substring(0, splitLength);
            string part2 = full.Substring(splitLength);
            SetTexts(Clip(part1), Clip(part2));
        }
    }

    private void SetTexts(string t1, string t2)
    {
        resultText1.text = t1;
        resultText2.text = t2;
    }

    private string Clip(string s)
    {
        if (string.IsNullOrEmpty(s)) return s;
        if (s.Length <= clipLength) return s;
        return s.Substring(0, clipLength) + "\n...(clipped)";
    }
}

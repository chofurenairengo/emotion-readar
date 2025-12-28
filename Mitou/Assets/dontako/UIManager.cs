using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

public class UI : MonoBehaviour
{
    public TestChat testChat;
    public InputField partnerInput;
    public Button requestButton;
    public Text resultText1;
    public Text resultText2;

    // Start is called before the first frame update
    void Start()
    {
        
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    public void OnClick()
    {
        string partnerUtterance = partnerInput.text;
        requestButton.interactable = false;
        resultText1.text = "Loading...";
        resultText2.text = "Loading...";

        testChat.RequestChoices(partnerUtterance, (choices, error) =>
        {
            requestButton.interactable = true;
            if (error != null)
            {
                resultText1.text = "Error: " + error;
                resultText2.text = "";
            }
            else
            {
                resultText1.text = choices[0];
                resultText2.text = choices[1];
            }
        });

    }
}

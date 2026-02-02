using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class KotlinBridge : MonoBehaviour
{
    void Start()
    {
        Debug.Log("[KotlinBridge] Start() called -> CallKotlin()");
        CallKotlin();
    }

    public void CallKotlin()
    {
        Debug.Log("[KotlinBridge] Calling PluginBridge.start()");
        using var plugin = new AndroidJavaClass("com.era.unityplugin.PluginBridge");
        plugin.CallStatic("start");
        Debug.Log("[KotlinBridge] PluginBridge.start() invoked");
    }
}

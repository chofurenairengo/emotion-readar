package com.commuxr.android.core.ui.component

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * パーミッションが必要なコンテンツをラップするComposable
 *
 * @param permission 必要なパーミッション（例: Manifest.permission.CAMERA）
 * @param rationaleMessage パーミッションが必要な理由を説明するメッセージ
 * @param content パーミッション許可後に表示するコンテンツ
 */
@Composable
fun RequirePermission(
    permission: String,
    rationaleMessage: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    if (hasPermission) {
        content()
    } else {
        PermissionRequestContent(
            rationaleMessage = rationaleMessage,
            onRequestPermission = { launcher.launch(permission) }
        )
    }
}

@Composable
private fun PermissionRequestContent(
    rationaleMessage: String,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = rationaleMessage)
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = "許可する")
        }
    }
}

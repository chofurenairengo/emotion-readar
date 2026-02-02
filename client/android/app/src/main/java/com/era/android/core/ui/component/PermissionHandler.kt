package com.era.android.core.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * 単一の権限を要求し、許可状態に応じてUIを切り替えるコンポーザブル
 *
 * @param permission 要求する権限（例: Manifest.permission.CAMERA）
 * @param rationaleMessage 権限が必要な理由を説明するメッセージ
 * @param onGranted 権限が許可された場合に表示するUI
 * @param onDenied 権限が拒否された場合に表示するUI（デフォルト: DefaultDeniedContent）
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequirePermission(
    permission: String,
    rationaleMessage: String,
    onGranted: @Composable () -> Unit,
    onDenied: @Composable () -> Unit = { DefaultDeniedContent(rationaleMessage) }
) {
    val permissionState = rememberPermissionState(permission)

    when {
        permissionState.status.isGranted -> {
            onGranted()
        }
        else -> {
            PermissionRequestContent(
                permissionState = permissionState,
                rationaleMessage = rationaleMessage,
                onDenied = onDenied
            )
        }
    }
}

/**
 * 複数の権限を要求し、全て許可された場合にUIを表示するコンポーザブル
 *
 * @param permissions 要求する権限のリスト
 * @param rationaleMessage 権限が必要な理由を説明するメッセージ
 * @param onAllGranted 全ての権限が許可された場合に表示するUI
 * @param onDenied 一部または全ての権限が拒否された場合に表示するUI
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequirePermissions(
    permissions: List<String>,
    rationaleMessage: String,
    onAllGranted: @Composable () -> Unit,
    onDenied: @Composable (List<String>) -> Unit = { deniedPermissions ->
        DefaultDeniedContent(
            rationaleMessage = rationaleMessage,
            deniedPermissions = deniedPermissions
        )
    }
) {
    val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

    when {
        multiplePermissionsState.allPermissionsGranted -> {
            onAllGranted()
        }
        else -> {
            val deniedPermissions = multiplePermissionsState.permissions
                .filter { !it.status.isGranted }
                .map { it.permission }

            LaunchedEffect(Unit) {
                multiplePermissionsState.launchMultiplePermissionRequest()
            }

            onDenied(deniedPermissions)
        }
    }
}

/**
 * 権限リクエストのUIを表示するコンポーザブル
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionRequestContent(
    permissionState: PermissionState,
    rationaleMessage: String,
    onDenied: @Composable () -> Unit
) {
    LaunchedEffect(Unit) {
        permissionState.launchPermissionRequest()
    }

    if (!permissionState.status.isGranted) {
        onDenied()
    }
}

/**
 * 権限拒否時のデフォルトUI
 * rationaleMessageと「設定を開く」ボタンを表示
 *
 * @param rationaleMessage 権限が必要な理由を説明するメッセージ
 * @param deniedPermissions 拒否された権限のリスト（オプション）
 */
@Composable
fun DefaultDeniedContent(
    rationaleMessage: String,
    deniedPermissions: List<String> = emptyList()
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "権限が必要です",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = rationaleMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        if (deniedPermissions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "拒否された権限: ${deniedPermissions.size}件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ) {
            Text("設定を開く")
        }
    }
}

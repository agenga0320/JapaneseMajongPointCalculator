package com.mahjong.score.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mahjong.score.BuildConfig
import com.mahjong.score.GameViewModel

/** 新版本提示，以及手動檢查更新的結果 */
@Composable
fun UpdateDialogs(vm: GameViewModel) {
    val uriHandler = LocalUriHandler.current

    vm.availableUpdate?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = { Text("有新版本 ${r.tag}") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("目前版本：v${BuildConfig.VERSION_NAME}")
                    if (r.notes.isNotBlank()) {
                        Text(
                            r.notes.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { uriHandler.openUri(r.url) }
                    vm.dismissUpdate()
                }) { Text("前往下載") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::ignoreUpdate) { Text("忽略此版本") }
                    TextButton(onClick = vm::dismissUpdate) { Text("稍後") }
                }
            },
        )
    }

    vm.updateMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearUpdateMessage,
            title = { Text("檢查更新") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearUpdateMessage) { Text("確定") } },
        )
    }
}

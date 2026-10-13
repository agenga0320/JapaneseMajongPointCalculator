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
import com.mahjong.score.UpdateResult

/** 新版本提示，以及手動檢查更新的結果 */
@Composable
fun UpdateDialogs(vm: GameViewModel) {
    val uriHandler = LocalUriHandler.current
    val version = "v${BuildConfig.VERSION_NAME}"

    vm.availableUpdate?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = { Text(tr("有新版本 ${r.tag}", "新しいバージョン ${r.tag} があります")) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(tr("目前版本：$version", "現在のバージョン：$version"))
                    Text(
                        tr(
                            "下載完成後開啟 APK 即可覆蓋安裝，紀錄會保留。",
                            "ダウンロード後に APK を開くと上書きインストールされます（記録は保持されます）。",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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
                }) { Text(tr("下載 APK", "APK をダウンロード")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::ignoreUpdate) { Text(tr("忽略此版本", "このバージョンを無視")) }
                    TextButton(onClick = vm::dismissUpdate) { Text(tr("稍後", "後で")) }
                }
            },
        )
    }

    vm.updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = vm::clearUpdateResult,
            title = { Text(tr("檢查更新", "更新を確認")) },
            text = {
                Text(
                    when (result) {
                        UpdateResult.UP_TO_DATE -> tr("目前已是最新版本（$version）", "最新バージョンです（$version）")
                        UpdateResult.FAILED -> tr(
                            "無法取得版本資訊，請確認網路連線",
                            "バージョン情報を取得できませんでした。ネットワーク接続を確認してください",
                        )
                    }
                )
            },
            confirmButton = { TextButton(onClick = vm::clearUpdateResult) { Text(tr("確定", "OK")) } },
        )
    }
}

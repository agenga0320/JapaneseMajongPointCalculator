package com.mahjong.score.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mahjong.score.BuildConfig
import com.mahjong.score.DownloadState
import com.mahjong.score.GameViewModel
import com.mahjong.score.UpdateResult
import com.mahjong.score.data.ApkInstaller

/** 新版本提示（App 內下載並安裝），以及手動檢查更新的結果 */
@Composable
fun UpdateDialogs(vm: GameViewModel) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = "v${BuildConfig.VERSION_NAME}"

    vm.availableUpdate?.let { r ->
        val state = vm.download
        // 已引導使用者去設定允許安裝時顯示提示
        var askedPermission by remember(r.tag) { mutableStateOf(false) }

        fun install(done: DownloadState.Done) {
            if (ApkInstaller.canInstall(context)) {
                runCatching { ApkInstaller.install(context, done.file) }
            } else {
                askedPermission = true
                runCatching { ApkInstaller.openPermissionSettings(context) }
            }
        }

        fun openInBrowser() = runCatching { uriHandler.openUri(r.apkUrl ?: r.pageUrl) }

        AlertDialog(
            // 下載中不因點到外面而關閉
            onDismissRequest = { if (state !is DownloadState.Downloading) vm.dismissUpdate() },
            title = { Text(tr("有新版本 ${r.tag}", "新しいバージョン ${r.tag} があります")) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(tr("目前版本：$version", "現在のバージョン：$version"))
                    when (state) {
                        is DownloadState.Downloading -> {
                            Text(
                                tr("下載中… ${(state.progress * 100).toInt()}%", "ダウンロード中… ${(state.progress * 100).toInt()}%"),
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            )
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        is DownloadState.Done -> {
                            Text(
                                if (askedPermission) tr(
                                    "請在設定中允許「安裝不明應用程式」，返回後再按一次「安裝」。",
                                    "設定で「不明なアプリのインストール」を許可してから戻り、もう一度「インストール」を押してください。",
                                ) else tr(
                                    "下載完成。按「安裝」即可覆蓋更新，紀錄會保留。",
                                    "ダウンロード完了。「インストール」で上書き更新します（記録は保持されます）。",
                                ),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        DownloadState.Failed -> {
                            Text(
                                tr("下載失敗，請確認網路連線後重試。", "ダウンロードに失敗しました。接続を確認して再試行してください。"),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        DownloadState.Idle -> {
                            if (r.notes.isNotBlank()) {
                                Text(
                                    r.notes.trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when (state) {
                    DownloadState.Idle -> TextButton(onClick = {
                        if (r.apkUrl != null) vm.startDownload() else openInBrowser()
                    }) {
                        Text(
                            if (r.apkUrl != null) tr("下載並安裝", "ダウンロードしてインストール")
                            else tr("前往下載", "ダウンロードページへ")
                        )
                    }
                    is DownloadState.Downloading -> TextButton(onClick = vm::cancelDownload) {
                        Text(tr("取消", "キャンセル"))
                    }
                    is DownloadState.Done -> TextButton(onClick = { install(state) }) {
                        Text(tr("安裝", "インストール"))
                    }
                    DownloadState.Failed -> TextButton(onClick = vm::startDownload) {
                        Text(tr("重試", "再試行"))
                    }
                }
            },
            dismissButton = {
                when (state) {
                    DownloadState.Idle -> Row {
                        TextButton(onClick = vm::ignoreUpdate) { Text(tr("忽略此版本", "このバージョンを無視")) }
                        TextButton(onClick = vm::dismissUpdate) { Text(tr("稍後", "後で")) }
                    }
                    is DownloadState.Downloading -> Unit
                    is DownloadState.Done -> TextButton(onClick = vm::dismissUpdate) { Text(tr("稍後", "後で")) }
                    DownloadState.Failed -> Row {
                        TextButton(onClick = { openInBrowser() }) { Text(tr("用瀏覽器下載", "ブラウザで開く")) }
                        TextButton(onClick = vm::dismissUpdate) { Text(tr("稍後", "後で")) }
                    }
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

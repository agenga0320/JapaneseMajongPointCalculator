package com.mahjong.score.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mahjong.score.data.RecordShare
import com.mahjong.score.model.Game
import kotlinx.coroutines.launch

/** 頂部列的分享按鈕：選擇以 PDF 或圖片分享半莊紀錄 */
@Composable
fun ShareRecordAction(game: Game) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val chooserTitle = tr("分享半莊紀錄", "半荘記録を共有")

    IconButton(onClick = { menu = true }, enabled = !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Filled.Share, contentDescription = tr("分享", "共有"))
    }

    fun run(action: suspend () -> Unit) {
        menu = false
        busy = true
        scope.launch {
            failed = runCatching { action() }.isFailure
            busy = false
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(chooserTitle) },
            text = {
                Column {
                    OutlinedButton(
                        onClick = { run { RecordShare.sharePdf(context, game, lang, chooserTitle) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("PDF（LINE・微信 等）", "PDF（LINE・WeChat など）")) }
                    OutlinedButton(
                        onClick = { run { RecordShare.shareImages(context, game, lang, chooserTitle) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(tr("圖片（Instagram 等）", "画像（Instagram など）")) }
                    Text(
                        tr(
                            "Instagram 不支援 PDF，請選擇圖片（每頁一張）。",
                            "Instagram は PDF に対応していないため、画像（1ページ1枚）を選んでください。",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text(tr("取消", "キャンセル")) } },
        )
    }
    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            title = { Text(chooserTitle) },
            text = { Text(tr("產生檔案失敗，請再試一次。", "ファイルの作成に失敗しました。もう一度お試しください。")) },
            confirmButton = { TextButton(onClick = { failed = false }) { Text(tr("確定", "OK")) } },
        )
    }
}

package com.mahjong.score.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 產生半莊紀錄的 PDF／圖片並開啟系統分享選單 */
object RecordShare {
    private fun shareDir(context: Context) = File(context.cacheDir, "share").apply {
        deleteRecursively() // 只保留這次分享的檔案
        mkdirs()
    }

    private fun baseName(game: Game) =
        "mahjong-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(game.startedAt))

    private fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun sharePdf(context: Context, game: Game, lang: AppLang, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            File(shareDir(context), baseName(game) + ".pdf").also { RecordReport(game, lang).writePdf(it) }
        }
        val uri = uriOf(context, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    suspend fun shareImages(context: Context, game: Game, lang: AppLang, chooserTitle: String) {
        val files = withContext(Dispatchers.IO) {
            RecordReport(game, lang).writePngs(shareDir(context), baseName(game))
        }
        val uris = files.map { uriOf(context, it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.setType("image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}

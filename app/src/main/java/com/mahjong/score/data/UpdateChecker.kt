package com.mahjong.score.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** apkUrl：Release 附加的本 App APK 直接下載連結（沒有附、或連結不可信時為 null） */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String,
)

/** 以 GitHub Releases 的最新版本檢查更新 */
object UpdateChecker {
    const val REPO = "agenga0320/JapaneseMajongPointCalculator"
    const val RELEASES_URL = "https://github.com/$REPO/releases/latest"
    private const val DOWNLOAD = "https://github.com/$REPO/releases/download/"
    const val MIME = "application/vnd.android.package-archive"

    /** 每個 Release 必須附上的 APK 檔名（release.bat 就是用這個名字上傳） */
    fun apkName(version: String) = "JapaneseMahjongPointCalculator-v$version.apk"

    /** 只有本 repo、該版本 tag、指定檔名的 APK 才會被下載並交給安裝程式 */
    fun isTrusted(version: String, url: String) =
        Regex("""\d+\.\d+\.\d+""").matches(version) && url == "${DOWNLOAD}v$version/${apkName(version)}"

    /** 取得最新 release；離線或尚無 release 時回傳 null */
    fun fetchLatest(): ReleaseInfo? = runCatching {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            val version = tag.trimStart('v', 'V')
            val assets = o.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name") == apkName(version) && it.optString("state") == "uploaded" }
                ?.optString("browser_download_url")
                ?.takeIf { isTrusted(version, it) }
            ReleaseInfo(
                tag = tag,
                version = version,
                pageUrl = o.optString("html_url", RELEASES_URL),
                apkUrl = apkUrl,
                notes = o.optString("body", ""),
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** "1.2.0" 與 "1.10" 等以數字逐段比較 */
    fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * 用系統的下載管理員下載新版 APK：在背景進行、有通知，關掉 App 也會繼續。
 * 只記住一個進行中的下載（id、網址、版本存在 SharedPreferences）。
 */
class UpdateDownload(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("apk_downloads", Context.MODE_PRIVATE)

    val id: Long get() = prefs.getLong("id", -1)
    val url: String get() = prefs.getString("url", "")!!
    val version: String get() = prefs.getString("version", "")!!

    /** 已安裝的版本追上下載的版本後，檔案就不需要了 */
    fun cleanUpIfInstalled(installed: String) {
        if (version.isNotEmpty() && !UpdateChecker.isNewer(version, installed)) {
            if (id >= 0) manager.remove(id)
            prefs.edit().clear().apply()
        }
    }

    /** status 為 DownloadManager.STATUS_*；找不到這個下載時為 -1 */
    class Progress(val status: Int, val bytes: Long, val total: Long) {
        val active: Boolean
            get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING ||
                status == DownloadManager.STATUS_PAUSED
    }

    fun progress(id: Long = this.id): Progress {
        if (id >= 0) {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) {
                    return Progress(
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    )
                }
            }
        }
        return Progress(-1, 0, -1)
    }

    /** 這個版本的下載是否已在進行中或已完成 */
    fun isKnown(apkUrl: String): Boolean {
        val s = progress().status
        return apkUrl == url && s != -1 && s != DownloadManager.STATUS_FAILED
    }

    /** 開始下載；同一版本已在下載或已下載好時沿用 */
    fun enqueue(version: String, apkUrl: String, notification: String): Long {
        require(UpdateChecker.isTrusted(version, apkUrl))
        val previous = id
        if (isKnown(apkUrl)) return previous
        val file = UpdateChecker.apkName(version)
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(file)
            .setDescription(notification)
            .setMimeType(UpdateChecker.MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "${System.currentTimeMillis()}-$file")
        val newId = manager.enqueue(request)
        prefs.edit().putLong("id", newId).putString("url", apkUrl).putString("version", version).apply()
        if (previous >= 0) manager.remove(previous)
        return newId
    }

    fun cancel() {
        if (id >= 0) manager.remove(id)
        prefs.edit().clear().apply()
    }

    fun downloaded(id: Long = this.id): Uri? = manager.getUriForDownloadedFile(id)
}

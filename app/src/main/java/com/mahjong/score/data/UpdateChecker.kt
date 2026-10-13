package com.mahjong.score.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** apkUrl：Release 附加的 APK 直接下載連結（沒有附 APK 時為 null） */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String,
)

/** 以 GitHub Releases 的最新版本檢查更新，並下載 APK */
object UpdateChecker {
    const val REPO = "agenga0320/JapaneseMajongPointCalculator"
    const val RELEASES_URL = "https://github.com/$REPO/releases/latest"

    /** 取得最新 release；離線或尚無 release 時回傳 null */
    fun fetchLatest(): ReleaseInfo? = runCatching {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            val assets = o.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotEmpty() }
            ReleaseInfo(
                tag = tag,
                version = tag.trimStart('v', 'V'),
                pageUrl = o.optString("html_url", RELEASES_URL),
                apkUrl = apkUrl,
                notes = o.optString("body", ""),
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * 下載 APK 到 dest。onProgress 收到 0..1 的進度（伺服器未提供大小時不呼叫）。
     * 失敗時丟出例外；協程取消時中止下載。
     */
    suspend fun downloadApk(url: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true // GitHub 會轉址到檔案伺服器
        val tmp = File(dest.path + ".part")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPct = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
            dest.delete()
            if (!tmp.renameTo(dest)) error("rename failed")
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

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

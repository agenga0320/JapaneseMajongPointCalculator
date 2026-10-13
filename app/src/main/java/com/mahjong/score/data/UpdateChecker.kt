package com.mahjong.score.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** url：APK 的直接下載連結；release 沒有附 APK 時為 Release 頁面 */
data class ReleaseInfo(val tag: String, val version: String, val url: String, val notes: String)

/** 以 GitHub Releases 的最新版本檢查更新 */
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
                url = apkUrl ?: o.optString("html_url", RELEASES_URL),
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

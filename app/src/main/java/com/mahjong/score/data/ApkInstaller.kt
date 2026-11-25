package com.mahjong.score.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 開啟系統安裝程式安裝下載好的 APK */
object ApkInstaller {
    /** 使用者是否已允許本 App 安裝其他應用程式 */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** 開啟「安裝不明應用程式」的設定頁（本 App） */
    fun openPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** uri：下載管理員提供的已下載檔案 */
    fun install(context: Context, uri: Uri) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

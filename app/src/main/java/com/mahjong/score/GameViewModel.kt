package com.mahjong.score

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mahjong.score.data.ReleaseInfo
import com.mahjong.score.data.Storage
import com.mahjong.score.data.UpdateChecker
import com.mahjong.score.data.UpdateDownload
import android.net.Uri
import kotlinx.coroutines.delay
import com.mahjong.score.model.AppLang
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.Rules
import com.mahjong.score.model.Win

enum class UpdateResult { UP_TO_DATE, FAILED }

sealed interface DownloadState {
    data object Idle : DownloadState
    /** progress：0..1 */
    data class Downloading(val progress: Float) : DownloadState
    data class Done(val uri: Uri) : DownloadState
    data object Failed : DownloadState
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private val downloads = UpdateDownload(app)

    var current by mutableStateOf(storage.loadCurrent())
        private set
    var history by mutableStateOf(storage.loadHistory())
        private set

    /** 有可下載的新版本時不為 null（顯示更新對話框） */
    var availableUpdate by mutableStateOf<ReleaseInfo?>(null)
        private set
    /** 手動檢查更新的結果（已是最新版／無法取得） */
    var updateResult by mutableStateOf<UpdateResult?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set

    /** 介面語言；未設定時依系統語言（日文 → 日本語，其他 → 中文） */
    var lang by mutableStateOf(
        storage.lang ?: if (Locale.getDefault().language == "ja") AppLang.JA else AppLang.ZH
    )
        private set

    fun changeLang(value: AppLang) {
        lang = value
        storage.lang = value
    }

    init {
        runCatching { downloads.cleanUpIfInstalled(BuildConfig.VERSION_NAME) }
        // v1.4.1 以前 App 自己下載的 APK 放在快取的 updates/，現在改用系統下載，舊檔刪掉
        runCatching { java.io.File(app.cacheDir, "updates").deleteRecursively() }
        checkUpdate(manual = false)
    }

    /** manual = true 時即使是已忽略的版本也會提示，並顯示「已是最新版本」等結果 */
    fun checkUpdate(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            checkingUpdate = false
            val newer = latest != null && UpdateChecker.isNewer(latest.version, BuildConfig.VERSION_NAME)
            when {
                newer && (manual || latest.tag != storage.ignoredVersion) -> {
                    availableUpdate = latest
                    // 上次已開始（或已完成）的背景下載：直接顯示進度或「安裝」
                    if (latest.apkUrl != null && runCatching { downloads.isKnown(latest.apkUrl) }.getOrDefault(false)) watchDownload()
                    else download = DownloadState.Idle
                }
                !manual -> Unit
                latest == null -> updateResult = UpdateResult.FAILED
                else -> updateResult = UpdateResult.UP_TO_DATE
            }
        }
    }

    fun ignoreUpdate() {
        availableUpdate?.let { storage.ignoredVersion = it.tag }
        dismissUpdate()
    }

    /** 關掉對話框；系統下載會在背景繼續（完成後有通知，下次開 App 會接著提示安裝） */
    fun dismissUpdate() {
        pollJob?.cancel()
        availableUpdate = null
    }

    // ---- 用系統下載管理員下載更新 ----

    var download by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set
    private var pollJob: Job? = null

    /** 去設定允許安裝後，回到 App 時自動接著安裝 */
    var installWhenAllowed by mutableStateOf(false)

    fun startDownload(notification: String) {
        val r = availableUpdate ?: return
        val url = r.apkUrl ?: return
        val ok = runCatching { downloads.enqueue(r.version, url, notification) }.isSuccess
        if (ok) watchDownload() else download = DownloadState.Failed
    }

    /** 每秒讀一次系統下載的進度，直到完成或失敗 */
    private fun watchDownload() {
        pollJob?.cancel()
        download = DownloadState.Downloading(0f)
        pollJob = viewModelScope.launch {
            while (true) {
                val p = withContext(Dispatchers.IO) { runCatching { downloads.progress() }.getOrNull() }
                when {
                    p == null -> { download = DownloadState.Failed; return@launch }
                    p.active -> download = DownloadState.Downloading(if (p.total > 0) p.bytes.toFloat() / p.total else 0f)
                    p.status == android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        download = downloads.downloaded()?.let { DownloadState.Done(it) } ?: DownloadState.Failed
                        return@launch
                    }
                    else -> { download = DownloadState.Failed; return@launch }
                }
                delay(1000)
            }
        }
    }

    fun cancelDownload() {
        pollJob?.cancel()
        runCatching { downloads.cancel() }
        download = DownloadState.Idle
    }
    fun clearUpdateResult() {
        updateResult = null
    }

    private fun update(game: Game?) {
        current = game
        storage.saveCurrent(game)
    }

    fun lastNames(): List<String> = (current ?: history.firstOrNull())?.names ?: List(4) { "" }
    fun lastRules(): Rules = (current ?: history.firstOrNull())?.rules ?: Rules()

    fun startGame(names: List<String>, rules: Rules) = update(GameLogic.newGame(names, rules))

    fun win(wins: List<Win>, loser: Int?, riichi: Set<Int>) {
        current?.let { update(GameLogic.applyWin(it, wins, loser, riichi)) }
    }

    fun draw(tenpai: Set<Int>, riichi: Set<Int>, abortive: Boolean) {
        current?.let { update(GameLogic.applyDraw(it, tenpai, riichi, abortive)) }
    }

    fun undo() {
        current?.let { update(GameLogic.undo(it)) }
    }

    fun endManually() {
        current?.let { update(it.copy(finished = true, manualEnd = true, riichi = emptySet())) }
    }

    /** 對局中宣告／取消立直 */
    fun toggleRiichi(p: Int) {
        current?.let { g -> update(g.copy(riichi = if (p in g.riichi) g.riichi - p else g.riichi + p)) }
    }

    /** 把結束的半莊存入歷史，回傳其 id */
    fun archive(): Long? {
        val g = current ?: return null
        history = listOf(g.copy(finished = true)) + history
        storage.saveHistory(history)
        update(null)
        return g.id
    }

    fun deleteHistory(id: Long) {
        history = history.filterNot { it.id == id }
        storage.saveHistory(history)
    }
}

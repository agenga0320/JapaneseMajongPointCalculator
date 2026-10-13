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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.Rules
import com.mahjong.score.model.Win

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var current by mutableStateOf(storage.loadCurrent())
        private set
    var history by mutableStateOf(storage.loadHistory())
        private set

    /** 有可下載的新版本時不為 null（顯示更新對話框） */
    var availableUpdate by mutableStateOf<ReleaseInfo?>(null)
        private set
    /** 手動檢查更新的結果訊息 */
    var updateMessage by mutableStateOf<String?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set

    init {
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
                newer && (manual || latest!!.tag != storage.ignoredVersion) -> availableUpdate = latest
                !manual -> Unit
                latest == null -> updateMessage = "無法取得版本資訊，請確認網路連線"
                else -> updateMessage = "目前已是最新版本（v${BuildConfig.VERSION_NAME}）"
            }
        }
    }

    fun ignoreUpdate() {
        availableUpdate?.let { storage.ignoredVersion = it.tag }
        availableUpdate = null
    }

    fun dismissUpdate() {
        availableUpdate = null
    }

    fun clearUpdateMessage() {
        updateMessage = null
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
        current?.let { update(it.copy(finished = true, manualEnd = true)) }
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

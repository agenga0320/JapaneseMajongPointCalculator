package com.mahjong.score.model

val WINDS = listOf("東", "南", "西", "北")

data class Rules(
    val startPoints: Int = 25000,
    val returnPoints: Int = 30000,
    val umaSmall: Int = 10,
    val umaLarge: Int = 20,
    /** 切上滿貫：4翻30符・3翻60符 視為滿貫 */
    val kiriage: Boolean = false,
    /** 擊飛：有人點數低於 0 即結束 */
    val tobi: Boolean = true,
    /** 西入：南4局結束時無人達返點則進入西場，西場中有人達返點即結束（最多到西4局） */
    val westEntry: Boolean = false,
)

/** kyoku: 0..3 = 東1〜4局，4..7 = 南1〜4局 */
data class RoundState(val kyoku: Int, val honba: Int, val kyotaku: Int, val scores: List<Int>)

/**
 * 一局的結果。before 保留局前狀態，用於復原。
 * wins 非空為和了（loser == null 為自摸），否則為流局。
 * legacyDetail：v1.0.0 以文字保存的舊紀錄，有值時直接顯示。
 */
data class HandRecord(
    val before: RoundState,
    val deltas: List<Int>,
    val wins: List<Win> = emptyList(),
    val loser: Int? = null,
    val tenpai: Set<Int> = emptySet(),
    val riichi: Set<Int> = emptySet(),
    val abortive: Boolean = false,
    val legacyDetail: String? = null,
) {
    val title: String get() = roundTitle(before)
}

/** yaku：由手牌判定得到的役（手動輸入翻符時為空） */
data class Win(val winner: Int, val han: Int, val fu: Int, val yakuman: Int, val yaku: List<YakuItem> = emptyList())

data class Standing(val player: Int, val rank: Int, val score: Int, val pt: Double)

data class Game(
    val id: Long,
    val startedAt: Long,
    /** 依起家順（index 0 為起家） */
    val names: List<String>,
    val rules: Rules,
    val state: RoundState,
    val records: List<HandRecord> = emptyList(),
    val finished: Boolean = false,
    /** 手動結束（而非打完南4局或擊飛） */
    val manualEnd: Boolean = false,
) {
    val dealer: Int get() = state.kyoku % 4
    val roundTitle: String get() = roundTitle(state)
    fun seatWind(p: Int) = WINDS[(p - dealer + 4) % 4]
}

fun kyokuName(kyoku: Int) = "${WINDS[kyoku / 4 % 4]}${kyoku % 4 + 1}局"
fun roundTitle(s: RoundState) = "${kyokuName(s.kyoku)} ${s.honba}本場"

object GameLogic {
    const val LAST_KYOKU = 7 // 南4局
    const val LAST_WEST_KYOKU = 11 // 西4局

    fun newGame(names: List<String>, rules: Rules): Game {
        val now = System.currentTimeMillis()
        return Game(now, now, names, rules, RoundState(0, 0, 0, List(4) { rules.startPoints }))
    }

    /**
     * 和了的點數變動。loser == null 為自摸。
     * 多人榮和（雙響）時，本場與供託由放銃者下家起最近的和了者取得（頭跳ね）。
     */
    fun winDeltas(game: Game, wins: List<Win>, loser: Int?, riichi: Set<Int>): IntArray {
        val s = game.state
        val dealer = game.dealer
        val d = IntArray(4)
        riichi.forEach { d[it] -= 1000 }
        val pot = (s.kyotaku + riichi.size) * 1000
        val head = if (loser == null) wins.first().winner
        else (1..3).map { (loser + it) % 4 }.first { p -> wins.any { it.winner == p } }

        for (w in wins) {
            val pay = Scoring.payment(w.han, w.fu, w.yakuman, w.winner == dealer, game.rules.kiriage)
            val honba = if (w.winner == head) s.honba else 0
            if (loser == null) {
                for (q in 0 until 4) {
                    if (q == w.winner) continue
                    val base = if (w.winner == dealer || q != dealer) pay.tsumoChild else pay.tsumoDealer
                    val amt = base + 100 * honba
                    d[q] -= amt
                    d[w.winner] += amt
                }
            } else {
                val amt = pay.ron + 300 * honba
                d[loser] -= amt
                d[w.winner] += amt
            }
        }
        d[head] += pot
        return d
    }

    fun applyWin(game: Game, wins: List<Win>, loser: Int?, riichi: Set<Int>): Game {
        val s = game.state
        val d = winDeltas(game, wins, loser, riichi)
        val dealerKeeps = wins.any { it.winner == game.dealer }
        val next = RoundState(
            kyoku = if (dealerKeeps) s.kyoku else s.kyoku + 1,
            honba = if (dealerKeeps) s.honba + 1 else 0,
            kyotaku = 0,
            scores = s.scores.mapIndexed { i, v -> v + d[i] },
        )
        return advance(game, next, HandRecord(s, d.toList(), wins = wins, loser = loser, riichi = riichi))
    }

    /** 流局的點數變動（不聽罰符 3000 點）。abortive = 途中流局，不付罰符 */
    fun drawDeltas(tenpai: Set<Int>, riichi: Set<Int>, abortive: Boolean): IntArray {
        val d = IntArray(4)
        riichi.forEach { d[it] -= 1000 }
        if (!abortive && tenpai.size in 1..3) {
            val gain = 3000 / tenpai.size
            val loss = 3000 / (4 - tenpai.size)
            for (i in 0 until 4) d[i] += if (i in tenpai) gain else -loss
        }
        return d
    }

    fun applyDraw(game: Game, tenpai: Set<Int>, riichi: Set<Int>, abortive: Boolean): Game {
        val s = game.state
        val d = drawDeltas(tenpai, riichi, abortive)
        val dealerKeeps = abortive || game.dealer in tenpai
        val next = RoundState(
            kyoku = if (dealerKeeps) s.kyoku else s.kyoku + 1,
            honba = s.honba + 1,
            kyotaku = s.kyotaku + riichi.size,
            scores = s.scores.mapIndexed { i, v -> v + d[i] },
        )
        val rec = HandRecord(s, d.toList(), tenpai = if (abortive) emptySet() else tenpai, riichi = riichi, abortive = abortive)
        return advance(game, next, rec)
    }

    /** 一局紀錄的文字說明 */
    fun describe(rec: HandRecord, names: List<String>, rules: Rules, lang: AppLang): String {
        rec.legacyDetail?.let { return it }
        fun join(ps: Set<Int>) = ps.sorted().joinToString("、") { names[it] }
        return buildString {
            if (rec.wins.isNotEmpty()) {
                append(rec.wins.joinToString("、") { w ->
                    val how = if (rec.loser == null) lang.t("自摸", "ツモ") else lang.t("榮和", "ロン")
                    val yaku = w.yaku.joinToString("、") { y -> if (y.isDora) "${y.label(lang)}${y.han}" else y.label(lang) }
                    "${names[w.winner]} $how ${Scoring.handLabel(w.han, w.fu, w.yakuman, rules.kiriage, lang)}" +
                        if (yaku.isNotEmpty()) "（$yaku）" else ""
                })
                rec.loser?.let { append(lang.t("（${names[it]} 放銃）", "（${names[it]} 放銃）")) }
            } else if (rec.abortive) {
                append("途中流局")
            } else {
                append(lang.t("流局　", "流局　"))
                append(
                    if (rec.tenpai.isEmpty()) lang.t("全員不聽", "全員ノーテン")
                    else lang.t("聽牌：", "テンパイ：") + join(rec.tenpai)
                )
            }
            if (rec.riichi.isNotEmpty()) append("\n" + lang.t("立直：", "リーチ：") + join(rec.riichi))
        }
    }

    private fun advance(game: Game, next: RoundState, rec: HandRecord): Game {
        val r = game.rules
        val over = when {
            r.tobi && next.scores.any { it < 0 } -> true
            next.kyoku <= LAST_KYOKU -> false
            !r.westEntry || next.kyoku > LAST_WEST_KYOKU -> true
            // 南4局結束時或西場中，有人達返點即結束；否則（繼續）進入／留在西場
            else -> next.scores.any { it >= r.returnPoints }
        }
        return game.copy(state = next, records = game.records + rec, finished = over, manualEnd = false)
    }

    fun undo(game: Game): Game {
        if (game.finished && game.manualEnd) return game.copy(finished = false, manualEnd = false)
        val last = game.records.lastOrNull() ?: return game
        return game.copy(state = last.before, records = game.records.dropLast(1), finished = false, manualEnd = false)
    }

    /** 名次順（同分時起家較近者優先） */
    fun order(scores: List<Int>): List<Int> =
        (0 until 4).sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it })

    /** 結算：剩餘供託歸第1名，pt = (點數 − 返點)/1000 + 馬 (+ 第1名的オカ) */
    fun standings(game: Game): List<Standing> {
        val s = game.state
        val r = game.rules
        val uma = listOf(r.umaLarge, r.umaSmall, -r.umaSmall, -r.umaLarge)
        val oka = (r.returnPoints - r.startPoints) * 4 / 1000.0
        return order(s.scores).mapIndexed { rank, p ->
            val score = s.scores[p] + if (rank == 0) s.kyotaku * 1000 else 0
            val pt = (score - r.returnPoints) / 1000.0 + uma[rank] + if (rank == 0) oka else 0.0
            Standing(p, rank + 1, score, pt)
        }
    }
}

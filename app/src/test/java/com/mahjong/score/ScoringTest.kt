package com.mahjong.score

import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.Rules
import com.mahjong.score.model.Scoring
import com.mahjong.score.model.Win
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    private fun ron(han: Int, fu: Int, dealer: Boolean, kiriage: Boolean = false) =
        Scoring.payment(han, fu, 0, dealer, kiriage).ron

    @Test
    fun ronPoints() {
        assertEquals(1000, ron(1, 30, false))
        assertEquals(1500, ron(1, 30, true))
        assertEquals(1600, ron(2, 25, false))
        assertEquals(3900, ron(3, 30, false))
        assertEquals(3900, ron(2, 40, true))
        assertEquals(7700, ron(4, 30, false))
        assertEquals(8000, ron(4, 30, false, kiriage = true))
        assertEquals(8000, ron(4, 40, false))
        assertEquals(12000, ron(6, 30, false))
        assertEquals(18000, ron(6, 30, true))
        assertEquals(32000, Scoring.payment(1, 30, 1, false).ron)
        assertEquals(96000, Scoring.payment(1, 30, 2, true).ron)
    }

    @Test
    fun tsumoPoints() {
        val child = Scoring.payment(1, 30, 0, false)
        assertEquals(300, child.tsumoChild)
        assertEquals(500, child.tsumoDealer)
        val pinfu = Scoring.payment(2, 20, 0, false)
        assertEquals(400, pinfu.tsumoChild)
        assertEquals(700, pinfu.tsumoDealer)
        assertEquals(1300, Scoring.payment(2, 40, 0, true).tsumoChild)
        assertEquals(4000, Scoring.payment(5, 30, 0, true).tsumoChild)
    }

    @Test
    fun tsumoWithHonbaAndKyotaku() {
        var g = GameLogic.newGame(listOf("A", "B", "C", "D"), Rules())
        g = g.copy(state = g.state.copy(honba = 2, kyotaku = 1))
        // 子（B）自摸 1翻30符，A 立直
        val d = GameLogic.winDeltas(g, listOf(Win(1, 1, 30, 0)), null, setOf(0))
        // 親 A 付 500+200，C/D 各付 300+200，B 另得供託 2 本（原有 1 + A 立直 1）
        assertEquals(listOf(-1000 - 700, 700 + 500 + 500 + 2000, -500, -500), d.toList())
        assertEquals(1000, d.sum()) // 原本場上的 1 本供託
    }

    @Test
    fun doubleRonHeadTakesSticks() {
        var g = GameLogic.newGame(listOf("A", "B", "C", "D"), Rules())
        g = g.copy(state = g.state.copy(honba = 1, kyotaku = 1))
        // A 放銃給 C 與 D。從 A 的下家依序 B→C→D，C 較近，本場與供託歸 C
        val d = GameLogic.winDeltas(g, listOf(Win(2, 1, 30, 0), Win(3, 1, 30, 0)), 0, emptySet())
        assertEquals(1000 + 300 + 1000, d[2])
        assertEquals(1000, d[3])
    }

    @Test
    fun notenPayments() {
        assertEquals(listOf(3000, -1000, -1000, -1000), GameLogic.drawDeltas(setOf(0), emptySet(), false).toList())
        assertEquals(listOf(1500, 1500, -1500, -1500), GameLogic.drawDeltas(setOf(0, 1), emptySet(), false).toList())
        assertEquals(listOf(1000, 1000, 1000, -3000), GameLogic.drawDeltas(setOf(0, 1, 2), emptySet(), false).toList())
        assertEquals(listOf(0, 0, 0, 0), GameLogic.drawDeltas(emptySet(), emptySet(), false).toList())
    }

    @Test
    fun westEntry() {
        val names = listOf("A", "B", "C", "D")
        fun south4(scores: List<Int>, west: Boolean) = GameLogic.newGame(names, Rules(westEntry = west))
            .let { it.copy(state = it.state.copy(kyoku = 7, scores = scores)) }
        val low = listOf(28000, 26000, 24000, 22000)

        // 南4 流局，只有 B 聽牌（親 D 不聽）→ A27000 B29000 C23000 D21000，無人達 30000 → 進入西1局
        val g1 = GameLogic.applyDraw(south4(low, true), setOf(1), emptySet(), false)
        assertEquals(false, g1.finished)
        assertEquals(8, g1.state.kyoku)
        assertEquals("西1局 1本場", g1.roundTitle)

        // 未開啟西入 → 南4 結束
        assertEquals(true, GameLogic.applyDraw(south4(low, false), setOf(1), emptySet(), false).finished)

        // 西1局（親 A）A 榮和 B 3翻30符 5800+300 → 33100 達到 30000 → 結束
        val g2 = GameLogic.applyWin(g1, listOf(Win(0, 3, 30, 0)), 1, emptySet())
        assertEquals(33100, g2.state.scores[0])
        assertEquals(true, g2.finished)

        // 西4局 全員不聽流局，仍無人達 30000 → 結束（不北入）
        val west4 = g1.copy(state = g1.state.copy(kyoku = 11, honba = 0, scores = low))
        assertEquals(true, GameLogic.applyDraw(west4, emptySet(), emptySet(), false).finished)
    }

    @Test
    fun standingsSumToZero() {
        var g = GameLogic.newGame(listOf("A", "B", "C", "D"), Rules())
        g = g.copy(state = g.state.copy(scores = listOf(42000, 31000, 18000, 8000), kyotaku = 1))
        val s = GameLogic.standings(g)
        assertEquals(0.0, s.sumOf { it.pt }, 1e-9)
        assertEquals(43000, s[0].score)
        assertEquals(53.0, s[0].pt, 1e-9) // (43000-30000)/1000 + 20 + 20(オカ)
    }
}

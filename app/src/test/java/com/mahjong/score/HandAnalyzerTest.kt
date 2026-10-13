package com.mahjong.score

import com.mahjong.score.model.Analysis
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.HandAnalyzer
import com.mahjong.score.model.HandResult
import com.mahjong.score.model.Meld
import com.mahjong.score.model.MeldType
import com.mahjong.score.model.Situation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandAnalyzerTest {
    /** "123m456p789s11z" 形式（z: 1-7 = 東南西北白發中）。最後一張為和了牌 */
    private fun tiles(s: String): List<Int> {
        val out = mutableListOf<Int>()
        val buf = mutableListOf<Int>()
        for (ch in s) {
            if (ch.isDigit()) buf += ch - '0'
            else {
                val base = when (ch) { 'm' -> 0; 'p' -> 9; 's' -> 18; else -> 27 }
                buf.forEach { out += base + it - 1 }
                buf.clear()
            }
        }
        return out
    }

    private fun agari(hand: String, sit: Situation = Situation(), melds: List<Meld> = emptyList()): HandResult {
        val a = HandAnalyzer.analyze(tiles(hand), melds, sit)
        assertTrue("expected agari but was $a", a is Analysis.Agari)
        return (a as Analysis.Agari).result
    }

    private fun HandResult.names() = yaku.map { it.label(AppLang.ZH) }.toSet()

    @Test
    fun pinfuTsumo() {
        // 234m 567p 345s 678s 99m，和了 8s（ryanmen）
        val r = agari("234m99m567p34567s8s", Situation(tsumo = true, riichi = true))
        assertTrue(r.names().containsAll(listOf("立直", "門前清自摸和", "平和")))
        assertEquals(20, r.fu)
    }

    @Test
    fun kanchanIsNotPinfu() {
        // 和了 8s 嵌張（79s 等 8s）
        val r = agari("234m99m567p345s79s8s", Situation(riichi = true))
        assertTrue("平和" !in r.names())
        assertEquals(40, r.fu) // 20 + 門前榮和10 + 嵌張2 = 32 → 40
    }

    @Test
    fun chiitoitsu() {
        val r = agari("1133m5577p22s66s4z4z", Situation(riichi = true))
        assertTrue("七對子" in r.names())
        assertEquals(25, r.fu)
    }

    @Test
    fun openYakuhaiAndHonitsu() {
        // 副露：碰 中；手牌 123m 456m 789m＋東 單騎 → 役牌中1・混一色2・一氣通貫1（皆為副露後的翻數）
        val r = agari("123456789m11z", Situation(seatWind = 1), listOf(Meld(MeldType.PON, 33)))
        assertTrue(r.names().containsAll(listOf("役牌 中", "混一色", "一氣通貫")))
        assertEquals(4, r.han)
    }

    @Test
    fun suuankouTsumo() {
        val r = agari("111m333p555s777s22z", Situation(tsumo = true))
        assertEquals(1, r.yakuman)
        assertTrue("四暗刻" in r.names())
    }

    @Test
    fun suuankouRonOnShanponIsSanankou() {
        // 雙碰榮和 → 三暗刻＋對對和
        val r = agari("111m333p22z777s55s5s", Situation(tsumo = false))
        assertEquals(0, r.yakuman)
        assertTrue(r.names().containsAll(listOf("三暗刻", "對對和")))
    }

    @Test
    fun kokushi() {
        val r = agari("19m19p19s12345677z")
        assertEquals(1, r.yakuman)
    }

    @Test
    fun noYaku() {
        // 副露後沒有役：吃 234m，手牌 567p 345s 678s 99m
        val a = HandAnalyzer.analyze(
            tiles("99m567p34567s8s"),
            listOf(Meld(MeldType.CHI, 1)),
            Situation(),
        )
        assertTrue(a is Analysis.NoYaku)
    }

    @Test
    fun incomplete() {
        val a = HandAnalyzer.analyze(tiles("123m"), emptyList(), Situation())
        assertEquals(Analysis.Incomplete(11), a)
    }
}

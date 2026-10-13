package com.mahjong.score.model

/**
 * 牌的編碼：0..8 = 1〜9萬、9..17 = 1〜9筒、18..26 = 1〜9索、
 * 27..30 = 東南西北、31..33 = 白發中
 */
object Tiles {
    const val COUNT = 34
    private val HONORS = listOf("東", "南", "西", "北", "白", "發", "中")
    private val SUITS = listOf("萬", "筒", "索")
    val YAOCHU = listOf(0, 8, 9, 17, 18, 26) + (27..33)
    private val GREEN = setOf(19, 20, 21, 23, 25, 32) // 2346 8索、發

    fun isHonor(t: Int) = t >= 27
    fun isWind(t: Int) = t in 27..30
    fun isDragon(t: Int) = t >= 31
    fun isTerminal(t: Int) = t < 27 && (t % 9 == 0 || t % 9 == 8)
    fun isYaochu(t: Int) = isHonor(t) || isTerminal(t)
    fun isGreen(t: Int) = t in GREEN
    fun rankText(t: Int) = if (isHonor(t)) HONORS[t - 27] else "${t % 9 + 1}"
    fun suitText(t: Int) = if (isHonor(t)) "" else SUITS[t / 9]
    fun name(t: Int) = rankText(t) + suitText(t)
}

enum class MeldType(val label: String) { CHI("吃"), PON("碰"), MINKAN("明槓"), ANKAN("暗槓") }

/** 副露。CHI 的 tile 為順子最小的牌 */
data class Meld(val type: MeldType, val tile: Int) {
    val tiles: List<Int>
        get() = when (type) {
            MeldType.CHI -> listOf(tile, tile + 1, tile + 2)
            MeldType.PON -> List(3) { tile }
            else -> List(4) { tile }
        }
}

/** 和了時的狀況。風以 0..3 表示 東南西北 */
data class Situation(
    val tsumo: Boolean = false,
    val roundWind: Int = 0,
    val seatWind: Int = 0,
    val riichi: Boolean = false,
    val doubleRiichi: Boolean = false,
    val ippatsu: Boolean = false,
    /** 海底摸月（自摸）／河底撈魚（榮和） */
    val lastTile: Boolean = false,
    val rinshan: Boolean = false,
    val chankan: Boolean = false,
    /** 天和（親）／地和（子） */
    val tenhou: Boolean = false,
    val dora: Int = 0,
    val akadora: Int = 0,
    val uradora: Int = 0,
) {
    val dealer: Boolean get() = seatWind == 0
}

data class YakuItem(val name: String, val han: Int, val yakuman: Int = 0, val isDora: Boolean = false)
data class FuItem(val name: String, val fu: Int)

data class HandResult(
    val yaku: List<YakuItem>,
    val fuItems: List<FuItem>,
    val fu: Int,
    val han: Int,
    val yakuman: Int,
    /** 不含寶牌的翻數；0 表示無役 */
    val yakuHan: Int,
) {
    val hasYaku: Boolean get() = yakuman > 0 || yakuHan > 0
}

sealed interface Analysis {
    /** 張數不對。diff > 0 表示還差幾張，< 0 表示多了幾張 */
    data class Incomplete(val diff: Int) : Analysis
    data object NotAgari : Analysis
    data class NoYaku(val result: HandResult) : Analysis
    data class Agari(val result: HandResult) : Analysis
}

object HandAnalyzer {
    private enum class Kind { SEQ, TRIP }
    private enum class Wait(val label: String?) {
        RYANMEN(null), SHANPON(null), KANCHAN("嵌張待"), PENCHAN("邊張待"), TANKI("單騎待")
    }

    private data class Block(val kind: Kind, val tile: Int, val open: Boolean = false, val kan: Boolean = false)

    /** concealed：門前的牌（含和了牌），最後一張為和了牌 */
    fun analyze(concealed: List<Int>, melds: List<Meld>, sit: Situation): Analysis {
        val need = 14 - 3 * melds.size
        if (concealed.size != need) return Analysis.Incomplete(need - concealed.size)
        val win = concealed.last()
        val counts = IntArray(Tiles.COUNT).also { c -> concealed.forEach { c[it]++ } }
        val all = concealed + melds.flatMap { it.tiles }
        val menzen = melds.all { it.type == MeldType.ANKAN }

        val candidates = mutableListOf<HandResult>()

        // 國士無雙
        if (melds.isEmpty() && Tiles.YAOCHU.all { counts[it] >= 1 } && concealed.all { Tiles.isYaochu(it) }) {
            candidates += yakumanResult(listOf(YakuItem("國士無雙", 0, 1)) + tenhou(sit, menzen))
        }
        // 七對子
        if (melds.isEmpty() && counts.count { it == 2 } == 7) {
            candidates += chiitoitsu(all, sit)
        }
        // 一般形（4面子1雀頭）
        val meldBlocks = melds.map {
            Block(
                if (it.type == MeldType.CHI) Kind.SEQ else Kind.TRIP,
                it.tile,
                open = it.type != MeldType.ANKAN,
                kan = it.type == MeldType.MINKAN || it.type == MeldType.ANKAN,
            )
        }
        for ((pair, sets) in decompose(counts)) {
            if (pair == win) candidates += regular(pair, sets, -1, Wait.TANKI, meldBlocks, all, counts, menzen, sit)
            sets.forEachIndexed { i, b ->
                val wait = when {
                    b.kind == Kind.TRIP && b.tile == win -> Wait.SHANPON
                    b.kind == Kind.SEQ && win in b.tile..b.tile + 2 -> when {
                        win == b.tile + 1 -> Wait.KANCHAN
                        win == b.tile + 2 && b.tile % 9 == 0 -> Wait.PENCHAN
                        win == b.tile && b.tile % 9 == 6 -> Wait.PENCHAN
                        else -> Wait.RYANMEN
                    }
                    else -> null
                }
                if (wait != null) candidates += regular(pair, sets, i, wait, meldBlocks, all, counts, menzen, sit)
            }
        }

        if (candidates.isEmpty()) return Analysis.NotAgari
        val best = candidates.maxWith(
            compareBy<HandResult>(
                { it.yakuman },
                { if (it.hasYaku) Scoring.basePoints(it.han, it.fu) else -1 },
                { it.han },
                { it.fu },
            )
        )
        return if (best.hasYaku) Analysis.Agari(best) else Analysis.NoYaku(best)
    }

    // ---- 分解 ----

    private fun decompose(counts: IntArray): List<Pair<Int, List<Block>>> {
        val out = mutableListOf<Pair<Int, List<Block>>>()
        for (p in 0 until Tiles.COUNT) {
            if (counts[p] < 2) continue
            counts[p] -= 2
            extractSets(counts, 0, mutableListOf(), out, p)
            counts[p] += 2
        }
        return out
    }

    private fun extractSets(c: IntArray, start: Int, acc: MutableList<Block>, out: MutableList<Pair<Int, List<Block>>>, pair: Int) {
        var i = start
        while (i < Tiles.COUNT && c[i] == 0) i++
        if (i == Tiles.COUNT) {
            out += pair to acc.toList()
            return
        }
        if (c[i] >= 3) {
            c[i] -= 3
            acc += Block(Kind.TRIP, i)
            extractSets(c, i, acc, out, pair)
            acc.removeAt(acc.lastIndex)
            c[i] += 3
        }
        if (i < 27 && i % 9 <= 6 && c[i + 1] > 0 && c[i + 2] > 0) {
            c[i]--; c[i + 1]--; c[i + 2]--
            acc += Block(Kind.SEQ, i)
            extractSets(c, i, acc, out, pair)
            acc.removeAt(acc.lastIndex)
            c[i]++; c[i + 1]++; c[i + 2]++
        }
    }

    // ---- 役 ----

    private fun tenhou(sit: Situation, menzen: Boolean): List<YakuItem> =
        if (sit.tenhou && sit.tsumo && menzen) listOf(YakuItem(if (sit.dealer) "天和" else "地和", 0, 1)) else emptyList()

    private fun yakumanResult(ym: List<YakuItem>) =
        HandResult(ym, emptyList(), 0, 0, ym.sumOf { it.yakuman }, 0)

    /** 與手牌形狀無關的役（立直、自摸、海底等） */
    private fun situationYaku(sit: Situation, menzen: Boolean): List<YakuItem> {
        val y = mutableListOf<YakuItem>()
        if (menzen) {
            if (sit.doubleRiichi) y += YakuItem("兩立直", 2)
            else if (sit.riichi) y += YakuItem("立直", 1)
            if (sit.ippatsu && (sit.riichi || sit.doubleRiichi)) y += YakuItem("一發", 1)
            if (sit.tsumo) y += YakuItem("門前清自摸和", 1)
        }
        if (sit.lastTile) y += YakuItem(if (sit.tsumo) "海底摸月" else "河底撈魚", 1)
        if (sit.rinshan && sit.tsumo) y += YakuItem("嶺上開花", 1)
        if (sit.chankan && !sit.tsumo) y += YakuItem("搶槓", 1)
        return y
    }

    /** 只看牌種的役（斷么九、混老頭、混一色、清一色） */
    private fun tileYaku(all: List<Int>, menzen: Boolean): List<YakuItem> {
        val y = mutableListOf<YakuItem>()
        if (all.none { Tiles.isYaochu(it) }) y += YakuItem("斷么九", 1)
        if (all.all { Tiles.isYaochu(it) }) y += YakuItem("混老頭", 2)
        val suits = all.filterNot { Tiles.isHonor(it) }.map { it / 9 }.toSet()
        if (suits.size == 1) {
            y += if (all.any { Tiles.isHonor(it) }) YakuItem("混一色", if (menzen) 3 else 2)
            else YakuItem("清一色", if (menzen) 6 else 5)
        }
        return y
    }

    private fun withDora(yaku: List<YakuItem>, sit: Situation): Pair<List<YakuItem>, Int> {
        val yakuHan = yaku.sumOf { it.han }
        if (yakuHan == 0) return yaku to 0
        val list = yaku.toMutableList()
        if (sit.dora > 0) list += YakuItem("寶牌", sit.dora, isDora = true)
        if (sit.akadora > 0) list += YakuItem("赤寶牌", sit.akadora, isDora = true)
        if (sit.uradora > 0 && (sit.riichi || sit.doubleRiichi)) list += YakuItem("裏寶牌", sit.uradora, isDora = true)
        return list to yakuHan
    }

    private fun chiitoitsu(all: List<Int>, sit: Situation): HandResult {
        if (all.all { Tiles.isHonor(it) }) {
            return yakumanResult(listOf(YakuItem("字一色", 0, 1)) + tenhou(sit, true))
        }
        tenhou(sit, true).takeIf { it.isNotEmpty() }?.let { return yakumanResult(it) }
        val base = situationYaku(sit, true) + YakuItem("七對子", 2) + tileYaku(all, true)
        val (yaku, yakuHan) = withDora(base, sit)
        return HandResult(yaku, listOf(FuItem("七對子", 25)), 25, yaku.sumOf { it.han }, 0, yakuHan)
    }

    private fun regular(
        pair: Int,
        concealedSets: List<Block>,
        winIdx: Int,
        wait: Wait,
        meldBlocks: List<Block>,
        all: List<Int>,
        counts: IntArray,
        menzen: Boolean,
        sit: Situation,
    ): HandResult {
        // 榮和完成的雙碰刻子視為明刻
        val blocks = concealedSets.mapIndexed { i, b ->
            if (i == winIdx && b.kind == Kind.TRIP && !sit.tsumo) b.copy(open = true) else b
        } + meldBlocks
        val trips = blocks.filter { it.kind == Kind.TRIP }
        val seqs = blocks.filter { it.kind == Kind.SEQ }
        val ankou = trips.count { !it.open }
        val kans = blocks.count { it.kan }
        val dragonTrips = trips.count { Tiles.isDragon(it.tile) }
        val windTrips = trips.count { Tiles.isWind(it.tile) }

        // 役滿
        val ym = tenhou(sit, menzen).toMutableList()
        if (trips.size == 4 && ankou == 4) ym += YakuItem("四暗刻", 0, 1)
        if (dragonTrips == 3) ym += YakuItem("大三元", 0, 1)
        if (windTrips == 4) ym += YakuItem("大四喜", 0, 1)
        else if (windTrips == 3 && Tiles.isWind(pair)) ym += YakuItem("小四喜", 0, 1)
        if (all.all { Tiles.isHonor(it) }) ym += YakuItem("字一色", 0, 1)
        if (all.all { Tiles.isGreen(it) }) ym += YakuItem("綠一色", 0, 1)
        if (all.all { Tiles.isTerminal(it) }) ym += YakuItem("清老頭", 0, 1)
        if (kans == 4) ym += YakuItem("四槓子", 0, 1)
        if (meldBlocks.isEmpty() && isChuuren(counts)) ym += YakuItem("九蓮寶燈", 0, 1)
        if (ym.isNotEmpty()) return yakumanResult(ym)

        val roundWindTile = 27 + sit.roundWind
        val seatWindTile = 27 + sit.seatWind
        val yakuhaiPair = Tiles.isDragon(pair) || pair == roundWindTile || pair == seatWindTile
        val pinfu = menzen && seqs.size == 4 && !yakuhaiPair && wait == Wait.RYANMEN

        val y = situationYaku(sit, menzen).toMutableList()
        if (pinfu) y += YakuItem("平和", 1)
        if (menzen) {
            val dup = seqs.groupingBy { it.tile }.eachCount().values.sumOf { it / 2 }
            if (dup >= 2) y += YakuItem("二盃口", 3)
            else if (dup == 1) y += YakuItem("一盃口", 1)
        }
        for (b in trips) {
            when (b.tile) {
                31 -> y += YakuItem("役牌 白", 1)
                32 -> y += YakuItem("役牌 發", 1)
                33 -> y += YakuItem("役牌 中", 1)
            }
            if (b.tile == roundWindTile) y += YakuItem("場風 ${Tiles.name(b.tile)}", 1)
            if (b.tile == seatWindTile) y += YakuItem("自風 ${Tiles.name(b.tile)}", 1)
        }
        if ((0..6).any { r -> (0..2).all { s -> seqs.any { it.tile == s * 9 + r } } }) {
            y += YakuItem("三色同順", if (menzen) 2 else 1)
        }
        if ((0..2).any { s -> listOf(0, 3, 6).all { r -> seqs.any { it.tile == s * 9 + r } } }) {
            y += YakuItem("一氣通貫", if (menzen) 2 else 1)
        }
        val allHaveYaochu = Tiles.isYaochu(pair) && blocks.all { b ->
            if (b.kind == Kind.SEQ) b.tile % 9 == 0 || b.tile % 9 == 6 else Tiles.isYaochu(b.tile)
        }
        if (allHaveYaochu && seqs.isNotEmpty()) {
            y += if (all.none { Tiles.isHonor(it) }) YakuItem("純全帶么九", if (menzen) 3 else 2)
            else YakuItem("混全帶么九", if (menzen) 2 else 1)
        }
        if (trips.size == 4) y += YakuItem("對對和", 2)
        if (ankou == 3) y += YakuItem("三暗刻", 2)
        if ((0..8).any { r -> (0..2).all { s -> trips.any { it.tile == s * 9 + r } } }) y += YakuItem("三色同刻", 2)
        if (kans == 3) y += YakuItem("三槓子", 2)
        if (dragonTrips == 2 && Tiles.isDragon(pair)) y += YakuItem("小三元", 2)
        y += tileYaku(all, menzen)

        val (yaku, yakuHan) = withDora(y, sit)

        // 符
        val fuItems = mutableListOf<FuItem>()
        val fu: Int
        if (pinfu && sit.tsumo) {
            fuItems += FuItem("平和自摸", 20)
            fu = 20
        } else {
            fuItems += FuItem("副底", 20)
            if (menzen && !sit.tsumo) fuItems += FuItem("門前榮和", 10)
            if (sit.tsumo && !pinfu) fuItems += FuItem("自摸", 2)
            for (b in trips) {
                var f = 2
                if (Tiles.isYaochu(b.tile)) f *= 2
                if (!b.open) f *= 2
                if (b.kan) f *= 4
                val kind = if (b.kan) (if (b.open) "明槓" else "暗槓") else (if (b.open) "明刻" else "暗刻")
                fuItems += FuItem("$kind ${Tiles.name(b.tile)}", f)
            }
            var pairFu = 0
            if (Tiles.isDragon(pair)) pairFu += 2
            if (pair == roundWindTile) pairFu += 2
            if (pair == seatWindTile) pairFu += 2
            if (pairFu > 0) fuItems += FuItem("雀頭 ${Tiles.name(pair)}", pairFu)
            wait.label?.let { fuItems += FuItem(it, 2) }
            val raw = fuItems.sumOf { it.fu }
            var rounded = (raw + 9) / 10 * 10
            if (rounded == 20 && !menzen) {
                fuItems += FuItem("副露平和形", 10)
                rounded = 30
            }
            fu = rounded
        }
        return HandResult(yaku, fuItems, fu, yaku.sumOf { it.han }, 0, yakuHan)
    }

    private fun isChuuren(counts: IntArray): Boolean {
        val total = counts.sum()
        for (s in 0..2) {
            val c = (0..8).map { counts[s * 9 + it] }
            if (c.sum() != total) continue
            return c[0] >= 3 && c[8] >= 3 && (1..7).all { c[it] >= 1 }
        }
        return false
    }
}

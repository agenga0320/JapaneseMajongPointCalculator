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

enum class MeldType(private val zh: String, private val ja: String) {
    CHI("吃", "チー"), PON("碰", "ポン"), MINKAN("明槓", "明槓"), ANKAN("暗槓", "暗槓");

    fun label(lang: AppLang) = lang.t(zh, ja)
}

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

enum class Yaku(private val zh: String, private val ja: String) {
    RIICHI("立直", "立直"),
    DOUBLE_RIICHI("兩立直", "ダブル立直"),
    IPPATSU("一發", "一発"),
    MENZEN_TSUMO("門前清自摸和", "門前清自摸和"),
    PINFU("平和", "平和"),
    TANYAO("斷么九", "断么九"),
    IIPEIKOU("一盃口", "一盃口"),
    RYANPEIKOU("二盃口", "二盃口"),
    HAKU("役牌 白", "役牌 白"),
    HATSU("役牌 發", "役牌 發"),
    CHUN("役牌 中", "役牌 中"),
    ROUND_WIND("場風", "場風"),
    SEAT_WIND("自風", "自風"),
    SANSHOKU("三色同順", "三色同順"),
    ITTSU("一氣通貫", "一気通貫"),
    CHANTA("混全帶么九", "混全帯么九"),
    JUNCHAN("純全帶么九", "純全帯么九"),
    CHIITOITSU("七對子", "七対子"),
    TOITOI("對對和", "対々和"),
    SANANKOU("三暗刻", "三暗刻"),
    SANSHOKU_DOUKOU("三色同刻", "三色同刻"),
    SANKANTSU("三槓子", "三槓子"),
    SHOUSANGEN("小三元", "小三元"),
    HONROUTOU("混老頭", "混老頭"),
    HONITSU("混一色", "混一色"),
    CHINITSU("清一色", "清一色"),
    HAITEI("海底摸月", "海底摸月"),
    HOUTEI("河底撈魚", "河底撈魚"),
    RINSHAN("嶺上開花", "嶺上開花"),
    CHANKAN("搶槓", "搶槓"),
    DORA("寶牌", "ドラ"),
    AKADORA("赤寶牌", "赤ドラ"),
    URADORA("裏寶牌", "裏ドラ"),
    KOKUSHI("國士無雙", "国士無双"),
    SUUANKOU("四暗刻", "四暗刻"),
    DAISANGEN("大三元", "大三元"),
    SHOUSUUSHII("小四喜", "小四喜"),
    DAISUUSHII("大四喜", "大四喜"),
    TSUUIISOU("字一色", "字一色"),
    RYUUIISOU("綠一色", "緑一色"),
    CHINROUTOU("清老頭", "清老頭"),
    SUUKANTSU("四槓子", "四槓子"),
    CHUUREN("九蓮寶燈", "九蓮宝燈"),
    TENHOU("天和", "天和"),
    CHIIHOU("地和", "地和");

    fun label(lang: AppLang) = lang.t(zh, ja)
}

/** tile >= 0 時在名稱後附上牌名（場風 東 等） */
data class YakuItem(val yaku: Yaku, val han: Int, val yakuman: Int = 0, val tile: Int = -1) {
    val isDora: Boolean get() = yaku == Yaku.DORA || yaku == Yaku.AKADORA || yaku == Yaku.URADORA
    fun label(lang: AppLang) = yaku.label(lang) + if (tile >= 0) " ${Tiles.name(tile)}" else ""
}

enum class FuKind(private val zh: String, private val ja: String) {
    BASE("副底", "副底"),
    MENZEN_RON("門前榮和", "門前ロン"),
    TSUMO("自摸", "ツモ"),
    PINFU_TSUMO("平和自摸", "平和ツモ"),
    MINKOU("明刻", "明刻"),
    ANKOU("暗刻", "暗刻"),
    MINKAN("明槓", "明槓"),
    ANKAN("暗槓", "暗槓"),
    PAIR("雀頭", "雀頭"),
    KANCHAN("嵌張待", "嵌張待ち"),
    PENCHAN("邊張待", "辺張待ち"),
    TANKI("單騎待", "単騎待ち"),
    OPEN_PINFU("副露平和形", "喰い平和形"),
    CHIITOITSU("七對子", "七対子");

    fun label(lang: AppLang) = lang.t(zh, ja)
}

data class FuItem(val kind: FuKind, val fu: Int, val tile: Int = -1) {
    fun label(lang: AppLang) = kind.label(lang) + if (tile >= 0) " ${Tiles.name(tile)}" else ""
}

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
    private enum class Wait(val fu: FuKind?) {
        RYANMEN(null), SHANPON(null), KANCHAN(FuKind.KANCHAN), PENCHAN(FuKind.PENCHAN), TANKI(FuKind.TANKI)
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
            candidates += yakumanResult(listOf(YakuItem(Yaku.KOKUSHI, 0, 1)) + tenhou(sit, menzen))
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
        if (sit.tenhou && sit.tsumo && menzen) listOf(YakuItem(if (sit.dealer) Yaku.TENHOU else Yaku.CHIIHOU, 0, 1))
        else emptyList()

    private fun yakumanResult(ym: List<YakuItem>) =
        HandResult(ym, emptyList(), 0, 0, ym.sumOf { it.yakuman }, 0)

    /** 與手牌形狀無關的役（立直、自摸、海底等） */
    private fun situationYaku(sit: Situation, menzen: Boolean): List<YakuItem> {
        val y = mutableListOf<YakuItem>()
        if (menzen) {
            if (sit.doubleRiichi) y += YakuItem(Yaku.DOUBLE_RIICHI, 2)
            else if (sit.riichi) y += YakuItem(Yaku.RIICHI, 1)
            if (sit.ippatsu && (sit.riichi || sit.doubleRiichi)) y += YakuItem(Yaku.IPPATSU, 1)
            if (sit.tsumo) y += YakuItem(Yaku.MENZEN_TSUMO, 1)
        }
        if (sit.lastTile) y += YakuItem(if (sit.tsumo) Yaku.HAITEI else Yaku.HOUTEI, 1)
        if (sit.rinshan && sit.tsumo) y += YakuItem(Yaku.RINSHAN, 1)
        if (sit.chankan && !sit.tsumo) y += YakuItem(Yaku.CHANKAN, 1)
        return y
    }

    /** 只看牌種的役（斷么九、混老頭、混一色、清一色） */
    private fun tileYaku(all: List<Int>, menzen: Boolean): List<YakuItem> {
        val y = mutableListOf<YakuItem>()
        if (all.none { Tiles.isYaochu(it) }) y += YakuItem(Yaku.TANYAO, 1)
        if (all.all { Tiles.isYaochu(it) }) y += YakuItem(Yaku.HONROUTOU, 2)
        val suits = all.filterNot { Tiles.isHonor(it) }.map { it / 9 }.toSet()
        if (suits.size == 1) {
            y += if (all.any { Tiles.isHonor(it) }) YakuItem(Yaku.HONITSU, if (menzen) 3 else 2)
            else YakuItem(Yaku.CHINITSU, if (menzen) 6 else 5)
        }
        return y
    }

    private fun withDora(yaku: List<YakuItem>, sit: Situation): Pair<List<YakuItem>, Int> {
        val yakuHan = yaku.sumOf { it.han }
        if (yakuHan == 0) return yaku to 0
        val list = yaku.toMutableList()
        if (sit.dora > 0) list += YakuItem(Yaku.DORA, sit.dora)
        if (sit.akadora > 0) list += YakuItem(Yaku.AKADORA, sit.akadora)
        if (sit.uradora > 0 && (sit.riichi || sit.doubleRiichi)) list += YakuItem(Yaku.URADORA, sit.uradora)
        return list to yakuHan
    }

    private fun chiitoitsu(all: List<Int>, sit: Situation): HandResult {
        if (all.all { Tiles.isHonor(it) }) {
            return yakumanResult(listOf(YakuItem(Yaku.TSUUIISOU, 0, 1)) + tenhou(sit, true))
        }
        tenhou(sit, true).takeIf { it.isNotEmpty() }?.let { return yakumanResult(it) }
        val base = situationYaku(sit, true) + YakuItem(Yaku.CHIITOITSU, 2) + tileYaku(all, true)
        val (yaku, yakuHan) = withDora(base, sit)
        return HandResult(yaku, listOf(FuItem(FuKind.CHIITOITSU, 25)), 25, yaku.sumOf { it.han }, 0, yakuHan)
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
        if (trips.size == 4 && ankou == 4) ym += YakuItem(Yaku.SUUANKOU, 0, 1)
        if (dragonTrips == 3) ym += YakuItem(Yaku.DAISANGEN, 0, 1)
        if (windTrips == 4) ym += YakuItem(Yaku.DAISUUSHII, 0, 1)
        else if (windTrips == 3 && Tiles.isWind(pair)) ym += YakuItem(Yaku.SHOUSUUSHII, 0, 1)
        if (all.all { Tiles.isHonor(it) }) ym += YakuItem(Yaku.TSUUIISOU, 0, 1)
        if (all.all { Tiles.isGreen(it) }) ym += YakuItem(Yaku.RYUUIISOU, 0, 1)
        if (all.all { Tiles.isTerminal(it) }) ym += YakuItem(Yaku.CHINROUTOU, 0, 1)
        if (kans == 4) ym += YakuItem(Yaku.SUUKANTSU, 0, 1)
        if (meldBlocks.isEmpty() && isChuuren(counts)) ym += YakuItem(Yaku.CHUUREN, 0, 1)
        if (ym.isNotEmpty()) return yakumanResult(ym)

        val roundWindTile = 27 + sit.roundWind
        val seatWindTile = 27 + sit.seatWind
        val yakuhaiPair = Tiles.isDragon(pair) || pair == roundWindTile || pair == seatWindTile
        val pinfu = menzen && seqs.size == 4 && !yakuhaiPair && wait == Wait.RYANMEN

        val y = situationYaku(sit, menzen).toMutableList()
        if (pinfu) y += YakuItem(Yaku.PINFU, 1)
        if (menzen) {
            val dup = seqs.groupingBy { it.tile }.eachCount().values.sumOf { it / 2 }
            if (dup >= 2) y += YakuItem(Yaku.RYANPEIKOU, 3)
            else if (dup == 1) y += YakuItem(Yaku.IIPEIKOU, 1)
        }
        for (b in trips) {
            when (b.tile) {
                31 -> y += YakuItem(Yaku.HAKU, 1)
                32 -> y += YakuItem(Yaku.HATSU, 1)
                33 -> y += YakuItem(Yaku.CHUN, 1)
            }
            if (b.tile == roundWindTile) y += YakuItem(Yaku.ROUND_WIND, 1, tile = b.tile)
            if (b.tile == seatWindTile) y += YakuItem(Yaku.SEAT_WIND, 1, tile = b.tile)
        }
        if ((0..6).any { r -> (0..2).all { s -> seqs.any { it.tile == s * 9 + r } } }) {
            y += YakuItem(Yaku.SANSHOKU, if (menzen) 2 else 1)
        }
        if ((0..2).any { s -> listOf(0, 3, 6).all { r -> seqs.any { it.tile == s * 9 + r } } }) {
            y += YakuItem(Yaku.ITTSU, if (menzen) 2 else 1)
        }
        val allHaveYaochu = Tiles.isYaochu(pair) && blocks.all { b ->
            if (b.kind == Kind.SEQ) b.tile % 9 == 0 || b.tile % 9 == 6 else Tiles.isYaochu(b.tile)
        }
        if (allHaveYaochu && seqs.isNotEmpty()) {
            y += if (all.none { Tiles.isHonor(it) }) YakuItem(Yaku.JUNCHAN, if (menzen) 3 else 2)
            else YakuItem(Yaku.CHANTA, if (menzen) 2 else 1)
        }
        if (trips.size == 4) y += YakuItem(Yaku.TOITOI, 2)
        if (ankou == 3) y += YakuItem(Yaku.SANANKOU, 2)
        if ((0..8).any { r -> (0..2).all { s -> trips.any { it.tile == s * 9 + r } } }) y += YakuItem(Yaku.SANSHOKU_DOUKOU, 2)
        if (kans == 3) y += YakuItem(Yaku.SANKANTSU, 2)
        if (dragonTrips == 2 && Tiles.isDragon(pair)) y += YakuItem(Yaku.SHOUSANGEN, 2)
        y += tileYaku(all, menzen)

        val (yaku, yakuHan) = withDora(y, sit)

        // 符
        val fuItems = mutableListOf<FuItem>()
        val fu: Int
        if (pinfu && sit.tsumo) {
            fuItems += FuItem(FuKind.PINFU_TSUMO, 20)
            fu = 20
        } else {
            fuItems += FuItem(FuKind.BASE, 20)
            if (menzen && !sit.tsumo) fuItems += FuItem(FuKind.MENZEN_RON, 10)
            if (sit.tsumo && !pinfu) fuItems += FuItem(FuKind.TSUMO, 2)
            for (b in trips) {
                var f = 2
                if (Tiles.isYaochu(b.tile)) f *= 2
                if (!b.open) f *= 2
                if (b.kan) f *= 4
                val kind = when {
                    b.kan && b.open -> FuKind.MINKAN
                    b.kan -> FuKind.ANKAN
                    b.open -> FuKind.MINKOU
                    else -> FuKind.ANKOU
                }
                fuItems += FuItem(kind, f, b.tile)
            }
            var pairFu = 0
            if (Tiles.isDragon(pair)) pairFu += 2
            if (pair == roundWindTile) pairFu += 2
            if (pair == seatWindTile) pairFu += 2
            if (pairFu > 0) fuItems += FuItem(FuKind.PAIR, pairFu, pair)
            wait.fu?.let { fuItems += FuItem(it, 2) }
            val raw = fuItems.sumOf { it.fu }
            var rounded = (raw + 9) / 10 * 10
            if (rounded == 20 && !menzen) {
                fuItems += FuItem(FuKind.OPEN_PINFU, 10)
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

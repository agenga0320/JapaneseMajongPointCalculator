package com.mahjong.score.model

/** 一手和了的支付額。ron = 榮和點數；tsumoDealer / tsumoChild = 自摸時親 / 子各自支付的點數 */
data class Payment(val ron: Int, val tsumoDealer: Int, val tsumoChild: Int)

object Scoring {
    val FU_OPTIONS = listOf(20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110)

    /** 基本點。yakuman > 0 時為役滿倍數；han >= 13 視為累計役滿 */
    fun basePoints(han: Int, fu: Int, yakuman: Int = 0, kiriage: Boolean = false): Int {
        if (yakuman > 0) return 8000 * yakuman
        return when {
            han >= 13 -> 8000
            han >= 11 -> 6000
            han >= 8 -> 4000
            han >= 6 -> 3000
            han == 5 -> 2000
            else -> {
                val b = fu * (1 shl (han + 2))
                if (b >= 2000 || (kiriage && b >= 1920)) 2000 else b
            }
        }
    }

    fun limitName(han: Int, fu: Int, yakuman: Int = 0, kiriage: Boolean = false): String? {
        if (yakuman >= 2) return "${yakuman}倍役滿"
        if (yakuman == 1) return "役滿"
        return when (basePoints(han, fu, 0, kiriage)) {
            8000 -> "累計役滿"
            6000 -> "三倍滿"
            4000 -> "倍滿"
            3000 -> "跳滿"
            2000 -> "滿貫"
            else -> null
        }
    }

    private fun up100(x: Int) = (x + 99) / 100 * 100

    fun payment(han: Int, fu: Int, yakuman: Int, dealer: Boolean, kiriage: Boolean = false): Payment {
        val b = basePoints(han, fu, yakuman, kiriage)
        return if (dealer) Payment(up100(b * 6), 0, up100(b * 2))
        else Payment(up100(b * 4), up100(b * 2), up100(b))
    }

    fun handLabel(han: Int, fu: Int, yakuman: Int, kiriage: Boolean = false): String {
        val limit = limitName(han, fu, yakuman, kiriage)
        return when {
            yakuman > 0 -> limit!!
            han >= 5 -> "${han}翻 $limit"
            limit != null -> "${han}翻${fu}符 $limit"
            else -> "${han}翻${fu}符"
        }
    }

    /** 自摸的表記：親「4000 all」、子「子支付/親支付」 */
    fun tsumoText(p: Payment, dealer: Boolean) =
        if (dealer) "${p.tsumoChild} all" else "${p.tsumoChild}/${p.tsumoDealer}"
}

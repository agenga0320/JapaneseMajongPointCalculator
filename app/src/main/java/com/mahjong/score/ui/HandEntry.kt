package com.mahjong.score.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mahjong.score.model.Analysis
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.HandAnalyzer
import com.mahjong.score.model.HandResult
import com.mahjong.score.model.Meld
import com.mahjong.score.model.MeldType
import com.mahjong.score.model.Scoring
import com.mahjong.score.model.Situation
import com.mahjong.score.model.Tiles
import com.mahjong.score.model.WINDS

private val TileFace = Color(0xFFFFFBF0)
private val TileBorder = Color(0xFFB0A898)

private fun tileColor(t: Int): Color = when {
    t < 9 -> Color(0xFFC62828)
    t < 18 -> Color(0xFF1565C0)
    t < 27 -> Color(0xFF2E7D32)
    t == 31 -> Color(0xFF757575)
    t == 32 -> Color(0xFF2E7D32)
    t == 33 -> Color(0xFFC62828)
    else -> Color(0xFF212121)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileView(
    tile: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(4.dp)
    var m = modifier
        .alpha(if (enabled) 1f else 0.3f)
        .background(TileFace, shape)
        .border(
            if (highlight) 2.dp else 1.dp,
            if (highlight) MaterialTheme.colorScheme.secondary else TileBorder,
            shape,
        )
    if (onClick != null && enabled) m = m.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    Box(m, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Tiles.rankText(tile),
                color = tileColor(tile),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
            if (!Tiles.isHonor(tile)) {
                Text(Tiles.suitText(tile), color = tileColor(tile), fontSize = 9.sp, lineHeight = 10.sp)
            }
        }
    }
}

private val SMALL_TILE: Modifier = Modifier.size(width = 28.dp, height = 38.dp)

/**
 * 輸入手牌（門前牌＋副露）並自動判定役與符。
 * lockTsumo = true 時不可切換自摸／榮和（由和了畫面決定）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandEntryDialog(
    initial: Situation,
    lockTsumo: Boolean,
    kiriage: Boolean,
    /** 回傳判定結果、狀況、手牌（最後一張為和了牌）與副露 */
    onApply: (HandResult, Situation, List<Int>, List<Meld>) -> Unit,
    onDismiss: () -> Unit,
) {
    var hand by remember { mutableStateOf(listOf<Int>()) }
    var melds by remember { mutableStateOf(listOf<Meld>()) }
    var mode by remember { mutableStateOf<MeldType?>(null) }
    var sit by remember { mutableStateOf(initial) }

    val need = 14 - 3 * melds.size
    val used = IntArray(Tiles.COUNT).also { u ->
        hand.forEach { u[it]++ }
        melds.forEach { m -> m.tiles.forEach { u[it]++ } }
    }
    val menzen = melds.all { it.type == MeldType.ANKAN }
    val effSit = if (menzen) sit else sit.copy(riichi = false, doubleRiichi = false, ippatsu = false, tenhou = false)
    val analysis = HandAnalyzer.analyze(hand, melds, effSit)

    fun canPick(t: Int): Boolean = when (mode) {
        null -> hand.size < need && used[t] < 4
        MeldType.CHI -> melds.size < 4 && t < 27 && t % 9 <= 6 && (0..2).all { used[t + it] < 4 }
        MeldType.PON -> melds.size < 4 && used[t] <= 1
        else -> melds.size < 4 && used[t] == 0
    }

    fun pick(t: Int) {
        val m = mode
        if (m == null) hand = hand + t
        else {
            melds = melds + Meld(m, t)
            mode = null
        }
    }

    fun removeAt(i: Int) {
        hand = hand.filterIndexed { j, _ -> j != i }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { SimpleTopBar(tr("輸入手牌", "手牌入力"), onDismiss) }) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    ) {
                        // ---- 手牌 ----
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("手牌（門前）${hand.size} / $need")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { hand = emptyList(); melds = emptyList(); mode = null }) {
                                Text(tr("全部清除", "全てクリア"))
                            }
                        }
                        val full = hand.isNotEmpty() && hand.size == need
                        val body = (if (full) hand.indices.toList().dropLast(1) else hand.indices.toList())
                            .sortedBy { hand[it] }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            body.forEach { i ->
                                TileView(
                                    hand[i],
                                    SMALL_TILE,
                                    onClick = { removeAt(i) },
                                    onLongClick = { hand = hand.filterIndexed { j, _ -> j != i } + hand[i] },
                                )
                            }
                            if (full) {
                                Spacer(Modifier.width(10.dp))
                                TileView(hand.last(), SMALL_TILE, highlight = true, onClick = { removeAt(hand.lastIndex) })
                            }
                        }
                        Text(
                            if (hand.isEmpty()) tr("從下方選牌加入手牌", "下の牌をタップして手牌に追加")
                            else tr("點牌移除；長按設為和了牌（紅框為和了牌）", "タップで削除・長押しで和了牌に設定（赤枠が和了牌）"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        // ---- 副露 ----
                        SectionTitle("副露")
                        if (melds.isEmpty()) {
                            Text(tr("無（門前）", "なし（門前）"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            melds.forEachIndexed { i, m ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        m.tiles.forEach { t ->
                                            TileView(t, SMALL_TILE, onClick = { melds = melds.filterIndexed { j, _ -> j != i } })
                                        }
                                    }
                                    Text(m.type.label(LocalLang.current), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (melds.isNotEmpty()) {
                            Text(
                                tr("點副露可移除", "タップで副露を削除"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        // ---- 狀況 ----
                        SectionTitle(tr("狀況", "状況"))
                        if (!lockTsumo) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !sit.tsumo,
                                    onClick = { sit = sit.copy(tsumo = false) },
                                    label = { Text(tr("榮和", "ロン")) },
                                )
                                FilterChip(
                                    selected = sit.tsumo,
                                    onClick = { sit = sit.copy(tsumo = true) },
                                    label = { Text(tr("自摸", "ツモ")) },
                                )
                            }
                        }
                        WindChips("場風", sit.roundWind) { sit = sit.copy(roundWind = it) }
                        WindChips("自風", sit.seatWind) { sit = sit.copy(seatWind = it) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OptionChip(tr("立直", "リーチ"), sit.riichi, menzen) { sit = sit.copy(riichi = it, doubleRiichi = false) }
                            OptionChip(tr("兩立直", "ダブルリーチ"), sit.doubleRiichi, menzen) { sit = sit.copy(doubleRiichi = it, riichi = false) }
                            OptionChip(tr("一發", "一発"), sit.ippatsu, menzen && (sit.riichi || sit.doubleRiichi)) { sit = sit.copy(ippatsu = it) }
                            OptionChip(if (sit.tsumo) "海底摸月" else "河底撈魚", sit.lastTile) { sit = sit.copy(lastTile = it) }
                            OptionChip("嶺上開花", sit.rinshan, sit.tsumo) { sit = sit.copy(rinshan = it) }
                            OptionChip("搶槓", sit.chankan, !sit.tsumo) { sit = sit.copy(chankan = it) }
                            OptionChip(if (sit.dealer) "天和" else "地和", sit.tenhou, sit.tsumo && menzen) { sit = sit.copy(tenhou = it) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Stepper(tr("寶牌", "ドラ"), sit.dora) { sit = sit.copy(dora = it) }
                            Stepper(tr("赤寶牌", "赤ドラ"), sit.akadora) { sit = sit.copy(akadora = it) }
                            Stepper(tr("裏寶牌", "裏ドラ"), sit.uradora, menzen && (sit.riichi || sit.doubleRiichi)) {
                                sit = sit.copy(uradora = it)
                            }
                        }

                        // ---- 判定結果 ----
                        SectionTitle("判定結果")
                        AnalysisDetail(analysis)
                        Spacer(Modifier.height(16.dp))
                    }

                    // ---- 底部：結果摘要 + 牌選擇盤 ----
                    Surface(tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    summaryText(analysis, effSit, kiriage, LocalLang.current),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis is Analysis.Agari) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { (analysis as? Analysis.Agari)?.let { onApply(it.result, effSit, hand, melds) } },
                                    enabled = analysis is Analysis.Agari,
                                ) { Text(tr("套用", "適用")) }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                FilterChip(selected = mode == null, onClick = { mode = null }, label = { Text("手牌") })
                                MeldType.entries.forEach { t ->
                                    FilterChip(
                                        selected = mode == t,
                                        onClick = { mode = if (mode == t) null else t },
                                        enabled = melds.size < 4,
                                        label = { Text(t.label(LocalLang.current)) },
                                    )
                                }
                            }
                            if (mode == MeldType.CHI) {
                                Text(
                                    tr("請點選順子中最小的那張牌", "順子の一番小さい牌を選んでください"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                )
                            }
                            TilePalette(::canPick, ::pick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TilePalette(canPick: (Int) -> Boolean, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf((0..8), (9..17), (18..26), (27..33)).forEach { range ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                range.forEach { t ->
                    TileView(
                        t,
                        Modifier.weight(1f).aspectRatio(0.75f),
                        enabled = canPick(t),
                        onClick = { onPick(t) },
                    )
                }
                // 字牌只有 7 張，補空位讓牌的寬度與數牌一致
                repeat(9 - range.count()) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindChips(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(48.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WINDS.forEachIndexed { i, w ->
                FilterChip(selected = value == i, onClick = { onChange(i) }, label = { Text(w) })
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = checked && enabled,
        onClick = { onChange(!checked) },
        enabled = enabled,
        label = { Text(label) },
    )
}

@Composable
private fun Stepper(label: String, value: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, enabled = enabled && value > 0) { Text("−") }
        Text("$value", Modifier.width(20.dp), textAlign = TextAlign.Center)
        TextButton(onClick = { onChange((value + 1).coerceAtMost(30)) }, enabled = enabled) { Text("＋") }
    }
}

private fun summaryText(a: Analysis, sit: Situation, kiriage: Boolean, lang: AppLang): String = when (a) {
    is Analysis.Incomplete ->
        if (a.diff > 0) lang.t("還差 ${a.diff} 張", "あと ${a.diff} 枚") else lang.t("多了 ${-a.diff} 張", "${-a.diff} 枚多い")
    Analysis.NotAgari -> lang.t("未構成和了形", "和了形ではありません")
    is Analysis.NoYaku -> lang.t("無役", "役なし")
    is Analysis.Agari -> {
        val r = a.result
        val pay = Scoring.payment(r.han, r.fu, r.yakuman, sit.dealer, kiriage)
        Scoring.handLabel(r.han, r.fu, r.yakuman, kiriage, lang) + "　" +
            if (sit.tsumo) Scoring.tsumoText(pay, sit.dealer, lang) else "${pay.ron}"
    }
}

@Composable
private fun AnalysisDetail(a: Analysis) {
    val lang = LocalLang.current
    val result = when (a) {
        is Analysis.Agari -> a.result
        is Analysis.NoYaku -> a.result
        else -> null
    }
    if (result == null) {
        Text(
            when (a) {
                is Analysis.Incomplete ->
                    if (a.diff > 0) tr("還差 ${a.diff} 張牌", "あと ${a.diff} 枚必要です")
                    else tr("多了 ${-a.diff} 張牌", "${-a.diff} 枚多すぎます")
                else -> tr(
                    "這手牌無法組成 4 面子 1 雀頭、七對子或國士無雙",
                    "4面子1雀頭・七対子・国士無双のいずれにもなりません",
                )
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (a is Analysis.NoYaku) {
        Text(
            tr("無役：沒有任何役（寶牌不算役），不能和了", "役なし：役がないため和了できません（ドラは役ではありません）"),
            color = MaterialTheme.colorScheme.error,
        )
    }
    result.yaku.forEach { y ->
        DetailRow(
            y.label(lang),
            when {
                y.yakuman == 1 -> tr("役滿", "役満")
                y.yakuman > 1 -> tr("${y.yakuman}倍役滿", "${y.yakuman}倍役満")
                else -> "${y.han}翻"
            },
        )
    }
    if (result.yakuman == 0 && result.fuItems.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        result.fuItems.forEach { DetailRow(it.label(lang), "${it.fu}符") }
        DetailRow(tr("合計（十位進位）", "合計（切り上げ）"), "${result.fu}符", bold = true)
    }
    if (a is Analysis.Agari) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        DetailRow(
            "合計",
            if (result.yakuman > 0) Scoring.handLabel(13, 0, result.yakuman, false, lang) else "${result.han}翻 ${result.fu}符",
            bold = true,
        )
    }
}

@Composable
private fun DetailRow(name: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

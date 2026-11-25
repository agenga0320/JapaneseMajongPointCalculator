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
import androidx.compose.material3.FilledTonalButton
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
import com.mahjong.score.R
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mahjong.score.model.WINDS

/** 牌面圖片（FluffyStuff/riichi-mahjong-tiles，CC0），索引與 Tiles 相同：萬・筒・索 1–9、東南西北白發中 */
private val TILE_IMAGES = intArrayOf(R.drawable.tile_0, R.drawable.tile_1, R.drawable.tile_2, R.drawable.tile_3, R.drawable.tile_4, R.drawable.tile_5, R.drawable.tile_6, R.drawable.tile_7, R.drawable.tile_8, R.drawable.tile_9, R.drawable.tile_10, R.drawable.tile_11, R.drawable.tile_12, R.drawable.tile_13, R.drawable.tile_14, R.drawable.tile_15, R.drawable.tile_16, R.drawable.tile_17, R.drawable.tile_18, R.drawable.tile_19, R.drawable.tile_20, R.drawable.tile_21, R.drawable.tile_22, R.drawable.tile_23, R.drawable.tile_24, R.drawable.tile_25, R.drawable.tile_26, R.drawable.tile_27, R.drawable.tile_28, R.drawable.tile_29, R.drawable.tile_30, R.drawable.tile_31, R.drawable.tile_32, R.drawable.tile_33)

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
    var m = modifier.alpha(if (enabled) 1f else 0.3f)
    if (highlight) m = m.border(2.dp, MaterialTheme.colorScheme.secondary, shape)
    if (onClick != null && enabled) m = m.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    Image(
        painterResource(TILE_IMAGES[tile]),
        contentDescription = Tiles.rankText(tile) + if (Tiles.isHonor(tile)) "" else Tiles.suitText(tile),
        modifier = m,
        contentScale = ContentScale.Fit,
    )
}

private enum class EntryStep { CHI, PON, KAN, HAND }

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
    /** 引導模式：依序問 吃→碰→槓→手牌→和了牌；立直時不可能吃碰，從（暗）槓開始 */
    guided: Boolean = false,
    /** 標題；雙響・三響時用來標示正在輸入誰的手牌 */
    title: String? = null,
) {
    var hand by remember { mutableStateOf(listOf<Int>()) }
    var melds by remember { mutableStateOf(listOf<Meld>()) }
    var sit by remember { mutableStateOf(initial) }
    val steps = if (initial.riichi) listOf(EntryStep.KAN, EntryStep.HAND) else EntryStep.entries
    var stepIdx by remember { mutableStateOf(if (guided) 0 else steps.lastIndex) }
    val step = steps[stepIdx]
    var kanType by remember { mutableStateOf(if (initial.riichi) MeldType.ANKAN else MeldType.MINKAN) }
    fun modeOf(s: EntryStep) = when (s) {
        EntryStep.CHI -> MeldType.CHI
        EntryStep.PON -> MeldType.PON
        EntryStep.KAN -> kanType
        EntryStep.HAND -> null
    }
    var mode by remember { mutableStateOf(if (guided) modeOf(steps[0]) else null) }
    fun goTo(i: Int) {
        stepIdx = i
        mode = modeOf(steps[i])
    }

    val need = 14 - 3 * melds.size
    val done = hand.size == need
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
            // 引導模式留在同一步（可能吃兩次）；副露滿四組就直接進手牌
            if (!guided) mode = null
            else if (melds.size == 4) goTo(steps.lastIndex)
        }
    }

    fun removeAt(i: Int) {
        hand = hand.filterIndexed { j, _ -> j != i }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { SimpleTopBar(title ?: tr("輸入手牌", "手牌入力"), onDismiss) }) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    ) {
                        // 引導模式每一步只顯示該步的資訊；場風・自風・立直由對局帶入，不再顯示
                        // ---- 手牌 ----
                        if (!guided || step == EntryStep.HAND) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("手牌（門前）${hand.size} / $need")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                hand = emptyList()
                                melds = emptyList()
                                if (guided) goTo(0) else mode = null
                            }) {
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
                                // 和了牌不只靠紅框，加上文字標籤
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TileView(hand.last(), SMALL_TILE, highlight = true, onClick = { removeAt(hand.lastIndex) })
                                    Text(
                                        "和了牌",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                        Text(
                            if (hand.isEmpty()) tr(
                                "從下方選牌加入手牌，最後加入的那張就是和了牌",
                                "下の牌をタップして追加（最後に追加した牌が和了牌）",
                            )
                            else tr("點牌移除；長按設為和了牌（紅框為和了牌）", "タップで削除・長押しで和了牌に設定（赤枠が和了牌）"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        }

                        // ---- 副露 ----
                        if (!guided || step != EntryStep.HAND) {
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
                        }

                        // ---- 引導模式最後一步：寶牌與系統無法判斷的特殊役 ----
                        if (guided && done) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Stepper(tr("寶牌", "ドラ"), sit.dora) { sit = sit.copy(dora = it) }
                                Stepper(tr("赤寶牌", "赤ドラ"), sit.akadora) { sit = sit.copy(akadora = it) }
                                if (sit.riichi) Stepper(tr("裏寶牌", "裏ドラ"), sit.uradora) { sit = sit.copy(uradora = it) }
                            }
                            SectionTitle(tr("特殊役（有才選）", "特殊役（該当時のみ）"))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (sit.riichi || sit.doubleRiichi) {
                                    OptionChip(tr("兩立直", "ダブルリーチ"), sit.doubleRiichi) {
                                        sit = sit.copy(doubleRiichi = it, riichi = !it)
                                    }
                                    OptionChip(tr("一發", "一発"), sit.ippatsu) { sit = sit.copy(ippatsu = it) }
                                }
                                OptionChip(if (sit.tsumo) "海底摸月" else "河底撈魚", sit.lastTile) { sit = sit.copy(lastTile = it) }
                                if (sit.tsumo && melds.any { it.type == MeldType.MINKAN || it.type == MeldType.ANKAN }) {
                                    OptionChip("嶺上開花", sit.rinshan) { sit = sit.copy(rinshan = it) }
                                }
                                if (!sit.tsumo) OptionChip("搶槓", sit.chankan) { sit = sit.copy(chankan = it) }
                                if (sit.tsumo && melds.isEmpty()) {
                                    OptionChip(if (sit.dealer) "天和" else "地和", sit.tenhou) { sit = sit.copy(tenhou = it) }
                                }
                            }
                        }

                        // ---- 狀況 ----
                        if (!guided) {
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
                        }

                        // ---- 判定結果 ----
                        if (!guided || done) {
                            SectionTitle("判定結果")
                            AnalysisDetail(analysis)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ---- 底部：結果摘要 + 牌選擇盤 ----
                    Surface(tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                            // 引導模式中途沒有結果可套用：摘要與「套用」等手牌輸入完才出現
                            if (!guided || done) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    summaryText(analysis, effSit, kiriage, LocalLang.current),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis is Analysis.Agari) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 上一步＝拿掉和了牌，回到選和了牌
                                if (guided) TextButton(onClick = { hand = hand.dropLast(1) }) { Text(tr("上一步", "戻る")) }
                                Button(
                                    onClick = { (analysis as? Analysis.Agari)?.let { onApply(it.result, effSit, hand, melds) } },
                                    enabled = analysis is Analysis.Agari,
                                ) { Text(tr("套用", "適用")) }
                            }
                            if (guided) {
                                if (!done) {
                                val winLabel = if (sit.tsumo) tr("自摸牌", "アガリ牌") else tr("榮和牌", "アガリ牌")
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        when (step) {
                                            EntryStep.CHI -> tr("有吃嗎？點選順子最小的那張", "チーした順子の一番小さい牌を選択")
                                            EntryStep.PON -> tr("有碰嗎？點選碰的牌", "ポンした牌を選択")
                                            EntryStep.KAN -> if (initial.riichi) tr("有暗槓嗎？點選槓的牌", "暗槓した牌を選択")
                                            else tr("有槓嗎？點選槓的牌", "カンした牌を選択")
                                            EntryStep.HAND -> when {
                                                hand.size < need - 1 -> tr("選擇手牌", "手牌を選択") + " ${hand.size} / ${need - 1}"
                                                hand.size == need - 1 -> tr("選擇", "") + winLabel + tr("", "を選択")
                                                else -> ""
                                            }
                                        },
                                        Modifier.weight(1f).padding(start = 8.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        // 說明文字不用紅色：紅色在這個 App 代表放銃・和了牌・負分
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (stepIdx > 0) TextButton(onClick = { goTo(stepIdx - 1) }) { Text(tr("上一步", "戻る")) }
                                    if (step != EntryStep.HAND) {
                                        FilledTonalButton(onClick = { goTo(stepIdx + 1) }) {
                                            Text(tr("下一步", "次へ"))
                                        }
                                    }
                                }
                                if (step == EntryStep.KAN && !initial.riichi) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 8.dp)) {
                                        listOf(MeldType.MINKAN, MeldType.ANKAN).forEach { t ->
                                            FilterChip(
                                                selected = kanType == t,
                                                onClick = { kanType = t; mode = t },
                                                label = { Text(t.label(LocalLang.current)) },
                                            )
                                        }
                                    }
                                }
                                }
                            } else Row(
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
                            if (!guided && mode == MeldType.CHI) {
                                Text(
                                    tr("請點選順子中最小的那張牌", "順子の一番小さい牌を選んでください"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                )
                            }
                            // 手牌已滿時整盤都不能點，引導模式直接收起（點手牌移除一張就會再出現）
                            if (!(guided && done)) TilePalette(::canPick, ::pick)
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

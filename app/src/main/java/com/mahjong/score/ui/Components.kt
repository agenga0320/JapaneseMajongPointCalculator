package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.FuItem
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.YakuItem
import com.mahjong.score.model.HandRecord
import com.mahjong.score.model.HandResult
import com.mahjong.score.model.Meld
import com.mahjong.score.model.Scoring
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 目前的介面語言（由 App 最上層提供） */
val LocalLang = compositionLocalOf { AppLang.ZH }

/** 依目前語言選擇中文或日文字串 */
@Composable
@ReadOnlyComposable
fun tr(zh: String, ja: String): String = LocalLang.current.t(zh, ja)

fun signed(v: Int) = if (v > 0) "+$v" else "$v"
fun ptText(pt: Double) = String.format(Locale.US, "%+.1f", pt)
fun dateText(millis: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "戻る"))
                }
            }
        },
        actions = actions,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerChips(
    names: List<String>,
    selected: (Int) -> Boolean,
    onToggle: (Int) -> Unit,
    enabled: (Int) -> Boolean = { true },
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n ->
            FilterChip(
                selected = selected(i),
                onClick = { onToggle(i) },
                enabled = enabled(i),
                label = { Text(n) },
            )
        }
    }
}

/** 翻數選擇的編碼：1..13 = 翻數（13 = 累計役滿），101..103 = 役滿倍數 */
data class HandInput(
    val hanCode: Int = 1,
    val fu: Int = 30,
    val yaku: List<YakuItem> = emptyList(),
    val fuItems: List<FuItem> = emptyList(),
    val hand: List<Int> = emptyList(),
    val melds: List<Meld> = emptyList(),
) {
    val han: Int get() = if (hanCode > 100) 13 else hanCode
    val yakuman: Int get() = if (hanCode > 100) hanCode - 100 else 0
    val fuMatters: Boolean get() = hanCode < 5

    /** 手動改翻符時，手牌判定的明細與手牌就不再對應，一併清除 */
    fun manual(): HandInput = copy(yaku = emptyList(), fuItems = emptyList(), hand = emptyList(), melds = emptyList())

    companion object {
        fun fromResult(r: HandResult, hand: List<Int>, melds: List<Meld>): HandInput =
            if (r.yakuman > 0) HandInput(100 + r.yakuman.coerceAtMost(3), 30, r.yaku, r.fuItems, hand, melds)
            else HandInput(r.han.coerceAtMost(13), r.fu, r.yaku, r.fuItems, hand, melds)
    }
}

/** 役一覽的文字（寶牌附上張數） */
fun yakuText(yaku: List<YakuItem>, lang: AppLang) =
    yaku.joinToString("、") { if (it.isDora) "${it.label(lang)}${it.han}" else it.label(lang) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HanFuPicker(input: HandInput, onChange: (HandInput) -> Unit) {
    Text(tr("翻數", "翻数"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..13).forEach { h ->
            FilterChip(
                selected = input.hanCode == h,
                onClick = { onChange(input.copy(hanCode = h)) },
                label = { Text(if (h == 13) "13+" else "$h") },
            )
        }
        (1..3).forEach { y ->
            FilterChip(
                selected = input.hanCode == 100 + y,
                onClick = { onChange(input.copy(hanCode = 100 + y)) },
                label = { Text(if (y == 1) tr("役滿", "役満") else tr("${y}倍役滿", "${y}倍役満")) },
            )
        }
    }
    Text(tr("符數", "符数"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Scoring.FU_OPTIONS.forEach { f ->
            FilterChip(
                selected = input.fuMatters && input.fu == f,
                onClick = { onChange(input.copy(fu = f)) },
                enabled = input.fuMatters,
                label = { Text("$f") },
            )
        }
    }
}

@Composable
fun DeltaRow(names: List<String>, deltas: List<Int>) {
    Row(Modifier.fillMaxWidth()) {
        names.forEachIndexed { i, n ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(n, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    signed(deltas[i]),
                    color = deltaColor(deltas[i]),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun RecordItem(game: Game, record: HandRecord) {
    val names = game.names
    var showDetail by remember { mutableStateOf(false) }
    if (showDetail) RecordDetailDialog(game, record) { showDetail = false }
    Card(onClick = { showDetail = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(record.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(GameLogic.describe(record, names, game.rules, LocalLang.current), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
            DeltaRow(names, record.deltas)
        }
    }
}

@Composable
fun StandingsTable(game: Game) {
    Column {
        GameLogic.standings(game).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.rank}位", Modifier.width(40.dp), fontWeight = FontWeight.Bold)
                Text(game.names[s.player], Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.score}", Modifier.width(72.dp), textAlign = TextAlign.End)
                Text(
                    ptText(s.pt),
                    Modifier.width(64.dp),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    color = if (s.pt >= 0) plusColor() else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahjong.score.BuildConfig
import com.mahjong.score.GameViewModel
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic

@Composable
fun HomeScreen(
    vm: GameViewModel,
    onNew: () -> Unit,
    onContinue: () -> Unit,
    onCalc: () -> Unit,
    onHistory: (Long) -> Unit,
) {
    var confirmNew by remember { mutableStateOf(false) }
    val current = vm.current

    Scaffold(
        topBar = {
            SimpleTopBar("日本麻雀点数計算機", null) {
                // 切換成另一種語言
                TextButton(onClick = { vm.changeLang(if (vm.lang == AppLang.JA) AppLang.ZH else AppLang.JA) }) {
                    Text(if (vm.lang == AppLang.JA) "中文" else "日本語")
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 首頁主視覺：跟對局桌面同一塊呢布，主要動作放在上面
            item {
                val tc = tableColors()
                Column(
                    Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(tc.felt, tc.feltEdge), radius = 1200f), MaterialTheme.shapes.large).padding(20.dp),
                ) {
                    if (current != null) {
                        Text(
                            if (current.finished) tr("已結束（尚未保存）", "終了（未保存）") else current.roundTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = tc.onFelt,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            current.names.indices.forEach { p ->
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        current.names[p],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tc.onFeltMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${current.displayScore(p)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = tc.onFelt,
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = onContinue,
                            colors = ButtonDefaults.buttonColors(containerColor = tc.gold, contentColor = tc.onGold),
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) { Text(tr("繼續", "続ける"), style = MaterialTheme.typography.titleMedium) }
                        // 有進行中的半莊時「繼續」才是主要動作，新半莊降為呢布上的文字按鈕
                        TextButton(
                            onClick = { confirmNew = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = tc.onFeltMuted),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(tr("開始新半莊", "新しい半荘を始める")) }
                    } else {
                        Text(
                            tr("記錄一場半莊", "半荘を記録する"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = tc.onFelt,
                        )
                        Text(
                            tr("手機放在桌子中間，四家一起用", "スマホを卓の真ん中に置いて、4人で使えます"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tc.onFeltMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(
                            onClick = onNew,
                            colors = ButtonDefaults.buttonColors(containerColor = tc.gold, contentColor = tc.onGold),
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) { Text(tr("開始新半莊", "新しい半荘を始める"), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onCalc, modifier = Modifier.fillMaxWidth()) { Text(tr("點數速查", "点数早見表")) }
            }
            item { SectionTitle(tr("歷史紀錄", "対局履歴")) }
            if (vm.history.isEmpty()) {
                item { Text(tr("尚無紀錄", "履歴はありません"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(vm.history, key = { it.id }) { g -> HistoryCard(g) { onHistory(g.id) } }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("版本", "バージョン") + " v${BuildConfig.VERSION_NAME}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { vm.checkUpdate(manual = true) }, enabled = !vm.checkingUpdate) {
                        Text(if (vm.checkingUpdate) tr("檢查中…", "確認中…") else tr("檢查更新", "更新を確認"))
                    }
                }
            }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text(tr("開始新半莊", "新しい半荘")) },
            text = {
                Text(
                    tr(
                        "目前進行中的半莊尚未保存，開始新半莊後將被捨棄。",
                        "進行中の半荘は保存されていません。新しい半荘を始めると破棄されます。",
                    )
                )
            },
            confirmButton = { TextButton(onClick = { confirmNew = false; onNew() }) { Text(tr("繼續", "続ける")) } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text(tr("取消", "キャンセル")) } },
        )
    }
}

@Composable
private fun HistoryCard(game: Game, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${dateText(game.startedAt)}　${game.records.size}局",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                GameLogic.standings(game).forEach { s ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${s.rank}. ${game.names[s.player]}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            ptText(s.pt),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (s.pt >= 0) plusColor() else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

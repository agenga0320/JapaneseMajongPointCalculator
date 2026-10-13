package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahjong.score.GameViewModel
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic

@Composable
fun GameScreen(
    vm: GameViewModel,
    onWin: () -> Unit,
    onDraw: () -> Unit,
    onArchived: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val game = vm.current ?: return
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    val lang = LocalLang.current
    Scaffold(
        topBar = {
            SimpleTopBar(if (game.finished) tr("半莊結束", "半荘終了") else game.roundTitle, onBack) {
                if (game.records.isNotEmpty()) ShareRecordAction(game)
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "供託 ${game.state.kyotaku} 本　" +
                        tr("已打 ${game.records.size} 局", "${game.records.size} 局終了"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { PlayerGrid(game) }

            if (game.finished) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("最終結算", "最終結果"), style = MaterialTheme.typography.titleMedium)
                            if (game.state.kyotaku > 0) {
                                Text(
                                    tr(
                                        "剩餘供託 ${game.state.kyotaku * 1000} 點歸第1名",
                                        "残り供託 ${game.state.kyotaku * 1000} 点はトップへ",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            StandingsTable(game)
                            Button(
                                onClick = { vm.archive()?.let(onArchived) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text(tr("保存紀錄", "記録を保存")) }
                            OutlinedButton(
                                onClick = { vm.undo() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (game.manualEnd) tr("取消結束，繼續對局", "終了を取り消して続ける")
                                    else tr("復原上一局", "1局戻す")
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onWin, modifier = Modifier.weight(1f)) { Text("和了") }
                        Button(onClick = onDraw, modifier = Modifier.weight(1f)) { Text("流局") }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { confirmUndo = true },
                            enabled = game.records.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text(tr("復原上一局", "1局戻す")) }
                        OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.weight(1f)) {
                            Text(tr("結束半莊", "半荘を終了"))
                        }
                    }
                }
            }

            if (game.records.isNotEmpty()) item { SectionTitle(tr("對局紀錄", "対局記録")) }
            items(game.records.reversed()) { RecordItem(game, it) }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(tr("結束半莊", "半荘を終了")) },
            text = { Text(tr("以目前點數結算本場半莊？", "現在の点数で半荘を精算しますか？")) },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.endManually() }) { Text(tr("結束", "終了")) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(tr("取消", "キャンセル")) } },
        )
    }
    if (confirmUndo) {
        val last = game.records.lastOrNull()
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(tr("復原上一局", "1局戻す")) },
            text = { Text(last?.let { "${it.title}\n${GameLogic.describe(it, game.names, game.rules, lang)}" } ?: "") },
            confirmButton = { TextButton(onClick = { confirmUndo = false; vm.undo() }) { Text(tr("復原", "戻す")) } },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text(tr("取消", "キャンセル")) } },
        )
    }
}

@Composable
private fun PlayerGrid(game: Game) {
    val order = GameLogic.order(game.state.scores)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(listOf(0, 1), listOf(3, 2)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> PlayerCard(game, p, order.indexOf(p) + 1, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlayerCard(game: Game, p: Int, rank: Int, modifier: Modifier) {
    val isDealer = p == game.dealer && !game.finished
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isDealer) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    game.seatWind(p) + if (isDealer) "　親" else "",
                    fontWeight = FontWeight.Bold,
                    color = if (isDealer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text("${rank}位", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                game.names[p],
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${game.state.scores[p]}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (game.state.scores[p] < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

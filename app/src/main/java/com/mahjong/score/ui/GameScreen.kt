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

    Scaffold(topBar = { SimpleTopBar(if (game.finished) "半莊結束" else game.roundTitle, onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "供託 ${game.state.kyotaku} 本　已打 ${game.records.size} 局",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { PlayerGrid(game) }

            if (game.finished) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("最終結算", style = MaterialTheme.typography.titleMedium)
                            if (game.state.kyotaku > 0) {
                                Text(
                                    "剩餘供託 ${game.state.kyotaku * 1000} 點歸第1名",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            StandingsTable(game)
                            Button(
                                onClick = { vm.archive()?.let(onArchived) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text("保存紀錄") }
                            OutlinedButton(
                                onClick = { vm.undo() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (game.manualEnd) "取消結束，繼續對局" else "復原上一局") }
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
                        ) { Text("復原上一局") }
                        OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.weight(1f)) {
                            Text("結束半莊")
                        }
                    }
                }
            }

            if (game.records.isNotEmpty()) item { SectionTitle("對局紀錄") }
            items(game.records.reversed()) { RecordItem(game.names, it) }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("結束半莊") },
            text = { Text("以目前點數結算本場半莊？") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.endManually() }) { Text("結束") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("取消") } },
        )
    }
    if (confirmUndo) {
        val last = game.records.lastOrNull()
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("復原上一局") },
            text = { Text(last?.let { "${it.title}\n${it.detail}" } ?: "") },
            confirmButton = { TextButton(onClick = { confirmUndo = false; vm.undo() }) { Text("復原") } },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("取消") } },
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

package com.mahjong.score.ui

import androidx.compose.foundation.clickable
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

    Scaffold(topBar = { SimpleTopBar("日本麻雀点数計算機", null) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current != null) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable(onClick = onContinue),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("進行中的半莊", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (current.finished) "已結束（尚未保存）" else current.roundTitle,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                current.names.indices.joinToString("　") { "${current.names[it]} ${current.state.scores[it]}" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Button(onClick = onContinue, modifier = Modifier.padding(top = 8.dp)) { Text("繼續") }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { if (current != null) confirmNew = true else onNew() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("開始新半莊") }
            }
            item {
                OutlinedButton(onClick = onCalc, modifier = Modifier.fillMaxWidth()) { Text("點數速查") }
            }
            item { SectionTitle("歷史紀錄") }
            if (vm.history.isEmpty()) {
                item { Text("尚無紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(vm.history, key = { it.id }) { g -> HistoryCard(g) { onHistory(g.id) } }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "版本 v${BuildConfig.VERSION_NAME}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { vm.checkUpdate(manual = true) }, enabled = !vm.checkingUpdate) {
                        Text(if (vm.checkingUpdate) "檢查中…" else "檢查更新")
                    }
                }
            }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("開始新半莊") },
            text = { Text("目前進行中的半莊尚未保存，開始新半莊後將被捨棄。") },
            confirmButton = { TextButton(onClick = { confirmNew = false; onNew() }) { Text("繼續") } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryCard(game: Game, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
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

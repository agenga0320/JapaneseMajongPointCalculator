package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahjong.score.model.Game

@Composable
fun HistoryScreen(game: Game, onDelete: () -> Unit, onBack: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val r = game.rules

    Scaffold(
        topBar = {
            SimpleTopBar(dateText(game.startedAt), onBack) {
                ShareRecordAction(game)
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = tr("刪除", "削除"))
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("最終結算", "最終結果"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr(
                                "${r.startPoints}點持 ${r.returnPoints}點返　馬 ",
                                "${r.startPoints}点持ち ${r.returnPoints}点返し　ウマ ",
                            ) + (if (r.umaLarge == 0) tr("無", "なし") else "${r.umaSmall}-${r.umaLarge}"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        StandingsTable(game)
                    }
                }
            }
            if (game.records.isNotEmpty()) {
                item { SectionTitle(tr("對局紀錄（${game.records.size}局）", "対局記録（${game.records.size}局）")) }
            }
            items(game.records) { RecordItem(game, it) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("刪除紀錄", "記録を削除")) },
            text = { Text(tr("確定要刪除這場半莊的紀錄嗎？", "この半荘の記録を削除しますか？")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(tr("刪除", "削除")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消", "キャンセル")) } },
        )
    }
}

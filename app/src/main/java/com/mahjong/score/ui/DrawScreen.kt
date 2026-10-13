package com.mahjong.score.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahjong.score.GameViewModel
import com.mahjong.score.model.GameLogic

@Composable
fun DrawScreen(vm: GameViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val game = vm.current ?: return
    val names = game.names
    var tenpai by remember { mutableStateOf(setOf<Int>()) }
    var riichi by remember { mutableStateOf(setOf<Int>()) }
    var abortive by remember { mutableStateOf(false) }

    val deltas = GameLogic.drawDeltas(tenpai, riichi, abortive).toList()

    Scaffold(topBar = { SimpleTopBar("流局　${game.roundTitle}", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("途中流局")
                    Text(
                        tr(
                            "九種九牌・四風連打・四家立直等，不付不聽罰符，親連莊",
                            "九種九牌・四風連打・四家立直など。ノーテン罰符なし、親は連荘",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = abortive, onCheckedChange = { abortive = it })
            }

            if (!abortive) {
                SectionTitle(tr("聽牌者", "テンパイ者"))
                PlayerChips(
                    names,
                    selected = { it in tenpai },
                    onToggle = { p -> tenpai = if (p in tenpai) tenpai - p else tenpai + p },
                )
            }

            SectionTitle(
                tr("本局立直者（場上供託 ${game.state.kyotaku} 本）", "この局のリーチ者（供託 ${game.state.kyotaku} 本）")
            )
            PlayerChips(
                names,
                selected = { it in riichi },
                onToggle = { p ->
                    if (p in riichi) riichi = riichi - p
                    else {
                        riichi = riichi + p
                        tenpai = tenpai + p // 立直者流局時必為聽牌
                    }
                },
            )

            SectionTitle(tr("點數變動", "点数移動"))
            DeltaRow(names, deltas)
            Text(
                if (abortive || game.dealer in tenpai) tr("親（${names[game.dealer]}）連莊", "親（${names[game.dealer]}）連荘")
                else tr("輪莊", "親流れ"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Button(
                onClick = {
                    vm.draw(if (abortive) emptySet() else tenpai, riichi, abortive)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) { Text("確定") }
        }
    }
}

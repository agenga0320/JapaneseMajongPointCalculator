package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahjong.score.GameViewModel
import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.Scoring
import com.mahjong.score.model.Situation
import com.mahjong.score.model.Win

@Composable
fun WinScreen(vm: GameViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val game = vm.current ?: return
    val names = game.names
    var tsumo by remember { mutableStateOf(false) }
    var loser by remember { mutableStateOf<Int?>(null) }
    var hands by remember { mutableStateOf(mapOf<Int, HandInput>()) }
    var riichi by remember { mutableStateOf(setOf<Int>()) }
    var entryFor by remember { mutableStateOf<Int?>(null) }

    val lang = LocalLang.current
    val wins = hands.map { (p, h) -> Win(p, h.han, h.fu, h.yakuman, h.yaku) }
    val payer = if (tsumo) null else loser
    val valid = wins.isNotEmpty() && (tsumo || loser != null)
    val deltas = if (valid) GameLogic.winDeltas(game, wins, payer, riichi).toList() else null

    Scaffold(topBar = { SimpleTopBar("和了　${game.roundTitle}", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionTitle(tr("和了方式", "和了方法"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !tsumo, onClick = { tsumo = false }, label = { Text(tr("榮和", "ロン")) })
                FilterChip(
                    selected = tsumo,
                    onClick = {
                        tsumo = true
                        loser = null
                        if (hands.size > 1) hands = hands.entries.take(1).associate { it.toPair() }
                    },
                    label = { Text(tr("自摸", "ツモ")) },
                )
            }

            if (!tsumo) {
                SectionTitle("放銃者")
                PlayerChips(
                    names,
                    selected = { loser == it },
                    onToggle = { p ->
                        loser = if (loser == p) null else p
                        hands = hands - p
                    },
                )
            }

            SectionTitle(if (tsumo) "和了者" else tr("和了者（雙響・三響可複選）", "和了者（ダブロン・トリロン可）"))
            PlayerChips(
                names,
                selected = { it in hands },
                enabled = { tsumo || it != loser },
                onToggle = { p ->
                    hands = when {
                        p in hands -> hands - p
                        tsumo -> mapOf(p to HandInput())
                        else -> hands + (p to HandInput())
                    }
                },
            )

            hands.forEach { (p, h) ->
                val isDealer = p == game.dealer
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${names[p]}（${if (isDealer) "親" else "子"}）", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { entryFor = p },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(tr("輸入手牌，自動判定役・符", "手牌を入力して役・符を自動判定")) }
                        if (h.yaku.isNotEmpty()) {
                            Text(
                                yakuText(h.yaku, lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text(
                            tr("或直接選翻符", "または翻・符を直接選択"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        HanFuPicker(h) { hands = hands + (p to it.copy(yaku = emptyList())) }
                        val pay = Scoring.payment(h.han, h.fu, h.yakuman, isDealer, game.rules.kiriage)
                        Text(
                            Scoring.handLabel(h.han, h.fu, h.yakuman, game.rules.kiriage, lang) + "　" +
                                if (tsumo) tr("自摸", "ツモ") + " ${Scoring.tsumoText(pay, isDealer, lang)}"
                                else tr("榮和", "ロン") + " ${pay.ron}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            SectionTitle(
                tr("本局立直者（場上供託 ${game.state.kyotaku} 本）", "この局のリーチ者（供託 ${game.state.kyotaku} 本）")
            )
            PlayerChips(
                names,
                selected = { it in riichi },
                onToggle = { p -> riichi = if (p in riichi) riichi - p else riichi + p },
            )

            if (deltas != null) {
                SectionTitle(tr("點數變動", "点数移動"))
                DeltaRow(names, deltas)
                Text(
                    names.indices.joinToString("　") { "${names[it]} ${game.state.scores[it] + deltas[it]}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = {
                    vm.win(wins, payer, riichi)
                    onDone()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) { Text("確定") }
        }
    }

    entryFor?.let { p ->
        HandEntryDialog(
            initial = Situation(
                tsumo = tsumo,
                roundWind = game.state.kyoku / 4 % 4,
                seatWind = (p - game.dealer + 4) % 4,
                riichi = p in riichi,
            ),
            lockTsumo = true,
            kiriage = game.rules.kiriage,
            onApply = { result, sit ->
                hands = hands + (p to HandInput.fromResult(result))
                if (sit.riichi || sit.doubleRiichi) riichi = riichi + p
                entryFor = null
            },
            onDismiss = { entryFor = null },
        )
    }
}

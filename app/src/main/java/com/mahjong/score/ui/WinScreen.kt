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
import androidx.compose.runtime.key
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
fun WinScreen(
    vm: GameViewModel,
    initTsumo: Boolean,
    initWinners: Set<Int>,
    initLoser: Int?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val game = vm.current ?: return
    val names = game.names
    // 和了方式・放銃者・和了者已在對局桌面選好；這裡依序輸入每位和了者的手牌，完成後只顯示點數變動
    val tsumo = initTsumo
    val payer = if (tsumo) null else initLoser
    var hands by remember { mutableStateOf(initWinners.associateWith { HandInput() }) }
    val riichi = game.riichi // 立直在對局畫面的卡片上宣告，這裡直接套用
    var entryFor by remember { mutableStateOf(initWinners.minOrNull()) }

    val lang = LocalLang.current
    val wins = hands.map { (p, h) -> Win(p, h.han, h.fu, h.yakuman, h.yaku, h.fuItems, h.hand, h.melds) }
    val valid = hands.isNotEmpty() && hands.values.all { it.hand.isNotEmpty() }
    val deltas = if (valid) GameLogic.winDeltas(game, wins, payer, riichi).toList() else null

    Scaffold(topBar = { SimpleTopBar("和了　${game.roundTitle}", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (deltas != null) {
                // 誰和了、役、翻符與點數，一位和了者一行
                SectionTitle("和了")
                hands.toSortedMap().forEach { (p, h) ->
                    val isDealer = p == game.dealer
                    val pay = Scoring.payment(h.han, h.fu, h.yakuman, isDealer, game.rules.kiriage)
                    Text(
                        "${game.seatWind(p)}家 ${names[p]}　" +
                            Scoring.handLabel(h.han, h.fu, h.yakuman, game.rules.kiriage, lang) + "　" +
                            if (tsumo) tr("自摸", "ツモ") + " ${Scoring.tsumoText(pay, isDealer, lang)}"
                            else tr("榮和", "ロン") + " ${pay.ron}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        yakuText(h.yaku, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
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
            ) { Text(tr("記錄這一局", "この局を記録")) }
        }
    }

    // key：換下一位和了者時重新開始輸入，不沿用上一位的手牌
    entryFor?.let { p -> key(p) {
        HandEntryDialog(
            initial = Situation(
                tsumo = tsumo,
                roundWind = game.state.kyoku / 4 % 4,
                seatWind = (p - game.dealer + 4) % 4,
                riichi = p in riichi,
            ),
            lockTsumo = true,
            kiriage = game.rules.kiriage,
            onApply = { result, _, hand, melds ->
                hands = hands + (p to HandInput.fromResult(result, hand, melds))
                entryFor = hands.keys.sorted().firstOrNull { hands.getValue(it).hand.isEmpty() }
            },
            // 中途離開手牌輸入就回到對局畫面（這個畫面沒有其他可操作的內容）
            onDismiss = onBack,
            guided = true,
            // 例：南家 2 的手牌（1/2）
            title = "${game.seatWind(p)}家 ${names[p]} " + tr("的手牌", "の手牌") +
                if (hands.size > 1) "（${hands.keys.sorted().indexOf(p) + 1}/${hands.size}）" else "",
        )
    } }
}

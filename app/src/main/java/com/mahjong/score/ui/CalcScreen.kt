package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.Scoring
import com.mahjong.score.model.Situation

@Composable
fun CalcScreen(onBack: () -> Unit) {
    var dealer by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(HandInput()) }
    var showEntry by remember { mutableStateOf(false) }
    val pay = Scoring.payment(input.han, input.fu, input.yakuman, dealer)
    val lang = LocalLang.current

    Scaffold(topBar = { SimpleTopBar(tr("點數速查", "点数早見表"), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(selected = !dealer, onClick = { dealer = false }, label = { Text("子") })
                FilterChip(selected = dealer, onClick = { dealer = true }, label = { Text("親") })
            }
            OutlinedButton(
                onClick = { showEntry = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(tr("輸入手牌，自動判定役・符", "手牌を入力して役・符を自動判定")) }
            HanFuPicker(input) { input = it.manual() }

            Card(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        Scoring.handLabel(input.han, input.fu, input.yakuman, false, lang),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (input.yaku.isNotEmpty()) Text(yakuText(input.yaku, lang), style = MaterialTheme.typography.bodySmall)
                    Text(
                        tr("榮和", "ロン") + "　${pay.ron}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr("自摸", "ツモ") + "　${Scoring.tsumoText(pay, dealer, lang)}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            SectionTitle(
                if (dealer) tr("親的點數表（榮和 / 自摸）", "親の点数表（ロン / ツモ）")
                else tr("子的點數表（榮和 / 自摸）", "子の点数表（ロン / ツモ）")
            )
            FuHanTable(dealer)
            Text(
                tr(
                    "5翻 滿貫・6〜7翻 跳滿・8〜10翻 倍滿・11〜12翻 三倍滿・13翻以上 累計役滿",
                    "5翻 満貫・6〜7翻 跳満・8〜10翻 倍満・11〜12翻 三倍満・13翻以上 数え役満",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (showEntry) {
        HandEntryDialog(
            initial = Situation(seatWind = if (dealer) 0 else 1),
            lockTsumo = false,
            kiriage = false,
            onApply = { result, sit, hand, melds ->
                input = HandInput.fromResult(result, hand, melds)
                dealer = sit.dealer
                showEntry = false
            },
            onDismiss = { showEntry = false },
        )
    }
}

@Composable
private fun FuHanTable(dealer: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Text("符", Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        (1..4).forEach {
            Text("${it}翻", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider()
    Scoring.FU_OPTIONS.forEach { fu ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$fu", Modifier.width(40.dp))
            (1..4).forEach { han ->
                val impossible = han == 1 && fu <= 25
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (impossible) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val p = Scoring.payment(han, fu, 0, dealer)
                        // 20符沒有榮和（平和自摸限定）
                        Text(if (fu == 20) "—" else "${p.ron}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            // 表格空間有限，「all」以 ∀ 表示
                            Scoring.tsumoText(p, dealer, AppLang.ZH).replace(" all", "∀"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

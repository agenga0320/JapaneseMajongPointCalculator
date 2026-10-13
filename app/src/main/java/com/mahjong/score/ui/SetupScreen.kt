package com.mahjong.score.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mahjong.score.GameViewModel
import com.mahjong.score.model.Rules
import com.mahjong.score.model.WINDS

private val UMA_OPTIONS = listOf(0 to 0, 5 to 10, 10 to 20, 10 to 30, 20 to 30)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: GameViewModel, onStart: () -> Unit, onBack: () -> Unit) {
    val names = remember { mutableStateListOf(*vm.lastNames().toTypedArray()) }
    val r0 = remember { vm.lastRules() }
    var start by remember { mutableStateOf(r0.startPoints.toString()) }
    var ret by remember { mutableStateOf(r0.returnPoints.toString()) }
    var uma by remember { mutableStateOf(r0.umaSmall to r0.umaLarge) }
    var kiriage by remember { mutableStateOf(r0.kiriage) }
    var tobi by remember { mutableStateOf(r0.tobi) }
    var westEntry by remember { mutableStateOf(r0.westEntry) }

    Scaffold(topBar = { SimpleTopBar(tr("新半莊", "新しい半荘"), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionTitle(tr("玩家（依起家順）", "プレイヤー（起家順）"))
            WINDS.forEachIndexed { i, w ->
                OutlinedTextField(
                    value = names[i],
                    onValueChange = { names[i] = it },
                    label = { Text(if (i == 0) "東家（起家）" else "${w}家") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            SectionTitle(tr("規則", "ルール"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.filter(Char::isDigit) },
                    label = { Text(tr("配給原點", "配給原点")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ret,
                    onValueChange = { ret = it.filter(Char::isDigit) },
                    label = { Text(tr("返點", "返し")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(tr("馬（順位點）", "ウマ（順位点）"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UMA_OPTIONS.forEach { opt ->
                    FilterChip(
                        selected = uma == opt,
                        onClick = { uma = opt },
                        label = { Text(if (opt.second == 0) tr("無", "なし") else "${opt.first}-${opt.second}") },
                    )
                }
            }
            SwitchRow(tr("切上滿貫（4翻30符・3翻60符 算滿貫）", "切り上げ満貫（4翻30符・3翻60符を満貫）"), kiriage) { kiriage = it }
            SwitchRow(tr("擊飛（點數低於 0 即結束）", "飛び（0点未満で終了）"), tobi) { tobi = it }
            SwitchRow(
                tr(
                    "西入（南4局結束時無人達返點則進入西場，西場有人達返點即結束）",
                    "西入（南4局終了時に誰も返し点に届かなければ西場へ。西場は誰かが返し点に達した時点で終了）",
                ),
                westEntry,
            ) { westEntry = it }

            Button(
                onClick = {
                    val finalNames = names.mapIndexed { i, n -> n.trim().ifEmpty { "${WINDS[i]}家" } }
                    val startPts = start.toIntOrNull() ?: 25000
                    val rules = Rules(
                        startPoints = startPts,
                        returnPoints = ret.toIntOrNull() ?: startPts,
                        umaSmall = uma.first,
                        umaLarge = uma.second,
                        kiriage = kiriage,
                        tobi = tobi,
                        westEntry = westEntry,
                    )
                    vm.startGame(finalNames, rules)
                    onStart()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) { Text("開始") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

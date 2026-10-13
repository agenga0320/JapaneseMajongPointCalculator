package com.mahjong.score.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.HandRecord
import com.mahjong.score.model.Scoring
import com.mahjong.score.model.Win

/** 一局的詳細內容：各和了者的役・符明細、點數，以及流局資訊 */
@Composable
fun RecordDetailDialog(game: Game, record: HandRecord, onDismiss: () -> Unit) {
    val names = game.names
    val before = record.before
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                when {
                    record.legacyDetail != null -> Text(record.legacyDetail)
                    record.wins.isNotEmpty() -> record.wins.forEachIndexed { i, w ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        WinDetail(game, record, w)
                    }
                    else -> Text(GameLogic.describe(record, names, game.rules, LocalLang.current))
                }

                if (record.legacyDetail == null && record.riichi.isNotEmpty()) {
                    Text(
                        tr("立直：", "リーチ：") + record.riichi.sorted().joinToString("、") { names[it] },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "${before.honba}本場　" + tr("供託 ${before.kyotaku} 本", "供託 ${before.kyotaku} 本"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                DeltaRow(names, record.deltas)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("關閉", "閉じる")) } },
    )
}

@Composable
private fun WinDetail(game: Game, record: HandRecord, w: Win) {
    val lang = LocalLang.current
    val names = game.names
    val dealer = w.winner == record.before.kyoku % 4
    val tsumo = record.loser == null

    Text(
        "${names[w.winner]}（${if (dealer) "親" else "子"}）　" + if (tsumo) tr("自摸", "ツモ") else tr("榮和", "ロン"),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    record.loser?.let {
        Text(
            tr("放銃：${names[it]}", "放銃：${names[it]}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (w.yaku.isEmpty()) {
        Text(
            tr("手動輸入翻符（未輸入手牌，沒有役的明細）", "翻・符を手動入力（手牌未入力のため役の内訳なし）"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        Text(
            tr("役", "役"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        w.yaku.forEach { y ->
            DetailLine(
                y.label(lang),
                when {
                    y.yakuman == 1 -> tr("役滿", "役満")
                    y.yakuman > 1 -> tr("${y.yakuman}倍役滿", "${y.yakuman}倍役満")
                    else -> "${y.han}翻"
                },
            )
        }
        if (w.yakuman == 0 && w.fuItems.isNotEmpty()) {
            Text(
                "符",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            w.fuItems.forEach { DetailLine(it.label(lang), "${it.fu}符") }
            DetailLine(tr("合計（十位進位）", "合計（切り上げ）"), "${w.fu}符")
        }
    }

    val pay = Scoring.payment(w.han, w.fu, w.yakuman, dealer, game.rules.kiriage)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    DetailLine(
        Scoring.handLabel(w.han, w.fu, w.yakuman, game.rules.kiriage, lang),
        if (tsumo) Scoring.tsumoText(pay, dealer, lang) else "${pay.ron}",
        bold = true,
    )
}

@Composable
private fun DetailLine(name: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

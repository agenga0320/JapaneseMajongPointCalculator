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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import android.app.Activity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.mahjong.score.model.kyokuName
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahjong.score.GameViewModel
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic

@Composable
fun GameScreen(
    vm: GameViewModel,
    /** 和了者、是否自摸、放槍者（自摸時為 null）已在桌面選好，進入翻符輸入 */
    onWin: (tsumo: Boolean, winners: Set<Int>, loser: Int?) -> Unit,
    onArchived: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val game = vm.current ?: return
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    // 流局模式：非 null 時各家卡片改為聽牌／不聽選擇（true = 聽牌）
    var drawChoices by remember { mutableStateOf<Map<Int, Boolean>?>(null) }

    // 和了模式：非 null 時各家卡片改為榮和／自摸／放槍選擇
    var winRoles by remember { mutableStateOf<Map<Int, WinRole>?>(null) }

    fun chooseTenpai(p: Int, tenpai: Boolean) {
        drawChoices = (drawChoices ?: return) + (p to tenpai)
    }

    fun chooseRole(p: Int, role: WinRole) {
        val cur = winRoles ?: return
        winRoles = when {
            cur[p] == role -> cur - p // 再按一次取消
            // 自摸只有一家，不會有放槍
            role == WinRole.TSUMO -> mapOf(p to role)
            // 放槍只有一家
            role == WinRole.DEAL_IN -> cur.filterValues { it == WinRole.RON } + (p to role)
            else -> cur.filterValues { it != WinRole.TSUMO } + (p to role)
        }
    }

    val lang = LocalLang.current
    val table = tableColors()
    // 對局中整個畫面是暗色桌面：狀態列圖示改成淺色，離開時還原
    val view = LocalView.current
    DisposableEffect(game.finished) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = ctl?.isAppearanceLightStatusBars
        if (!game.finished) ctl?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) ctl.isAppearanceLightStatusBars = before }
    }
    Scaffold(
        containerColor = if (game.finished) MaterialTheme.colorScheme.background else table.feltEdge,
        topBar = {
            // 對局中局數已顯示在桌面中央，標題列只留返回與分享
            // 對局中標題列跟桌面同色，整個畫面是一張桌子
            val tc = tableColors()
            SimpleTopBar(
                if (game.finished) tr("半莊結束", "半荘終了") else "",
                onBack,
                containerColor = if (game.finished) Color.Unspecified else tc.feltEdge,
                iconColor = tc.onFelt,
            ) {
                // 半莊結束後才能分享
                if (game.finished && game.records.isNotEmpty()) ShareRecordAction(game)
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            // 左右不留邊，上下卡片才能碰到畫面兩端；其他項目各自加左右邊距
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // 對局中桌面撐滿整個畫面（東在最下方），對局紀錄往下捲動查看
                PlayerTable(
                    game,
                    onRiichi = vm::toggleRiichi,
                    onWin = { drawChoices = null; winRoles = emptyMap() },
                    winRoles = winRoles,
                    onChooseRole = ::chooseRole,
                    // 自摸一家，或榮和一家以上＋放槍一家，才能確定
                    onConfirmWin = {
                        val r = winRoles ?: emptyMap()
                        val tsumo = r.filterValues { it == WinRole.TSUMO }.keys
                        val ron = r.filterValues { it == WinRole.RON }.keys
                        val loser = r.filterValues { it == WinRole.DEAL_IN }.keys.firstOrNull()
                        if (tsumo.size == 1) onWin(true, tsumo, null)
                        else if (ron.isNotEmpty() && loser != null) onWin(false, ron, loser)
                        else return@PlayerTable
                        winRoles = null
                    },
                    onCancelWin = { winRoles = null },
                    // 立直者流局時必為聽牌，預先選好
                    onDraw = { winRoles = null; drawChoices = game.riichi.associateWith { true } },
                    drawChoices = drawChoices,
                    onChooseTenpai = ::chooseTenpai,
                    // 四家都選完後才能按：計算不聽罰符並進入下一局
                    onConfirmDraw = {
                        drawChoices?.takeIf { it.size == 4 }?.let { m ->
                            vm.draw(m.filterValues { it }.keys, game.riichi, abortive = false)
                            drawChoices = null
                        }
                    },
                    onAbortive = {
                        vm.draw(emptySet(), game.riichi, abortive = true)
                        drawChoices = null
                    },
                    onCancelDraw = { drawChoices = null },
                    onEnd = { confirmEnd = true },
                    onUndo = { confirmUndo = true },
                    fill = !game.finished,
                    modifier = if (game.finished) Modifier else Modifier.fillParentMaxHeight(),
                )
            }

            if (game.finished) {
                item {
                    Card(
                        Modifier.padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
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
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                Text(
                                    if (game.manualEnd) tr("取消結束，繼續對局", "終了を取り消して続ける")
                                    else tr("復原上一局", "1局戻す")
                                )
                            }
                        }
                    }
                }
            }

            if (game.records.isNotEmpty()) item {
                Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle(tr("對局紀錄", "対局記録")) }
            }
            items(game.records.reversed()) { Box(Modifier.padding(horizontal = 16.dp)) { RecordItem(game, it) } }
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

private enum class WinRole { RON, TSUMO, DEAL_IN }

/** 卡片厚度（上下卡片的高、左右卡片的寬） */
private val CARD_H = 136.dp
/** 半莊結束後版面不撐滿時，左右卡片的長度 */
private val SIDE_LEN = 280.dp

/**
 * 手機平放在桌子中央：玩家依實際座位圍坐（起家在下，逆時針為右・上・左），
 * 每張卡片轉向面對該玩家。座位固定，座風每局輪轉。中央放局數與共用的流局／結束按鈕。
 */
@Composable
private fun PlayerTable(
    game: Game,
    onRiichi: (Int) -> Unit,
    onWin: () -> Unit,
    /** 和了模式中各家的選擇；null 表示不在和了模式 */
    winRoles: Map<Int, WinRole>?,
    onChooseRole: (Int, WinRole) -> Unit,
    onConfirmWin: () -> Unit,
    onCancelWin: () -> Unit,
    onDraw: () -> Unit,
    /** 流局模式中各家的選擇（true = 聽牌）；null 表示不在流局模式 */
    drawChoices: Map<Int, Boolean>?,
    onChooseTenpai: (Int, Boolean) -> Unit,
    onConfirmDraw: () -> Unit,
    onAbortive: () -> Unit,
    onCancelDraw: () -> Unit,
    onEnd: () -> Unit,
    onUndo: () -> Unit,
    /** true：撐滿 modifier 給的高度，西貼上緣、東貼下緣、南北置中 */
    fill: Boolean,
    modifier: Modifier,
) {
    val scores = List(4) { game.displayScore(it) }
    val order = GameLogic.order(scores)
    @Composable
    fun seat(p: Int, degrees: Int) = PlayerCard(
        game, p, order.indexOf(p) + 1,
        // 上下卡片橫跨整個畫面寬；左右卡片沿著中間那一列的高度拉長
        if (degrees % 180 == 0) Modifier.fillMaxWidth().height(CARD_H).rotate(degrees.toFloat())
        else (if (fill) Modifier.fillMaxHeight() else Modifier.height(SIDE_LEN))
            .rotateFacing(degrees).padding(vertical = 4.dp).fillMaxWidth().height(CARD_H),
        onRiichi = { onRiichi(p) },
        drawMode = drawChoices != null,
        tenpai = drawChoices?.get(p),
        onChooseTenpai = { onChooseTenpai(p, it) },
        winMode = winRoles != null,
        role = winRoles?.get(p),
        onChooseRole = { onChooseRole(p, it) },
    )

    val tc = tableColors()
    // 桌面上的按鈕：主要＝金色，次要＝淺色框線，文字按鈕＝呢布淺色
    val ivoryButton = ButtonDefaults.buttonColors(
        containerColor = tc.gold, contentColor = tc.onGold,
        disabledContainerColor = tc.feltRaised, disabledContentColor = tc.onFeltMuted,
    )
    val feltText = ButtonDefaults.textButtonColors(contentColor = tc.onFeltMuted, disabledContentColor = Color(0xFF5E8C7F))
    val outlineOnFelt = ButtonDefaults.outlinedButtonColors(contentColor = tc.onFelt)
    val outlineBorder = BorderStroke(1.dp, tc.onFeltMuted)

    Column(
        // 中央亮、四周暗的呢布
        modifier.fillMaxWidth()
            .background(Brush.radialGradient(listOf(tc.felt, tc.feltEdge), radius = 1400f))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        seat(2, 180)
        // weight 只在高度固定（撐滿）時使用；結束後高度不固定，不能分配剩餘空間
        Row(if (fill) Modifier.weight(1f) else Modifier, verticalAlignment = Alignment.CenterVertically) {
            seat(3, 90)
            Column(
                Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 中央資訊面板：局數，下面是本場（百點棒）與供託（千點棒）
                Column(
                    Modifier
                        .padding(bottom = 16.dp)
                        .background(tc.panel, MaterialTheme.shapes.medium)
                        .border(1.dp, tc.cardBorder, MaterialTheme.shapes.medium)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (game.finished) tr("結束", "終了") else kyokuName(game.state.kyoku),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = tc.gold,
                    )
                    // 中央欄很窄：本場、供託各一行，點棒＋數字
                    @Composable
                    fun stickRow(thousand: Boolean, n: Int) = Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PointStick(thousand, Modifier.width(40.dp).height(8.dp))
                        Text(
                            "×$n",
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = tc.onFelt,
                        )
                    }
                    if (!game.finished) stickRow(thousand = false, game.state.honba)
                    stickRow(thousand = true, game.displayKyotaku)
                }
                val compact = PaddingValues(horizontal = 20.dp)
                val wide = Modifier.widthIn(min = 112.dp)
                if (winRoles != null) {
                    val roles = winRoles.values.toList()
                    val valid = roles == listOf(WinRole.TSUMO) ||
                        (WinRole.RON in roles && roles.count { it == WinRole.DEAL_IN } == 1)
                    Button(onClick = onConfirmWin, enabled = valid, colors = ivoryButton, contentPadding = compact, modifier = wide) {
                        Text(tr("確定", "確定"))
                    }
                    TextButton(onClick = onCancelWin, colors = feltText) { Text(tr("取消", "キャンセル")) }
                } else if (drawChoices != null) {
                    Button(
                        onClick = onConfirmDraw,
                        enabled = drawChoices.size == 4,
                        colors = ivoryButton,
                        contentPadding = compact,
                        modifier = wide,
                    ) { Text(tr("確定", "確定")) }
                    TextButton(onClick = onAbortive, colors = feltText) { Text("途中流局") }
                    TextButton(onClick = onCancelDraw, colors = feltText) { Text(tr("取消", "キャンセル")) }
                } else if (!game.finished) {
                    Button(onClick = onWin, colors = ivoryButton, contentPadding = compact, modifier = wide) {
                        Text("和了", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = onDraw,
                        colors = outlineOnFelt,
                        border = outlineBorder,
                        contentPadding = compact,
                        modifier = wide.padding(top = 8.dp),
                    ) { Text("流局", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onUndo, enabled = game.records.isNotEmpty(), colors = feltText) {
                        Text(tr("復原上一局", "1局戻す"))
                    }
                    TextButton(onClick = onEnd, colors = feltText) { Text(tr("結束半莊", "半荘を終了")) }
                }
            }
            seat(1, -90)
        }
        seat(0, 0)
    }
}
/** 旋轉內容；轉 ±90° 時同時交換佔用的寬高，讓版面以旋轉後的大小排列 */
private fun Modifier.rotateFacing(degrees: Int): Modifier =
    if (degrees % 180 == 0) rotate(degrees.toFloat())
    else layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth, maxHeight = constraints.maxWidth,
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2)
        }
    }.rotate(degrees.toFloat())

@Composable
private fun PlayerCard(
    game: Game,
    p: Int,
    rank: Int,
    modifier: Modifier,
    onRiichi: () -> Unit,
    drawMode: Boolean,
    /** 流局模式中的選擇：true 聽牌、false 不聽、null 尚未選 */
    tenpai: Boolean?,
    onChooseTenpai: (Boolean) -> Unit,
    winMode: Boolean,
    role: WinRole?,
    onChooseRole: (WinRole) -> Unit,
) {
    val isDealer = p == game.dealer && !game.finished
    val score = game.displayScore(p)
    val riichi = p in game.riichi
    val tc = tableColors()
    val red = tc.red
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = tc.card, contentColor = tc.onCard),
        border = BorderStroke(1.dp, if (isDealer) red else tc.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        // 親家：卡片上緣一條紅帶（跟著卡片轉向，面對該玩家）
        if (isDealer) Box(Modifier.fillMaxWidth().height(6.dp).background(red))
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.seatWind(p),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDealer) red else tc.onCard,
                )
                if (isDealer) Text(
                    "親",
                    Modifier.padding(start = 6.dp).background(red, MaterialTheme.shapes.small).padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Text(
                    game.names[p],
                    Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = tc.onCardMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${rank}位",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (rank == 1) tc.gold else tc.onCardMuted,
                )
            }
            Text(
                "$score",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (score < 0) red else tc.onCard,
            )
            val primary = tc.gold to tc.onGold
            val dark = tc.red to Color.White
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    winMode -> {
                        CardChip(tr("榮和", "ロン"), role == WinRole.RON, primary) { onChooseRole(WinRole.RON) }
                        CardChip(tr("自摸", "ツモ"), role == WinRole.TSUMO, primary) { onChooseRole(WinRole.TSUMO) }
                        CardChip(tr("放槍", "放銃"), role == WinRole.DEAL_IN, dark) { onChooseRole(WinRole.DEAL_IN) }
                    }
                    drawMode -> {
                        CardChip(tr("聽牌", "テンパイ"), tenpai == true, primary) { onChooseTenpai(true) }
                        CardChip(tr("不聽", "ノーテン"), tenpai == false, dark) { onChooseTenpai(false) }
                    }
                    else -> {
                        CardChip(
                            if (riichi) tr("立直中", "リーチ中") else tr("立直", "リーチ"),
                            riichi,
                            // 立直＝放出點棒：金色，跟親家的紅色分開
                            tc.gold to tc.onGold,
                            // 點數不足 1000 不能立直；已立直時再按一次可取消（按錯時）
                            enabled = !game.finished && (riichi || score >= 1000),
                            fill = false,
                            onClick = onRiichi,
                        )
                        // 立直後在卡片上擺出千點棒
                        if (riichi) PointStick(thousand = true, Modifier.padding(start = 12.dp).width(120.dp).height(14.dp))
                    }
                }
            }
        }
    }
}

/** 卡片下方的選擇按鈕；同一列的按鈕平分卡片寬度 */
@Composable
private fun RowScope.CardChip(
    label: String,
    selected: Boolean,
    selectedColors: Pair<Color, Color>,
    enabled: Boolean = true,
    fill: Boolean = true,
    onClick: () -> Unit,
) = FilterChip(
    selected = selected,
    onClick = onClick,
    enabled = enabled,
    // 單獨一顆（立直）不拉滿：左右卡片很長，拉滿會變成畫面上最大的元素
    modifier = (if (fill) Modifier.weight(1f) else Modifier.width(160.dp)).height(44.dp),
    colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = tableColors().onCard,
        disabledLabelColor = tableColors().cardBorder,
        selectedContainerColor = selectedColors.first,
        selectedLabelColor = selectedColors.second,
    ),
    border = FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = tableColors().cardBorder,
        disabledBorderColor = tableColors().feltRaised,
    ),
    label = {
        Text(
            label,
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    },
)
/** 點棒：千點棒（中央一個紅點）或百點棒（兩端各四個黑點） */
@Composable
private fun PointStick(thousand: Boolean, modifier: Modifier = Modifier.width(44.dp).height(9.dp)) {
    val red = tableColors().red
    Canvas(modifier) {
        val r = size.height / 2
        drawRoundRect(Color(0xFFF7F3EA), cornerRadius = CornerRadius(r, r))
        if (thousand) {
            drawCircle(red, radius = size.height * 0.32f, center = center)
        } else {
            val d = size.height * 0.11f
            listOf(0.18f, 0.82f).forEach { fx ->
                for (dx in listOf(-1, 1)) for (dy in listOf(-1, 1)) {
                    drawCircle(Color(0xFF2A2A2A), d, Offset(size.width * fx + dx * d * 2.2f, center.y + dy * d * 2.2f))
                }
            }
        }
    }
}
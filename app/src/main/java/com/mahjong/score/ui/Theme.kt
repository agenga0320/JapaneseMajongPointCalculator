package com.mahjong.score.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
 * 風格：麻將館。桌面是深綠呢布，卡片是象牙色（牌的顏色），紅色是親家・中・負分，金色只給立直。
 * 中性色全部帶一點暖黃（紙・骨牌），不用 Material 預設偏紫的灰。
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8DC),
    onPrimaryContainer = Color(0xFF0B2E22),
    secondary = Color(0xFFB8322A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8DCD6),
    onSecondaryContainer = Color(0xFF4A0F0A),
    tertiary = Color(0xFF7A5C12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E3B5),
    onTertiaryContainer = Color(0xFF2E2200),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF1F1C17),
    surface = Color(0xFFF6F3EC),
    onSurface = Color(0xFF1F1C17),
    surfaceVariant = Color(0xFFE8E2D6),
    onSurfaceVariant = Color(0xFF575043),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EDE4),
    surfaceContainer = Color(0xFFECE7DD),
    surfaceContainerHigh = Color(0xFFE6E0D4),
    surfaceContainerHighest = Color(0xFFE0D9CC),
    outline = Color(0xFF8C8476),
    outlineVariant = Color(0xFFD3CBBC),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF6F0E6),
    error = Color(0xFFB8322A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1B0),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF0F4F3B),
    onPrimaryContainer = Color(0xFFBFEBD8),
    secondary = Color(0xFFFFB4A9),
    onSecondary = Color(0xFF5C1410),
    secondaryContainer = Color(0xFF7C2620),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFFE3C46E),
    onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF584400),
    onTertiaryContainer = Color(0xFFFFE08F),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFE9E2D6),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFE9E2D6),
    surfaceVariant = Color(0xFF3A362E),
    onSurfaceVariant = Color(0xFFCDC5B6),
    surfaceContainerLowest = Color(0xFF100E0B),
    surfaceContainerLow = Color(0xFF1D1B16),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF2C2924),
    surfaceContainerHighest = Color(0xFF37332D),
    outline = Color(0xFF968F82),
    outlineVariant = Color(0xFF4A463D),
    inverseSurface = Color(0xFFE9E2D6),
    inverseOnSurface = Color(0xFF34302A),
    error = Color(0xFFFFB4A9),
)

/**
 * 對局桌面專用色：麻將客戶端常見的夜間牌桌——中央亮、四周暗的呢布，
 * 深色分數牌配亮色數字；金色＝主要動作與第1名，紅色＝親家與負分。
 * 桌面不分淺色／深色模式，永遠是暗的。
 */
@Immutable
data class TableColors(
    /** 呢布中央 */
    val felt: Color,
    /** 呢布四周 */
    val feltEdge: Color,
    /** 呢布上的主要文字 */
    val onFelt: Color,
    /** 呢布上的次要文字（文字按鈕），呢布色相的淺色，不用半透明白 */
    val onFeltMuted: Color,
    /** 停用按鈕底色 */
    val feltRaised: Color,
    /** 中央資訊面板 */
    val panel: Color,
    /** 分數牌 */
    val card: Color,
    val cardBorder: Color,
    val onCard: Color,
    val onCardMuted: Color,
    val gold: Color,
    val onGold: Color,
    val red: Color,
)

private val Table = TableColors(
    felt = Color(0xFF22695A),
    feltEdge = Color(0xFF0D2E27),
    onFelt = Color(0xFFF4EEDF),
    onFeltMuted = Color(0xFFA9CFC1),
    feltRaised = Color(0xFF2A5A4E),
    panel = Color(0xFF0A221C),
    card = Color(0xFF0F2C25),
    cardBorder = Color(0xFF3B7262),
    onCard = Color(0xFFF4EEDF),
    onCardMuted = Color(0xFF9FC0B4),
    gold = Color(0xFFE9C46A),
    onGold = Color(0xFF2B2000),
    red = Color(0xFFE5584B),
)

@Composable
fun tableColors(): TableColors = Table

// 卡片・按鈕統一圓角：小元件 8、卡片 16
private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

// 點數用等寬數字，分數上下對齊、跳動時不晃
private val Tnum = TextStyle(fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.merge(Tnum),
        headlineLarge = t.headlineLarge.merge(Tnum),
        headlineMedium = t.headlineMedium.merge(Tnum),
        titleLarge = t.titleLarge.merge(Tnum),
        titleMedium = t.titleMedium.merge(Tnum),
    )
}

@Composable
fun MahjongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

@Composable
fun plusColor() = MaterialTheme.colorScheme.primary

@Composable
fun deltaColor(v: Int) = when {
    v > 0 -> plusColor()
    v < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

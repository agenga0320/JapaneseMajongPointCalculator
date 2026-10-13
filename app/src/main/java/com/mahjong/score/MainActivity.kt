package com.mahjong.score

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahjong.score.ui.CalcScreen
import com.mahjong.score.ui.DrawScreen
import com.mahjong.score.ui.GameScreen
import com.mahjong.score.ui.HistoryScreen
import com.mahjong.score.ui.HomeScreen
import com.mahjong.score.ui.MahjongTheme
import com.mahjong.score.ui.SetupScreen
import com.mahjong.score.ui.UpdateDialogs
import com.mahjong.score.ui.WinScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MahjongTheme { App() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Setup : Screen
    data object Game : Screen
    data object Win : Screen
    data object Draw : Screen
    data object Calc : Screen
    data class History(val id: Long) : Screen
}

@Composable
private fun App(vm: GameViewModel = viewModel()) {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { pop() }

    UpdateDialogs(vm)

    when (val screen = stack.last()) {
        Screen.Home -> HomeScreen(
            vm,
            onNew = { push(Screen.Setup) },
            onContinue = { push(Screen.Game) },
            onCalc = { push(Screen.Calc) },
            onHistory = { push(Screen.History(it)) },
        )
        Screen.Setup -> SetupScreen(vm, onStart = { stack = listOf(Screen.Home, Screen.Game) }, onBack = ::pop)
        Screen.Game -> GameScreen(
            vm,
            onWin = { push(Screen.Win) },
            onDraw = { push(Screen.Draw) },
            onArchived = { id -> stack = listOf(Screen.Home, Screen.History(id)) },
            onBack = ::pop,
        )
        Screen.Win -> WinScreen(vm, onDone = ::pop, onBack = ::pop)
        Screen.Draw -> DrawScreen(vm, onDone = ::pop, onBack = ::pop)
        Screen.Calc -> CalcScreen(onBack = ::pop)
        is Screen.History -> {
            val game = vm.history.firstOrNull { it.id == screen.id }
            if (game == null) LaunchedEffect(screen) { pop() }
            else HistoryScreen(game, onDelete = { vm.deleteHistory(game.id); pop() }, onBack = ::pop)
        }
    }
}

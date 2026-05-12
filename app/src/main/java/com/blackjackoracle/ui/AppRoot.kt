package com.blackjackoracle.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun AppRoot(vm: GameViewModel = viewModel()) {
    AnimatedContent(
        targetState = vm.state.phase,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
        label = "phase",
    ) { phase ->
        // Exhaustive when — adding a phase forces a compile-time decision here
        // rather than silently routing through GameTableScreen.
        when (phase) {
            GamePhase.SETUP -> SetupScreen(onStart = vm::startGame)
            GamePhase.GAME_OVER -> GameOverScreen(vm)
            GamePhase.BETTING,
            GamePhase.DEALING,
            GamePhase.INSURANCE,
            GamePhase.PLAYER_TURNS,
            GamePhase.DEALER_TURN,
            GamePhase.SETTLEMENT,
            GamePhase.ROUND_END -> GameTableScreen(vm)
        }
    }
}

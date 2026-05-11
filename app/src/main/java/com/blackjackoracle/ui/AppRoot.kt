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
        when (phase) {
            GamePhase.SETUP -> SetupScreen(vm)
            GamePhase.GAME_OVER -> GameOverScreen(vm)
            else -> GameTableScreen(vm)
        }
    }
}

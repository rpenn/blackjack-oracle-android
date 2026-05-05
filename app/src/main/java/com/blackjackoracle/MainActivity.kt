package com.blackjackoracle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.service.TtsService
import com.blackjackoracle.ui.GameOverScreen
import com.blackjackoracle.ui.GameTableScreen
import com.blackjackoracle.ui.SetupScreen
import com.blackjackoracle.ui.theme.BlackjackOracleTheme
import com.blackjackoracle.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackjackOracleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val vm: GameViewModel = viewModel()
    val ctx = LocalContext.current.applicationContext
    val tts = remember { TtsService(ctx) }
    DisposableEffect(tts) {
        onDispose { tts.release() }
    }

    AnimatedContent(
        targetState = vm.state.phase,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
        label = "phase"
    ) { phase ->
        when (phase) {
            GamePhase.SETUP -> SetupScreen(vm)
            GamePhase.GAME_OVER -> GameOverScreen(vm)
            else -> GameTableScreen(vm, tts)
        }
    }
}

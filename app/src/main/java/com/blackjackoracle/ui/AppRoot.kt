package com.blackjackoracle.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackjackoracle.BuildConfig
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun AppRoot(vm: GameViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        var showSettings by remember { mutableStateOf(false) }

        AnimatedContent(
            targetState = vm.state.phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "phase",
        ) { phase ->
            // Exhaustive when — adding a phase forces a compile-time decision here
            // rather than silently routing through GameTableScreen.
            when (phase) {
                GamePhase.SETUP -> SetupScreen(
                    onStart = vm::startGame,
                    onSettings = { showSettings = true },
                )
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

        if (BuildConfig.DEBUG) {
            DebugPremiumToggle(Modifier.align(Alignment.TopStart))
        }

        val paywall = LocalPaywall.current
        val showPaywall by paywall.isPresented.collectAsState()
        var showPromo by remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = showPaywall,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            PaywallScreen(onPromoCode = { showPromo = true })
        }
        if (showPromo) {
            PromoCodeSheet(onDismiss = { showPromo = false })
        }

        if (showSettings) {
            SettingsScreen(onDismiss = { showSettings = false })
        }
    }
}

/// Debug-only chip cycling the entitlement override: REAL → ON → OFF → REAL.
@Composable
private fun DebugPremiumToggle(modifier: Modifier = Modifier) {
    val entitlements = LocalEntitlements.current
    var stage by remember { mutableIntStateOf(0) }
    val label = when (stage) {
        1 -> "PREM: ON"
        2 -> "PREM: OFF"
        else -> "PREM: REAL"
    }
    Box(
        modifier
            .statusBarsPadding()
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable {
                stage = (stage + 1) % 3
                entitlements.debugOverride = when (stage) {
                    1 -> true
                    2 -> false
                    else -> null
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

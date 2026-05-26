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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blackjackoracle.BuildConfig
import com.blackjackoracle.LocalEntitlements
import com.blackjackoracle.LocalPaywall
import com.blackjackoracle.service.billing.EntitlementStore

@Composable
fun DebugToggle(entitlements: EntitlementStore) {
    if (!BuildConfig.DEBUG) return
    val premium by entitlements.isPremium.collectAsState()
    val overrideState = entitlements.debugOverride
    val text = when (overrideState) {
        true -> "FORCE ON"
        false -> "FORCE OFF"
        null -> if (premium) "REAL (ON)" else "REAL (OFF)"
    }
    Text(
        text = text,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(4.dp)
            .clickable {
                entitlements.debugOverride = when (overrideState) {
                    null -> true
                    true -> false
                    false -> null
                }
            },
        color = Color.White
    )
}

@Composable
fun AppRoot(vm: GameViewModel = viewModel()) {
    val paywall = LocalPaywall.current
    val entitlements = LocalEntitlements.current
    val isPaywallPresented by paywall.isPresented.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
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
        
        if (isPaywallPresented) {
            PaywallScreen()
        }
        
        if (BuildConfig.DEBUG) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp)) {
                DebugToggle(entitlements)
            }
        }
    }
}

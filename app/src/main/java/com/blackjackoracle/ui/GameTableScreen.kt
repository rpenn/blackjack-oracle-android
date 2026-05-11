package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.ui.components.BottomRail
import com.blackjackoracle.ui.components.DealerArea
import com.blackjackoracle.ui.components.GameHeader
import com.blackjackoracle.ui.components.Inscription
import com.blackjackoracle.ui.components.PlayerArea
import com.blackjackoracle.ui.components.RoundEndOverlay
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun GameTableScreen(vm: GameViewModel) {
    val state = vm.state
    var showHelp by remember { mutableStateOf(false) }
    var showQuit by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BjColors.FeltCenter, BjColors.FeltEdge), radius = 900f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 256.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameHeader(
                state = state,
                onQuit = { showQuit = true },
                onHelp = { showHelp = true },
            )
            Spacer(Modifier.height(12.dp))
            DealerArea(state)
            Spacer(Modifier.height(22.dp))
            Inscription()
            Spacer(Modifier.weight(1f))
            PlayerArea(state)
            Spacer(Modifier.height(20.dp))
        }

        BottomRail(vm = vm, modifier = Modifier.align(Alignment.BottomCenter))

        if (state.phase == GamePhase.ROUND_END) {
            RoundEndOverlay(vm)
        }

        if (state.phase == GamePhase.INSURANCE) {
            InsuranceDialog(
                onTake = { vm.handleInsurance(true) },
                onDecline = { vm.handleInsurance(false) },
            )
        }

        if (showHelp) {
            HelpDialog(onDismiss = { showHelp = false })
        }

        if (showQuit) {
            QuitDialog(
                onQuit = {
                    showQuit = false
                    vm.returnToSetup()
                },
                onKeepPlaying = { showQuit = false },
            )
        }
    }
}

@Composable
private fun InsuranceDialog(
    onTake: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Insurance?") },
        text = { Text("Dealer shows an Ace. Take insurance?") },
        confirmButton = { TextButton(onClick = onTake) { Text("Take Insurance") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("No Thanks") } },
    )
}

@Composable
private fun QuitDialog(
    onQuit: () -> Unit,
    onKeepPlaying: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepPlaying,
        title = { Text("Quit Game?") },
        text = { Text("Your bankroll will be lost.") },
        confirmButton = {
            TextButton(onClick = onQuit) { Text("Quit", color = BjColors.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onKeepPlaying) { Text("Keep Playing") }
        },
    )
}

package com.blackjackoracle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.blackjackoracle.ui.components.ChipFloat
import com.blackjackoracle.ui.components.ChipFloatLabel
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
    var floats by remember { mutableStateOf<List<ChipFloat>>(emptyList()) }
    var nextFloatId by remember { mutableStateOf(0L) }
    // The bottom rail's height varies by phase (BettingControls vs WinBars +
    // ActionControls vs InfoRow only) and by system nav-bar size. Measure it
    // and use that as the column's bottom padding so the main content always
    // has the maximum possible vertical room without overlapping the rail.
    var railHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val bottomPadding = if (railHeightPx > 0) with(density) { railHeightPx.toDp() } else 256.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BjColors.FeltCenter, BjColors.FeltEdge), radius = 900f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = bottomPadding),
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

        // Floating "+$N" labels rise out of the chip stack area when the player
        // taps a chip. Anchored above the bottom rail so they read against the
        // felt rather than the wood. Box (not Column) so labels overlap and
        // each animates independently — removing one mid-flight does not shift
        // the others.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 280.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            floats.forEach { float ->
                ChipFloatLabel(float = float, onDone = { id -> floats = floats.filterNot { it.id == id } })
            }
        }

        BottomRail(
            vm = vm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { railHeightPx = it.height },
            onChipFloat = { amount, color ->
                val id = nextFloatId++
                floats = floats + ChipFloat(id = id, amount = amount, color = color)
            },
        )

        // Fade the round-end overlay in/out so the 58%-black scrim doesn't
        // pop in a single frame — that pop is the screen-wide "flicker" the
        // user sees at bust / Stand / dealer bust / Next Hand boundaries.
        AnimatedVisibility(
            visible = state.phase == GamePhase.ROUND_END,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
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
        confirmButton = { TextButton(onClick = onQuit) { Text("Quit", color = BjColors.Danger) } },
        dismissButton = { TextButton(onClick = onKeepPlaying) { Text("Keep Playing") } },
    )
}

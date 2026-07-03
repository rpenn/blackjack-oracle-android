package com.blackjackoracle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// How far above the rail's top edge the floating "+$N" labels should fly.
private val ChipFloatLift = 30.dp

@Composable
fun GameTableScreen(vm: GameViewModel) {
    val state = vm.state
    var showHelp by remember { mutableStateOf(false) }
    var showQuit by remember { mutableStateOf(false) }
    var floats by remember { mutableStateOf<List<ChipFloat>>(emptyList()) }
    val nextFloatId = remember { mutableLongStateOf(0L) }
    // The bottom rail's height varies by phase (BettingControls vs WinBars +
    // ActionControls vs InfoRow only) and by system nav-bar size. Measure it
    // and use that as the column's bottom padding so the main content always
    // has the maximum possible vertical room without overlapping the rail.
    val railHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val measured = railHeightPx.intValue > 0
    val bottomPadding = if (measured) with(density) { railHeightPx.intValue.toDp() } else 256.dp

    val tableGradient = remember {
        Brush.radialGradient(listOf(BjColors.FeltCenter, BjColors.FeltEdge), radius = 900f)
    }

    // After-loss nudge: at most once per process (latched in the VM), when a
    // round resolves with the human net negative and the user isn't premium.
    val isPremium by LocalEntitlements.current.isPremium.collectAsState()
    val paywall = LocalPaywall.current
    var showLossNudge by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.ROUND_END && !isPremium && !vm.lossNudgeShown &&
            state.roundResults.sumOf { it.net } < 0
        ) {
            vm.lossNudgeShown = true
            showLossNudge = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(tableGradient),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = bottomPadding)
                // Hide the column until the rail has reported its measured
                // height, otherwise the first frame uses a 256dp guess and the
                // content jumps when the real value lands.
                .alpha(if (measured) 1f else 0f),
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
            // While captioning in-game, gently shrink the player zone (anchored
            // at its bottom) so, on small screens, the hand risen by the taller
            // rail can't climb into the inscription. Springs back on dismiss.
            val captioning = isPremium && vm.captionsEnabled &&
                vm.captions.isActive && state.phase != GamePhase.ROUND_END
            val playerScale by animateFloatAsState(
                targetValue = if (captioning) 0.85f else 1f,
                label = "playerScale",
            )
            PlayerArea(
                state,
                Modifier.graphicsLayer {
                    scaleX = playerScale
                    scaleY = playerScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            )
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
                .padding(bottom = bottomPadding + ChipFloatLift)
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
                .onSizeChanged { railHeightPx.intValue = it.height },
            onChipFloat = { amount, color ->
                val id = nextFloatId.longValue
                nextFloatId.longValue = id + 1
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

        if (showLossNudge) {
            LossNudge(
                modifier = Modifier.align(Alignment.TopCenter),
                onClick = {
                    showLossNudge = false
                    paywall.present("after_loss_toast")
                },
                onDismiss = { showLossNudge = false },
            )
        }

        if (state.phase == GamePhase.INSURANCE) {
            InsuranceDialog(
                canAfford = vm.canAffordInsurance(),
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
private fun LossNudge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xEE1A2A1A))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Tough hand? See your exact odds with Premium",
            color = BjColors.Neutral,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss",
            tint = BjColors.Neutral.copy(alpha = 0.7f),
            modifier = Modifier.clickable { onDismiss() }.padding(8.dp),
        )
    }
}

@Composable
private fun InsuranceDialog(
    canAfford: Boolean,
    onTake: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        text = if (!canAfford) {
            { Text("Balance too low for Insurance") }
        } else {
            null
        },
        confirmButton = {
            // Disabled when the player can't afford the half-bet — the VM is
            // simultaneously running a 1.5s auto-decline timer in that case.
            TextButton(onClick = onTake, enabled = canAfford) { Text("Take Insurance") }
        },
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

package com.blackjackoracle.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.WinChance
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.AdvisorUiState
import com.blackjackoracle.viewmodel.GameViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BottomRail(
    vm: GameViewModel,
    modifier: Modifier = Modifier,
    onChipFloat: (amount: Int, color: Color) -> Unit = { _, _ -> },
) {
    val state = vm.state
    val showWinChanceLabel = vm.isHumanTurn && state.winChance != null
    val railGradient = remember {
        Brush.verticalGradient(listOf(BjColors.RailTop, BjColors.RailBottom))
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(railGradient)
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Displayed balance subtracts the pending bet so balance + bet stays
        // constant as the user stacks chips during BETTING. `pendingBet` is
        // zeroed by beginHand() once the deal fires, so during PLAYER_TURNS
        // this reduces to the raw chip count.
        InfoRow(
            chips = state.human.chips - state.human.pendingBet,
            askOliver = vm.advisorState,
            askOliverEnabled = isAskOliverEnabled(state),
            onAskOliver = vm::requestAskOliverAdvice,
            showWinChanceLabel = showWinChanceLabel,
        )
        // Only show controls in phases where the player can act. Rendering
        // ActionControls during DEALING / DEALER_TURN / SETTLEMENT / ROUND_END
        // shows every button in its disabled state (25% alpha container) —
        // that's the screen-wide "dim" the user sees at every transition.
        when (state.phase) {
            GamePhase.BETTING -> BettingControls(vm = vm, onChipFloat = onChipFloat)
            GamePhase.PLAYER_TURNS -> {
                state.winChance?.let { WinBars(it, vm.availableActions()) }
                ActionControls(vm)
            }
            else -> Unit
        }
    }
}

private fun isAskOliverEnabled(state: GameState): Boolean =
    state.phase != GamePhase.ROUND_END &&
        (state.phase != GamePhase.BETTING || state.human.pendingBet > 0)

// Info row (Balance | Oliver pill | Win chance label)

@Composable
private fun InfoRow(
    chips: Int,
    askOliver: AdvisorUiState,
    askOliverEnabled: Boolean,
    onAskOliver: () -> Unit,
    showWinChanceLabel: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BalanceCell(
            chips = chips,
            modifier = Modifier.width(86.dp),
        )
        Spacer(Modifier.weight(1f))
        OliverPill(
            isLoading = askOliver.isLoading,
            isSpeaking = askOliver.isSpeaking,
            enabled = askOliverEnabled,
            onClick = onAskOliver,
        )
        Spacer(Modifier.weight(1f))
        WinChanceLabel(
            visible = showWinChanceLabel,
            modifier = Modifier.width(86.dp),
        )
    }
}

@Composable
private fun BalanceCell(chips: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            "BALANCE",
            color = BjColors.Neutral.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "$$chips",
            color = BjColors.Accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun WinChanceLabel(visible: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (visible) {
            Text(
                "WIN CHANCE",
                color = BjColors.Neutral.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OliverPill(
    isLoading: Boolean,
    isSpeaking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painterResource(R.drawable.oliver),
            contentDescription = "Oliver",
            modifier = Modifier.size(28.dp).clip(CircleShape),
        )
        Text(
            "Ask Oliver",
            color = BjColors.Neutral.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (isSpeaking) {
            SpeakingBars(maxHeight = 14.dp)
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = BjColors.Accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

//Round-end overlay's Oliver button (full-width, keeps the rich style)

@Composable
private fun OliversHootButton(vm: GameViewModel) {
    val hootState = vm.hootState
    OliverAdvisorButton(
        title = "Oliver's Hoot",
        statusLabel = hootState.statusLabel,
        isLoading = hootState.isLoading,
        isSpeaking = hootState.isSpeaking,
        enabled = vm.state.phase == GamePhase.ROUND_END,
        onClick = vm::requestOliversHoot,
    )
}

@Composable
private fun OliverAdvisorButton(
    title: String,
    statusLabel: String,
    isLoading: Boolean,
    isSpeaking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.oliver),
            contentDescription = "Oliver",
            modifier = Modifier.size(44.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = BjColors.Neutral.copy(alpha = if (enabled) 1f else 0.38f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Text(
                statusLabel,
                color = BjColors.Neutral.copy(alpha = 0.7f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        if (isSpeaking) {
            SpeakingBars(maxHeight = 22.dp, barWidth = 4.dp, spacing = 3.dp)
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = BjColors.Accent,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Speak advice",
                tint = BjColors.Neutral.copy(alpha = 0.8f),
            )
        }
    }
}

//Betting / action rows (no Balance — that's in the info row now)

@Composable
private fun BettingControls(
    vm: GameViewModel,
    onChipFloat: (amount: Int, color: Color) -> Unit,
) {
    val human = vm.state.human
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(
            onClick = { vm.updateHumanPendingBet(0) },
            colors = ButtonDefaults.buttonColors(BjColors.Danger),
        ) { Text("CLEAR") }
        Spacer(Modifier.width(24.dp))
        Button(
            onClick = { vm.confirmBetsAndDeal() },
            enabled = human.pendingBet > 0,
            colors = ButtonDefaults.buttonColors(BjColors.Accent),
        ) { Text("DEAL", color = Color.Black) }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(1, 5, 10, 25, 100, 500).forEach { value ->
            ChipButton(
                value = value,
                enabled = human.pendingBet + value <= human.chips,
                onClick = {
                    vm.updateHumanPendingBet(human.pendingBet + value)
                    onChipFloat(value, chipColor(value))
                },
            )
        }
    }
}

@Composable
private fun ActionControls(vm: GameViewModel) {
    val state = vm.state
    val actions = remember(state) { vm.availableActions() }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton("HIT", BjColors.Success, PlayerAction.Hit in actions) {
            vm.handlePlayerAction(PlayerAction.Hit)
        }
        if (PlayerAction.Split in actions) {
            ActionButton("SPLIT", BjColors.SplitYellow, true) {
                vm.handlePlayerAction(PlayerAction.Split)
            }
        }
        if (PlayerAction.Double in actions) {
            ActionButton("DOUBLE", BjColors.InfoBlue, true) {
                vm.handlePlayerAction(PlayerAction.Double)
            }
        }
        ActionButton("STAND", BjColors.Danger, PlayerAction.Stand in actions) {
            vm.handlePlayerAction(PlayerAction.Stand)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        // 48dp height matches the Material 3 minimum touch-target spec; the
        // accessibility scanner flagged 44dp on the previous rev.
        modifier = Modifier.size(width = 78.dp, height = 48.dp),
        // Default Material3 contentPadding is horizontal=24dp which leaves only
        // ~30dp of text room inside a 78dp button — long labels (DOUBLE, STAND,
        // SPLIT) get clipped. Override so the full button width is usable.
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(color, disabledContainerColor = color.copy(alpha = 0.25f)),
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
        )
    }
}

//Equity bars (label is in InfoRow above — bars only here)

@Composable
private fun WinBars(
    winChance: WinChance,
    actions: Set<PlayerAction>,
) {
    val rows = buildList<Triple<String, Double, Color>> {
        add(Triple("Hit", winChance.ifHit, BjColors.Success))
        if (PlayerAction.Split in actions && winChance.ifSplitHand != null) {
            add(Triple("Split H1", winChance.ifSplitHand, BjColors.SplitYellow))
            add(Triple("Split H2", winChance.ifSplitHand, BjColors.SplitYellow))
        }
        if (PlayerAction.Double in actions) {
            add(Triple("Double", winChance.ifDouble, BjColors.InfoBlue))
        }
        add(Triple("Stand", winChance.ifStand, BjColors.Danger))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (label, value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = BjColors.Neutral.copy(alpha = 0.78f),
                    modifier = Modifier.width(64.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                LinearProgressIndicator(
                    progress = { (value / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    color = color,
                    trackColor = BjColors.Neutral.copy(alpha = 0.18f),
                    // Material3 1.3+ defaults: kill the trailing stop-indicator
                    // dot (the colored mark at the far right of every row) and
                    // the gap between the filled bar and the track (the visual
                    // "break" at the % boundary).
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    "${value.roundToInt()}%",
                    color = color,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

//Round-end overlay

@Composable
fun RoundEndOverlay(vm: GameViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xDD101D28))
                .border(1.dp, BjColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            vm.state.roundResults.forEach { result ->
                val color = when {
                    result.net > 0 -> BjColors.Success
                    result.net < 0 -> BjColors.Danger
                    else -> BjColors.Neutral
                }
                val label = if (result.net == 0) {
                    result.outcomeLabel
                } else {
                    "${result.outcomeLabel} · $${abs(result.net)}"
                }
                Text(
                    label,
                    color = color,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(18.dp))
            OliversHootButton(vm)
            Spacer(Modifier.height(18.dp))
            GoldButton("NEXT HAND", Modifier.fillMaxWidth()) { vm.startNextHand() }
        }
    }
}

// Animated vertical bars shown while Oliver is talking — a speaker-level
// motion that reads as "sound playing" far better than a spinner. Each bar
// runs its own looping height animation with a staggered start offset, so the
// peak appears to travel left-to-right across the row (KITT-style).
@Composable
private fun SpeakingBars(
    modifier: Modifier = Modifier,
    color: Color = BjColors.Accent,
    barCount: Int = 4,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.dp,
    minHeight: Dp = 3.dp,
    maxHeight: Dp = 16.dp,
) {
    val transition = rememberInfiniteTransition(label = "speakingBars")
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { i ->
            val fraction by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 90),
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(barWidth)
                    .height(minHeight + (maxHeight - minHeight) * fraction)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color),
            )
        }
    }
}

internal fun chipColor(value: Int): Color = when (value) {
    1 -> BjColors.ChipOne
    5 -> BjColors.ChipFive
    10 -> BjColors.ChipTen
    25 -> BjColors.ChipTwentyFive
    100 -> BjColors.ChipHundred
    500 -> BjColors.ChipFiveHundred
    else -> error("unknown chip value: $value")
}

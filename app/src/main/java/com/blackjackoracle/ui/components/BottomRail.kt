package com.blackjackoracle.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.WinChance
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BottomRail(
    vm: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val state = vm.state
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(BjColors.RailTop, BjColors.RailBottom)))
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 6.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AskOliverButton(vm)
        if (state.phase == GamePhase.BETTING) {
            BettingControls(vm)
        } else {
            state.winChance?.let { WinBars(it, vm.availableActions()) }
            ActionControls(vm)
        }
    }
}

@Composable
private fun AskOliverButton(
    vm: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val state = vm.state
    val advisorState = vm.advisorState
    val enabled = state.phase != GamePhase.ROUND_END && (state.phase != GamePhase.BETTING || state.human.pendingBet > 0)
    OliverAdvisorButton(
        title = "Ask Oliver",
        statusLabel = advisorState.statusLabel,
        isLoading = advisorState.isLoading,
        enabled = enabled,
        modifier = modifier,
        onClick = vm::requestAskOliverAdvice,
    )
}

@Composable
private fun OliversHootButton(
    vm: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val hootState = vm.hootState
    OliverAdvisorButton(
        title = "Oliver's Hoot",
        statusLabel = hootState.statusLabel,
        isLoading = hootState.isLoading,
        enabled = vm.state.phase == GamePhase.ROUND_END,
        modifier = modifier,
        onClick = vm::requestOliversHoot,
    )
}

@Composable
private fun OliverAdvisorButton(
    title: String,
    statusLabel: String,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.oliver), contentDescription = "Oliver", modifier = Modifier.size(48.dp).clip(CircleShape))
        Spacer(Modifier.width(14.dp))
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
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = BjColors.Accent, strokeWidth = 2.dp)
        } else {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Speak advice",
                tint = BjColors.Neutral.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun BettingControls(vm: GameViewModel) {
    val human = vm.state.human
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(
            onClick = { vm.updateHumanPendingBet(0) },
            colors = ButtonDefaults.buttonColors(BjColors.Danger),
        ) {
            Text("CLEAR")
        }
        Spacer(Modifier.width(24.dp))
        Button(
            onClick = { vm.confirmBetsAndDeal() },
            enabled = human.pendingBet > 0,
            colors = ButtonDefaults.buttonColors(BjColors.Accent),
        ) {
            Text("DEAL", color = Color.Black)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(1, 5, 10, 25, 100, 500).forEach { value ->
            ChipButton(value, human.pendingBet + value <= human.chips) {
                vm.updateHumanPendingBet(human.pendingBet + value)
            }
        }
        Spacer(Modifier.weight(1f))
        Balance(human.chips)
    }
}

@Composable
private fun ActionControls(vm: GameViewModel) {
    val actions = vm.availableActions()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton("HIT", BjColors.Success, PlayerAction.Hit in actions) { vm.handlePlayerAction(PlayerAction.Hit) }
        if (PlayerAction.Split in actions) {
            ActionButton("SPLIT", BjColors.SplitYellow, true) { vm.handlePlayerAction(PlayerAction.Split) }
        }
        if (PlayerAction.Double in actions) {
            ActionButton("DOUBLE", BjColors.InfoBlue, true) { vm.handlePlayerAction(PlayerAction.Double) }
        }
        ActionButton("STAND", BjColors.Danger, PlayerAction.Stand in actions) { vm.handlePlayerAction(PlayerAction.Stand) }
        Spacer(Modifier.weight(1f))
        Balance(vm.state.human.chips)
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
        modifier = Modifier.size(width = 78.dp, height = 44.dp),
        colors = ButtonDefaults.buttonColors(color, disabledContainerColor = color.copy(alpha = 0.25f)),
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun WinBars(
    winChance: WinChance,
    actions: Set<PlayerAction>,
) {
    val rows = buildList {
        add("Hit" to (winChance.ifHit to BjColors.Success))
        if (PlayerAction.Split in actions && winChance.ifSplitHand != null) {
            add("Split H1" to (winChance.ifSplitHand to BjColors.SplitYellow))
            add("Split H2" to (winChance.ifSplitHand to BjColors.SplitYellow))
        }
        if (PlayerAction.Double in actions) add("Double" to (winChance.ifDouble to BjColors.InfoBlue))
        add("Stand" to (winChance.ifStand to BjColors.Danger))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(64.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "Win Chance",
                color = BjColors.Neutral.copy(alpha = 0.62f),
                modifier = Modifier.width(74.dp),
                textAlign = TextAlign.End,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        rows.forEach { (label, pair) ->
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
                    progress = { (pair.first / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    color = pair.second,
                    trackColor = BjColors.Neutral.copy(alpha = 0.18f),
                )
                Text(
                    "${pair.first.roundToInt()}%",
                    color = pair.second,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun Balance(chips: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Text("BALANCE", color = BjColors.Neutral.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
fun RoundEndOverlay(vm: GameViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xDD101D28))
                .border(1.dp, BjColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            vm.state.roundResults.forEach {
                Text(
                    if (it.net == 0) it.outcomeLabel else "${it.outcomeLabel} · $${abs(it.net)}",
                    color = if (it.net > 0) BjColors.Success else if (it.net < 0) BjColors.Danger else BjColors.Neutral,
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

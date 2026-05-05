package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.service.TtsService
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun GameTableScreen(vm: GameViewModel, tts: TtsService) {
    var showQuit by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    BackgroundGradientBox {
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(min = this@BoxWithConstraints.maxHeight)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TableHeader(vm, onQuit = { showQuit = true }, onHelp = { showHelp = true })
                        DealerRow(vm)
                        AIPlayersRow(vm)
                        Spacer(modifier = Modifier.weight(1f))
                        BottomStack(vm = vm, tts = tts)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }

            if (vm.state.phase == GamePhase.INSURANCE) {
                InsuranceDialog(vm)
            }
            if (vm.state.phase == GamePhase.ROUND_END) {
                RoundEndOverlay(vm = vm, tts = tts)
            }
        }
    }

    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text("Quit Game?") },
            text = { Text("Your bankroll will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    vm.returnToSetup()
                }) { Text("Quit", color = BjColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text("Keep Playing") }
            }
        )
    }

    if (showHelp) {
        HowToPlayDialog(onDismiss = { showHelp = false })
    }
}

@Composable
private fun TableHeader(vm: GameViewModel, onQuit: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onQuit) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Quit",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Hand ${vm.state.currentRound}",
                color = Color.White.copy(alpha = 0.7f),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                vm.state.phase.displayName(),
                color = BjColors.Accent,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onHelp) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "Help",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DealerRow(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(BjColors.FeltTop, BjColors.FeltBottom),
                    radius = 800f
                )
            )
            .border(1.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "DEALER",
            color = Color.White.copy(alpha = 0.7f),
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        )
        val dealerCardCount = vm.state.dealerCards.size
        val dealerCardW = if (dealerCardCount > 5) 44.dp else 56.dp
        val dealerCardH = if (dealerCardCount > 5) 63.dp else 80.dp
        val dealerOverlap = if (dealerCardCount > 5) (-18).dp else (-22).dp
        Row(horizontalArrangement = Arrangement.spacedBy(dealerOverlap)) {
            for ((i, c) in vm.state.dealerCards.withIndex()) {
                val faceDown = i == 1 && !vm.state.dealerHoleRevealed
                CardView(card = c, faceDown = faceDown, width = dealerCardW, height = dealerCardH)
            }
        }
        if (vm.state.dealerHoleRevealed) {
            val ev = HandEvaluator.evaluate(vm.state.dealerCards)
            if (ev.isBust) {
                Text(
                    "Dealer Busts! 💥",
                    color = BjColors.Danger,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                )
            } else {
                Text(
                    ev.displayString(),
                    color = BjColors.AccentSoft,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
            }
        } else if (vm.state.dealerCards.isNotEmpty()) {
            Text(
                "shows ${vm.state.dealerCards[0].displayString}",
                color = Color.White.copy(alpha = 0.6f),
                style = TextStyle(fontSize = 12.sp)
            )
        }
        vm.state.lastAction?.let { la ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${la.playerName} ${la.action}",
                    color = Color.White.copy(alpha = 0.75f),
                    style = TextStyle(fontSize = 11.sp)
                )
            }
        }
    }
}

@Composable
private fun AIPlayersRow(vm: GameViewModel) {
    val ais = vm.state.players.withIndex().filter { !it.value.isHuman }
    if (ais.isEmpty()) return
    val compact = ais.size >= 4
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (compact) Arrangement.spacedBy(10.dp)
        else Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        for ((index, p) in ais) {
            Box(modifier = if (compact) Modifier else Modifier.weight(1f)) {
                PlayerSeat(
                    player = p,
                    isCurrent = vm.state.phase == GamePhase.PLAYER_TURNS &&
                        vm.state.currentPlayerIndex == index,
                    revealAll = true,
                    compact = compact,
                    modifier = Modifier.wrapContentSize()
                )
            }
        }
    }
}

@Composable
private fun BottomStack(vm: GameViewModel, tts: TtsService) {
    val human = vm.humanPlayer ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Human cards + chips + win-chance
        Column(
            modifier = Modifier.fillMaxWidth().glassCard().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (human.hands.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(-22.dp)) {
                            repeat(2) {
                                CardView(card = null, faceDown = true, width = 50.dp, height = 72.dp)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for ((i, h) in human.hands.withIndex()) {
                                HandView(
                                    hand = h,
                                    cardWidth = 50.dp,
                                    cardHeight = 72.dp,
                                    isActive = vm.state.phase == GamePhase.PLAYER_TURNS &&
                                        i == human.activeHandIndex,
                                    revealAll = true
                                )
                            }
                        }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        human.name,
                        color = Color.White,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        "$${human.chips}",
                        color = BjColors.Accent,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    )
                    if (human.insuranceBet > 0) {
                        Text(
                            "ins. $${human.insuranceBet}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = TextStyle(fontSize = 11.sp)
                        )
                    }
                }
            }
            // Win chance bars (only when there's an active hand and dealer up-card)
            if (vm.isHumanTurn) {
                WinChanceBars(vm.state.winChance)
            }
        }

        // Advisor + (action or betting) controls
        AdvisorButton(vm = vm, tts = tts)
        when (vm.state.phase) {
            GamePhase.BETTING -> BettingControls(vm)
            GamePhase.PLAYER_TURNS -> ActionControls(vm)
            GamePhase.INSURANCE -> {
                // The InsuranceDialog over the table handles the choice; show stand-in panel.
                ActionControls(vm)
            }
            else -> ActionControls(vm)
        }
    }
}

@Composable
private fun InsuranceDialog(vm: GameViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Insurance?") },
        text = {
            val human = vm.humanPlayer
            val main = human?.hands?.firstOrNull()?.bet ?: 0
            val side = main / 2
            Text(
                "The dealer is showing an Ace. Take insurance for $$side? Pays 2:1 if the dealer has blackjack.\n\n(Generally a poor bet — Oliver does not recommend it.)"
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.handleInsurance(take = true) }) {
                Text("Take Insurance", color = BjColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.handleInsurance(take = false) }) {
                Text("No Thanks", color = Color.White)
            }
        },
        containerColor = Color(0xFF1C2A36),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun RoundEndOverlay(vm: GameViewModel, tts: TtsService) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f))
            .clickable(enabled = false) { }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(30.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF14202B).copy(alpha = .80f))
                .border(1.dp, BjColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show only the human's results prominently
            val human = vm.humanPlayer
            val humanResults = vm.state.roundResults.filter {
                it.playerName == (human?.name ?: "")
            }
            if (humanResults.isEmpty()) {
                Text(
                    "Round Complete",
                    color = BjColors.Accent,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )
            } else {
                for (r in humanResults) {
                    val color = if (r.net > 0) BjColors.Success
                    else if (r.net < 0) BjColors.Danger
                    else BjColors.Neutral
                    val sign = if (r.net > 0) "+" else ""
                    Text(
                        r.outcomeLabel,
                        color = color,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "${r.handTotal} · $sign$${r.net}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = TextStyle(fontSize = 14.sp)
                    )
                }
            }
            AdvisorButton(vm = vm, tts = tts, showHoot = true)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                )
                .clickable { vm.startNextHand() }
                .padding(horizontal = 30.dp, vertical = 12.dp)
        ) {
            Text(
                "Next Hand",
                color = Color.Black,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

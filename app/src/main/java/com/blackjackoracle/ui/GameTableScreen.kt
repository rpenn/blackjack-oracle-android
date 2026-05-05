package com.blackjackoracle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.service.TtsService
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Felt & wood palette ─────────────────────────────────────────────────────

private val FeltLight = Color(0xFF1E7A38)
private val FeltDark  = Color(0xFF0D3F1A)
private val WoodLight = Color(0xFF6A3A15)
private val WoodDark  = Color(0xFF3C1A08)
private val GoldEdge  = Color(0xFFD1A445)

private fun chipColor(value: Int): Color = when (value) {
    1    -> Color(0xFFE0E0E0)
    5    -> Color(0xFFD72020)
    10   -> Color(0xFF233EC8)
    25   -> Color(0xFF1D9E3C)
    100  -> Color(0xFF851996)
    500  -> Color(0xFF1A1A1A)
    else -> Color.Gray
}

// ─── Floating chip label model ────────────────────────────────────────────────

private data class ChipFloat(val id: Long = System.nanoTime(), val amount: Int)

// ─── Root composable ─────────────────────────────────────────────────────────

@Composable
fun GameTableScreen(vm: GameViewModel, tts: TtsService) {
    var showQuit by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    // Chip bounce: scale target for the pending-bet chip stack
    var chipScaleTarget by remember { mutableStateOf(1f) }
    val chipScale by animateFloatAsState(
        targetValue = chipScaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "chipScale"
    )
    // Auto-reset scale back to 1.0 after the bounce peak
    LaunchedEffect(chipScaleTarget) {
        if (chipScaleTarget > 1f) {
            delay(200)
            chipScaleTarget = 1f
        }
    }

    // Floating "+$N" labels
    var chipFloats by remember { mutableStateOf(emptyList<ChipFloat>()) }

    // Win-chance row: opacity-toggled so layout never shifts
    val wcVisible = vm.isHumanTurn && vm.state.winChance != null
    val wcAlpha by animateFloatAsState(
        targetValue   = if (wcVisible) 1f else 0f,
        animationSpec = tween(250),
        label         = "wcAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Felt background ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(colors = listOf(FeltLight, FeltDark), radius = 1400f)
                )
        )

        // ── Main column ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            TableHeader(vm = vm, onQuit = { showQuit = true }, onHelp = { showHelp = true })

            // Playing surface
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {

                DealerArea(vm)

                TableInscription()

                Spacer(modifier = Modifier.weight(1f))

                WoodRailLine()

                HumanZone(vm = vm, chipScale = chipScale)

                // Win-chance row — fixed height, opacity-toggled only
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(wcAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    WinChanceRow(vm.state.winChance)
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            BottomRail(
                vm           = vm,
                tts          = tts,
                onChipTapped = { amount ->
                    chipScaleTarget = 1.22f
                    val f = ChipFloat(amount = amount)
                    chipFloats = chipFloats + f
                }
            )
        }

        // ── Floating chip labels (non-blocking overlay) ──────────────────────
        Box(
            modifier         = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 50.dp, bottom = 200.dp)
                .wrapContentSize()
        ) {
            chipFloats.forEach { f ->
                key(f.id) {
                    ChipFloatLabel(float = f, onDone = {
                        chipFloats = chipFloats.filter { it.id != f.id }
                    })
                }
            }
        }

        // ── Overlays ─────────────────────────────────────────────────────────
        if (vm.state.phase == GamePhase.INSURANCE) InsuranceDialog(vm)
        if (vm.state.phase == GamePhase.ROUND_END)  RoundEndOverlay(vm, tts)
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title   = { Text("Quit Game?") },
            text    = { Text("Your bankroll will be lost.") },
            confirmButton = {
                TextButton(onClick = { showQuit = false; vm.returnToSetup() }) {
                    Text("Quit", color = BjColors.Danger)
                }
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

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun TableHeader(vm: GameViewModel, onQuit: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onQuit) {
            Icon(Icons.Filled.Close, "Quit", tint = Color.White.copy(alpha = 0.55f))
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Hand ${vm.state.currentRound}",
                color = Color.White.copy(alpha = 0.55f),
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                vm.state.phase.displayName(),
                color = Color.White.copy(alpha = 0.90f),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Chip count — permanent, never hidden, so layout never shifts
        Column(
            horizontalAlignment = Alignment.End,
            modifier            = Modifier.width(56.dp)
        ) {
            Text("CHIPS", fontSize = 8.sp, color = Color.White.copy(alpha = 0.40f))
            Text(
                "$${vm.humanPlayer?.chips ?: 0}",
                color = BjColors.Accent,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
        }

        IconButton(onClick = onHelp) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Help",
                tint = Color.White.copy(alpha = 0.55f))
        }
    }
}

// ─── Dealer area (animated cards, no shoe / discard pile) ────────────────────

@Composable
private fun DealerArea(vm: GameViewModel) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "DEALER",
            color = Color.White.copy(alpha = 0.65f),
            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        )

        // Cards with per-card slide-in animation
        val count = vm.state.dealerCards.size
        val cW    = if (count > 5) 40.dp else 50.dp
        val cH    = if (count > 5) 57.dp else 71.dp
        val gap   = if (count > 5) (-16).dp else (-20).dp

        Box(modifier = Modifier.height(72.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                vm.state.dealerCards.forEachIndexed { i, c ->
                    key(i) {
                        val faceDown = i == 1 && !vm.state.dealerHoleRevealed
                        AnimatedCardEntry(card = c, faceDown = faceDown, width = cW, height = cH)
                    }
                }
            }
        }

        // Dealer total / bust
        if (vm.state.dealerHoleRevealed) {
            val ev = HandEvaluator.evaluate(vm.state.dealerCards)
            if (ev.isBust) {
                Text("Dealer Busts! 💥", color = BjColors.Danger,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
            } else {
                Text(ev.displayString(), color = BjColors.AccentSoft,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        } else if (vm.state.dealerCards.isNotEmpty()) {
            Text(
                "shows ${vm.state.dealerCards[0].displayString}",
                color = Color.White.copy(alpha = 0.55f),
                style = TextStyle(fontSize = 10.sp)
            )
        }

        vm.state.lastAction?.let { la ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("${la.playerName} ${la.action}",
                    color = Color.White.copy(alpha = 0.70f),
                    style = TextStyle(fontSize = 9.sp))
            }
        }
    }
}

// ─── Table inscription ────────────────────────────────────────────────────────

@Composable
private fun TableInscription() {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "BLACKJACK PAYS 3 TO 2",
            color = Color.White.copy(alpha = 0.15f),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        )
        Text(
            "DEALER MUST DRAW TO 16 AND STAND ON ALL 17s",
            color = Color.White.copy(alpha = 0.10f),
            style = TextStyle(fontSize = 8.sp, letterSpacing = 0.8.sp)
        )
    }
}

// ─── Wood rail divider ────────────────────────────────────────────────────────

@Composable
private fun WoodRailLine() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp).background(
                Brush.horizontalGradient(listOf(WoodDark, WoodLight, Color(0xFF8A4B20), WoodLight, WoodDark))
            )
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GoldEdge.copy(alpha = 0.45f)))
    }
}

// ─── Human zone ───────────────────────────────────────────────────────────────

@Composable
private fun HumanZone(vm: GameViewModel, chipScale: Float) {
    val human = vm.humanPlayer ?: return
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        if (human.hands.isEmpty()) {
            // Betting phase: show animated pending-bet chip stack in the dashed ring
            Box(
                modifier         = Modifier
                    .size(110.dp, 86.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Inner Box gets the scale — doesn't affect the outer ring's layout
                Box(modifier = Modifier.scale(chipScale)) {
                    BetChipStack(amount = human.pendingBet)
                }
            }
        } else {
            // Play phase: inline card rendering with per-card deal animation
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                human.hands.forEachIndexed { i, h ->
                    val isActive = vm.state.phase == GamePhase.PLAYER_TURNS &&
                        i == human.activeHandIndex
                    val ev = HandEvaluator.evaluate(h.cards)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Cards — each new card slides in from above
                        Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
                            h.cards.forEachIndexed { ci, c ->
                                key(ci) {
                                    AnimatedCardEntry(
                                        card     = c,
                                        faceDown = false,
                                        width    = 50.dp,
                                        height   = 71.dp
                                    )
                                }
                            }
                        }

                        // Value / outcome badge (mirrors HandView style)
                        val badgeText  = h.outcome ?: ev.displayString()
                        val badgeColor = when (h.outcome) {
                            "Win", "Blackjack" -> BjColors.Success
                            "Loss", "Bust"     -> BjColors.Danger
                            "Push"             -> BjColors.Neutral
                            "Surrender"        -> Color(0xFFFF9800)
                            else -> when {
                                ev.isBust  -> BjColors.Danger
                                isActive   -> BjColors.Accent
                                else       -> Color.White.copy(alpha = 0.85f)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.40f))
                                .border(
                                    width = if (isActive) 1.5.dp else 0.5.dp,
                                    color = if (isActive) BjColors.Accent
                                            else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(badgeText, color = badgeColor,
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                        }

                        // Animated chip stack under the hand
                        BetChipStack(amount = h.bet)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Insurance badge (only when relevant)
        if (human.insuranceBet > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Text("INS", fontSize = 7.sp,
                    color = Color.White.copy(alpha = 0.50f),
                    style = TextStyle(fontWeight = FontWeight.Bold))
                Text("$${human.insuranceBet}", color = BjColors.Accent,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

// ─── Win-chance row (dedicated full-width, toggled by opacity) ────────────────

@Composable
private fun WinChanceRow(wc: com.blackjackoracle.model.WinChance?) {
    if (wc == null) return
    Column(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement   = Arrangement.spacedBy(4.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        WinBar(label = "Hit",   pct = wc.ifHit,   color = BjColors.InfoBlue)
        WinBar(label = "Stand", pct = wc.ifStand, color = BjColors.Success)
        wc.ifDouble?.let {
            WinBar(label = "Double", pct = it, color = BjColors.Accent)
        }
        wc.ifSplit?.let {
            WinBar(label = "Hand 1", pct = it, color = Color(0xFF915AE6))
            WinBar(label = "Hand 2", pct = it, color = Color(0xFF915AE6))
        }
    }
}

@Composable
private fun WinBar(label: String, pct: Double, color: Color, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue   = (pct / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(350),
        label         = "winbar_$label"
    )
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.65f),
            style = TextStyle(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(45.dp))
        Box(
            modifier = Modifier
                .weight(1f).height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Text("${pct.toInt()}%", fontSize = 9.sp,
            color = color,
            style = TextStyle(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(32.dp))
    }
}

// ─── Bottom wood rail ─────────────────────────────────────────────────────────

@Composable
private fun BottomRail(
    vm: GameViewModel,
    tts: TtsService,
    onChipTapped: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(WoodLight, WoodDark)))
            .navigationBarsPadding()
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(GoldEdge.copy(alpha = 0.5f)))

        Column(
            modifier            = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdvisorButton(vm = vm, tts = tts)

            when (vm.state.phase) {
                GamePhase.BETTING -> BettingBar(vm, onChipTapped)
                else              -> ActionBar(vm)
            }
        }
    }
}

// ─── Betting bar ──────────────────────────────────────────────────────────────

@Composable
private fun BettingBar(vm: GameViewModel, onChipTapped: (Int) -> Unit) {
    val human = vm.humanPlayer ?: return
    val bet   = human.pendingBet
    val chips = human.chips

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(1, 5, 10, 25).forEach { value ->
                CasinoChipBtn(
                    value      = value,
                    currentBet = bet,
                    maxChips   = chips,
                    onClick    = {
                        vm.updateHumanPendingBet(bet + value)
                        onChipTapped(value)
                    }
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 4.dp)
        ) {
            Text("BET", fontSize = 8.sp, color = Color.White.copy(alpha = 0.45f))
            Text("$$bet", color = BjColors.Accent,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .clickable { vm.updateHumanPendingBet(0) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Clear", color = Color.White.copy(alpha = 0.70f),
                    style = TextStyle(fontSize = 11.sp))
            }
            val dealEnabled = bet > 0
            Box(
                modifier = Modifier
                    .shadow(if (dealEnabled) 6.dp else 0.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (dealEnabled) Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                        else Brush.verticalGradient(listOf(BjColors.Neutral.copy(alpha = 0.2f), BjColors.Neutral.copy(alpha = 0.1f)))
                    )
                    .clickable(enabled = dealEnabled) { vm.confirmBetsAndDeal() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("DEAL", 
                    color = if (dealEnabled) Color(0xFF1F0C02) else Color.White.copy(alpha = 0.3f),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

// ─── Action bar (chip count in header; win chance in dedicated row above) ─────

@Composable
private fun ActionBar(vm: GameViewModel) {
    val avail  = vm.availableActions()
    val active = vm.isHumanTurn

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        TableBtn("HIT",   BjColors.InfoBlue,
            active && PlayerAction.Hit    in avail) { vm.handlePlayerAction(PlayerAction.Hit)    }
        Spacer(modifier = Modifier.width(8.dp))
        TableBtn("DBL",   BjColors.Accent,
            active && PlayerAction.Double in avail) { vm.handlePlayerAction(PlayerAction.Double)  }
        Spacer(modifier = Modifier.width(8.dp))
        TableBtn("SPLIT", Color(0xFF915AE6),
            active && PlayerAction.Split  in avail) { vm.handlePlayerAction(PlayerAction.Split)   }
        Spacer(modifier = Modifier.width(8.dp))
        TableBtn("STAND", BjColors.Success,
            active && PlayerAction.Stand  in avail) { vm.handlePlayerAction(PlayerAction.Stand)   }
    }
}

// ─── Insurance dialog ─────────────────────────────────────────────────────────

@Composable
private fun InsuranceDialog(vm: GameViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Insurance?") },
        text  = {
            val main = vm.humanPlayer?.hands?.firstOrNull()?.bet ?: 0
            val side = (main / 2).coerceAtLeast(1)
            Text(
                "The dealer is showing an Ace. Take insurance for $$side? " +
                "Pays 2:1 if the dealer has blackjack.\n\n" +
                "(Generally a poor bet — Oliver does not recommend it.)"
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
        containerColor    = Color(0xFF1C2A36),
        titleContentColor = Color.White,
        textContentColor  = Color.White.copy(alpha = 0.85f),
        shape             = RoundedCornerShape(22.dp)
    )
}

// ─── Round-end overlay ────────────────────────────────────────────────────────

@Composable
private fun RoundEndOverlay(vm: GameViewModel, tts: TtsService) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f))
            .clickable(enabled = false) { }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(30.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF14202B).copy(alpha = 0.88f))
                .border(1.dp, BjColors.Accent.copy(alpha = 0.30f), RoundedCornerShape(22.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val humanResults = vm.state.roundResults.filter {
                it.playerName == (vm.humanPlayer?.name ?: "")
            }
            if (humanResults.isEmpty()) {
                Text("Round Complete", color = BjColors.Accent,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold))
            } else {
                for (r in humanResults) {
                    val c    = if (r.net > 0) BjColors.Success else if (r.net < 0) BjColors.Danger else BjColors.Neutral
                    val sign = if (r.net > 0) "+" else ""
                    Text(r.outcomeLabel, color = c,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    Text("${r.handTotal} · $sign$$${r.net}",
                        color = Color.White.copy(alpha = 0.80f),
                        style = TextStyle(fontSize = 14.sp))
                }
            }
            AdvisorButton(vm = vm, tts = tts, showHoot = true)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
                .shadow(8.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft)))
                .clickable { vm.startNextHand() }
                .padding(horizontal = 30.dp, vertical = 12.dp)
        ) {
            Text("Next Hand", color = Color.Black,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

// ─── Animated card entry ──────────────────────────────────────────────────────

/** Wraps CardView with a slide-in-from-above deal animation on first composition. */
@Composable
private fun AnimatedCardEntry(card: Card, faceDown: Boolean, width: Dp, height: Dp) {
    val offsetY = remember { Animatable(-180f) }
    val cardAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            offsetY.animateTo(
                0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            )
        }
        cardAlpha.animateTo(1f, animationSpec = tween(durationMillis = 200))
    }

    Box(
        modifier = Modifier
            .offset(y = offsetY.value.dp)
            .alpha(cardAlpha.value)
    ) {
        CardView(card = card, faceDown = faceDown, width = width, height = height)
    }
}

// ─── Floating "+$N" chip label ────────────────────────────────────────────────

@Composable
private fun ChipFloatLabel(float: ChipFloat, onDone: () -> Unit) {
    var triggered by remember { mutableStateOf(false) }

    val offsetY by animateFloatAsState(
        targetValue   = if (triggered) -70f else 0f,
        animationSpec = tween(durationMillis = 850, easing = LinearOutSlowInEasing),
        label         = "floatY"
    )
    val floatAlpha by animateFloatAsState(
        targetValue   = if (triggered) 0f else 1f,
        animationSpec = tween(durationMillis = 850, easing = LinearOutSlowInEasing),
        label         = "floatAlpha"
    )

    LaunchedEffect(Unit) {
        triggered = true
        delay(900)
        onDone()
    }

    Text(
        "+$${float.amount}",
        color  = chipColor(float.amount).copy(alpha = floatAlpha),
        style  = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
        modifier = Modifier.offset(y = offsetY.dp)
    )
}

// ─── Bet chip stack (denomination breakdown, stacked visually) ────────────────

/** Decomposes `amount` into up to 8 denomination chips and stacks them. */
@Composable
fun BetChipStack(amount: Int) {
    val chips = remember(amount) {
        val result    = mutableListOf<Pair<Int, Color>>()
        var remaining = amount
        for (d in listOf(25, 10, 5, 1)) {
            while (remaining >= d && result.size < 8) {
                result.add(d to chipColor(d))
                remaining -= d
            }
        }
        result
    }
    if (chips.isEmpty()) return

    val stackH = 26 + (chips.size - 1) * 5  // dp

    Box(
        modifier         = Modifier.size(26.dp, stackH.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Render chips bottom-to-top (index 0 = bottom chip = widest y offset)
        chips.forEachIndexed { i, (_, color) ->
            val topOff = ((chips.size - 1 - i) * 5).dp
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .offset(y = topOff)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            )
        }
        // Amount label on the top chip (y = 0)
        Text(
            "$$amount",
            color = Color.White,
            style = TextStyle(
                fontSize   = if (amount >= 100) 5.sp else 6.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
    }
}

/** Single-chip badge — backward-compatible for any usages in other files. */
@Composable
fun BetChipView(amount: Int) {
    val col = when {
        amount < 5   -> chipColor(1)
        amount < 10  -> chipColor(5)
        amount < 25  -> chipColor(10)
        amount < 100 -> chipColor(25)
        else         -> chipColor(100)
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(col)
            .border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("$$amount", color = Color.White,
            style = TextStyle(
                fontSize   = if (amount >= 100) 6.sp else 7.sp,
                fontWeight = FontWeight.ExtraBold
            ))
    }
}

// ─── Tappable casino chip button ──────────────────────────────────────────────

@Composable
private fun CasinoChipBtn(value: Int, currentBet: Int, maxChips: Int, onClick: () -> Unit) {
    val col     = chipColor(value)
    val enabled = currentBet + value <= maxChips
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(if (enabled) 3.dp else 0.dp, CircleShape)
            .clip(CircleShape)
            .background(col.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .border(
                    1.5.dp,
                    Color.White.copy(alpha = if (enabled) 0.30f else 0.15f),
                    CircleShape
                )
        )
        Text(
            "$$value",
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            style = TextStyle(
                fontSize   = if (value >= 10) 9.sp else 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
    }
}

// ─── Square action button ─────────────────────────────────────────────────────

@Composable
private fun TableBtn(title: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 44.dp)
            .shadow(if (enabled) 4.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else color.copy(alpha = 0.25f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        )
    }
}

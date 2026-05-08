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
import androidx.compose.ui.text.style.TextAlign
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

                // WoodRailLine() removed per user request

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    HumanZone(vm = vm, chipScale = chipScale)
                }

                Spacer(modifier = Modifier.height(8.dp))
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
        val cW    = if (count > 5) 56.dp else 70.dp
        val cH    = if (count > 5) 80.dp else 100.dp
        val gap   = if (count > 5) (-22).dp else (-28).dp

        Box(modifier = Modifier.height(101.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                vm.state.dealerCards.forEachIndexed { i, c ->
                    key(i) {
                        val faceDown = i == 1 && !vm.state.dealerHoleRevealed
                        AnimatedCardEntry(card = c, faceDown = faceDown, width = cW, height = cH)
                    }
                }
            }
        }

        // Dealer total / bust — fixed-height slot so its appearance never shifts the layout
        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
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
        }

        // Last action — fixed-height slot, transparent until populated
        Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
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
            color = Color(0xFFD4A845).copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.0.sp)
        )
        Text(
            "DEALER MUST DRAW TO 16 AND STAND ON ALL 17s",
            color = Color(0xFFD4A845).copy(alpha = 0.50f),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 16.sp, letterSpacing = 1.6.sp)
        )
    }
}

// ─── Human zone ───────────────────────────────────────────────────────────────

@Composable
private fun HumanZone(vm: GameViewModel, chipScale: Float) {
    val human = vm.humanPlayer ?: return
    Column(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(16.dp)
    ) {
        if (human.hands.isEmpty()) {
            // Betting phase: show animated pending-bet chip stack with BET label below
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(110.dp, 86.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.scale(chipScale)) {
                        BetChipStack(amount = human.pendingBet)
                    }
                }
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "BET",
                        fontSize = 8.sp,
                        color    = Color.White.copy(alpha = 0.45f),
                        style    = TextStyle(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "$${human.pendingBet}",
                        color = BjColors.Accent,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            // Play phase: centered hands
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                human.hands.forEachIndexed { i, h ->
                    key(i) {
                        val isActive = vm.state.phase == GamePhase.PLAYER_TURNS &&
                            i == human.activeHandIndex
                        val ev = HandEvaluator.evaluate(h.cards)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Cards
                            Row(horizontalArrangement = Arrangement.spacedBy((-30).dp)) {
                                h.cards.forEachIndexed { ci, c ->
                                    key(ci) {
                                        AnimatedCardEntry(
                                            card     = c,
                                            faceDown = false,
                                            width    = 70.dp,
                                            height   = 100.dp
                                        )
                                    }
                                }
                            }

                            // Value badge — show hand total instead of "Bust" text
                            val badgeText  = if (h.outcome == null || h.outcome == "Bust") ev.displayString() else h.outcome
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

                            // Bet chip stack — fixed slot so the column height never changes when chips are added/removed
                            Box(modifier = Modifier.height(62.dp), contentAlignment = Alignment.TopCenter) {
                                BetChipStack(amount = h.bet)
                            }
                        }
                    }
                }
            }
        }

        // Insurance badge — fixed-height slot so cards never shift when it appears/disappears
        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
            if (human.insuranceBet > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("INSURANCE: ", fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.50f),
                        style = TextStyle(fontWeight = FontWeight.Bold))
                    Text("$${human.insuranceBet}", color = BjColors.Accent,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

// ─── Win-chance row (dedicated full-width, toggled by opacity) ────────────────

@Composable
private fun WinChanceRow(vm: GameViewModel) {
    val wc = vm.state.winChance
    // Alpha-only toggle: always occupies the same space so layout never shifts
    val rowAlpha by animateFloatAsState(
        targetValue   = if (wc != null) 1f else 0f,
        animationSpec = tween(300),
        label         = "wc_row_alpha"
    )
    val multiHand = vm.state.activeHandCount > 1
    val handLabel = if (multiHand) "Hand ${vm.state.activeHandIdx + 1} " else ""

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .alpha(rowAlpha),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Win Chance",
            modifier  = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            color     = Color.White.copy(alpha = 0.85f),
            style     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        )
        WinBar(label = "${handLabel}Hit",   pct = wc?.ifHit   ?: 0.0, color = BjColors.Success)
        WinBar(label = "${handLabel}Stand", pct = wc?.ifStand ?: 0.0, color = BjColors.Danger)

        wc?.ifDouble?.let {
            WinBar(label = "${handLabel}Double", pct = it, color = BjColors.InfoBlue)
        }
        wc?.ifSplit?.let {
            WinBar(label = "Hand 1", pct = it, color = BjColors.OrangeWarn)
            WinBar(label = "Hand 2", pct = it, color = BjColors.OrangeWarn)
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
        Text(label, fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.65f),
            style = TextStyle(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(100.dp))
        Box(
            modifier = Modifier
                .weight(1f).height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Text("${pct.toInt()}%", fontSize = 18.sp,
            color = color,
            style = TextStyle(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(64.dp))
    }
}

// ─── Bottom wood rail ─────────────────────────────────────────────────────────

@Composable
private fun BottomRail(
    vm: GameViewModel,
    tts: TtsService,
    onChipTapped: (Int) -> Unit
) {
    val isBetting = vm.state.phase == GamePhase.BETTING

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdvisorButton(vm = vm, tts = tts)

            if (isBetting) {
                BettingActionsRow(vm)
                BettingChipsRow(vm, onChipTapped)
            } else {
                WinChanceRow(vm)
                PlayActionsRow(vm)
            }
        }
    }
}

@Composable
private fun BettingActionsRow(vm: GameViewModel) {
    val human = vm.humanPlayer ?: return
    val bet = human.pendingBet
    val dealEnabled = bet > 0

    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clear (red)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(BjColors.Danger.copy(alpha = if (bet > 0) 0.85f else 0.25f))
                .clickable(enabled = bet > 0) { vm.updateHumanPendingBet(0) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Clear",
                color = Color.White.copy(alpha = if (bet > 0) 1f else 0.4f),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
        }

        // Deal (yellow/amber)
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(if (dealEnabled) 6.dp else 0.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (dealEnabled) Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                    else Brush.verticalGradient(listOf(BjColors.Neutral.copy(alpha = 0.1f), BjColors.Neutral.copy(alpha = 0.05f)))
                )
                .clickable(enabled = dealEnabled) { vm.confirmBetsAndDeal() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "DEAL",
                color = if (dealEnabled) Color(0xFF1F0C02) else Color.White.copy(alpha = 0.2f),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            )
        }
    }
}

@Composable
private fun BettingChipsRow(vm: GameViewModel, onChipTapped: (Int) -> Unit) {
    val human = vm.humanPlayer ?: return
    val bet   = human.pendingBet
    val chips = human.chips

    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 25, 100).forEach { value ->
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
        Column(horizontalAlignment = Alignment.End) {
            Text("BALANCE", fontSize = 8.sp, color = Color.White.copy(alpha = 0.40f))
            Text(
                "$$chips",
                color = BjColors.Accent,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// ─── Play actions row (action buttons + balance, always same height) ──────────

@Composable
private fun PlayActionsRow(vm: GameViewModel) {
    val avail  = vm.availableActions()
    val active = vm.isHumanTurn
    val chips  = vm.humanPlayer?.chips ?: return

    Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        // Action buttons centered horizontally
        Row(
            modifier              = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            TableBtn("HIT",   BjColors.Success,
                active && PlayerAction.Hit   in avail) { vm.handlePlayerAction(PlayerAction.Hit)   }
            if (PlayerAction.Split in avail) {
                TableBtn("SPLIT",  BjColors.OrangeWarn,
                    active) { vm.handlePlayerAction(PlayerAction.Split)  }
            }
            if (PlayerAction.Double in avail) {
                TableBtn("DOUBLE", BjColors.InfoBlue,
                    active) { vm.handlePlayerAction(PlayerAction.Double) }
            }
            TableBtn("STAND", BjColors.Danger,
                active && PlayerAction.Stand in avail) { vm.handlePlayerAction(PlayerAction.Stand) }
        }
        // Balance pinned to the right
        Column(
            modifier            = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End
        ) {
            Text("BALANCE", fontSize = 8.sp, color = Color.White.copy(alpha = 0.40f))
            Text(
                "$$chips",
                color = BjColors.Accent.copy(alpha = 0.6f),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// ─── Insurance dialog ─────────────────────────────────────────────────────────

@Composable
private fun InsuranceDialog(vm: GameViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                "Insurance?",
                modifier  = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
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
                .background(Color(0xFF14202B).copy(alpha = 0.60f))
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
                    val c = if (r.net > 0) BjColors.Success else if (r.net < 0) BjColors.Danger else BjColors.Neutral
                    val label = when (r.outcomeLabel) {
                        "Win", "Blackjack", "Insurance won" -> "WIN"
                        "Loss", "Bust", "Surrender", "Insurance lost" -> "LOSS"
                        else -> r.outcomeLabel.uppercase()
                    }
                    val sign = if (r.net > 0) "+" else if (r.net < 0) "-" else ""
                    val amountText = if (r.net != 0) " $sign $${kotlin.math.abs(r.net)}" else ""
                    Text("$label$amountText", color = c,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold))
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

/** Wraps CardView with a smooth slide-in deal animation. Fires once when the card enters the composition. */
@Composable
private fun AnimatedCardEntry(card: Card, faceDown: Boolean, width: Dp, height: Dp) {
    val offsetY = remember { Animatable(-120f) }
    val cardAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            offsetY.animateTo(
                0f,
                animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)
            )
        }
        cardAlpha.animateTo(1f, animationSpec = tween(durationMillis = 150))
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
        for (d in listOf(100, 25, 10, 5, 1)) {
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
private fun CasinoChipBtn(value: Int, currentBet: Int, maxChips: Int, enabledOverride: Boolean = true, onClick: () -> Unit) {
    val col     = chipColor(value)
    val enabled = enabledOverride && currentBet + value <= maxChips
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
            .size(width = 72.dp, height = 44.dp)
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

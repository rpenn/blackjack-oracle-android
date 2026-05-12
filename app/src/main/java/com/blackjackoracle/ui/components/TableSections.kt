package com.blackjackoracle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun GameHeader(
    state: GameState,
    onQuit: () -> Unit,
    onHelp: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        IconButton(onClick = onQuit, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Default.Close, contentDescription = "Quit", tint = BjColors.Neutral.copy(alpha = 0.7f))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hand ${state.currentRound}", color = BjColors.Neutral.copy(alpha = 0.55f), fontWeight = FontWeight.Bold)
            Text(state.phase.displayName(), color = BjColors.Neutral, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        IconButton(onClick = onHelp, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help", tint = BjColors.Neutral.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun DealerArea(state: GameState) {
    Text(
        "D E A L E R",
        color = BjColors.Neutral.copy(alpha = 0.65f),
        fontWeight = FontWeight.Bold,
        letterSpacing = 5.sp,
    )
    // Fixed-height row so dealer turn (hole-card flip, dealer draws) never
    // shifts the player area vertically.
    Row(
        horizontalArrangement = Arrangement.spacedBy(CardOverlap),
        modifier = Modifier.height(CardHeight + 5.dp),
    ) {
        state.dealerCards.forEachIndexed { index, card ->
            PlayingCard(
                card = card,
                faceDown = index == 1 && !state.dealerHoleRevealed,
                width = CardWidth,
                height = CardHeight,
            )
        }
    }
    val dealerLabel = remember(state.dealerCards, state.dealerHoleRevealed) {
        if (state.dealerHoleRevealed && state.dealerCards.isNotEmpty()) {
            HandEvaluator.evaluate(state.dealerCards).displayString()
        } else {
            state.dealerCards.firstOrNull()?.let { "shows ${it.displayString}" }.orEmpty()
        }
    }
    Text(dealerLabel, color = BjColors.Neutral.copy(alpha = 0.65f), fontSize = 13.sp)
    // Always rendered so its height is reserved from the first frame —
    // if it appeared conditionally, the Spacer(weight=1f) above PlayerArea
    // would shrink by ~29dp the moment the first action sets lastAction,
    // yanking the player cards upward in one frame (the "jump").
    Text(
        text = state.lastAction?.let { "${it.playerName} ${it.action}" } ?: "",
        color = BjColors.Neutral.copy(alpha = 0.8f),
        modifier = Modifier
            .alpha(if (state.lastAction != null) 1f else 0f)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
fun Inscription() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "BLACKJACK PAYS 3 TO 2",
            color = BjColors.TableGold,
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "DEALER MUST HIT SOFT 17",
            color = BjColors.TableGold,
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun PlayerArea(state: GameState) {
    if (state.phase == GamePhase.BETTING) {
        BetBox(state.human.pendingBet)
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.human.hands.forEachIndexed { index, hand ->
            val isActiveHand = index == state.human.activeHandIndex
            // Only highlight during a split — with one hand there's nothing
            // to disambiguate, so the line would just be visual noise.
            val highlightActive = state.human.hands.size > 1 && isActiveHand &&
                hand.cards.isNotEmpty()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = if (isActiveHand) 1f else 0.85f
                },
            ) {
                // Fixed height = card height. Keeps the score/chips/BET stack
                // anchored at the same Y position from the empty-hand frame
                // through the final card, so no vertical shift when cards arrive.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CardOverlap),
                    modifier = Modifier
                        .height(CardHeight)
                        .then(
                            if (highlightActive) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = BjColors.Accent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                            } else Modifier,
                        ),
                ) {
                    hand.cards.forEach { card ->
                        PlayingCard(
                            card = card,
                            faceDown = false,
                            width = CardWidth,
                            height = CardHeight,
                        )
                    }
                }
                Text(
                    text = hand.outcome ?: HandEvaluator.evaluate(hand.cards).displayString(),
                    color = if (isActiveHand) BjColors.Accent else BjColors.Neutral,
                    modifier = Modifier
                        .alpha(if (hand.cards.isNotEmpty()) 1f else 0f)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
                // Fixed-height chip slot: max 4-chip column (22dp + 3 × 8dp).
                // Without this, doubling the bet grows the column by ~8dp and
                // the centered outer Row pushes the cards upward — a subtle
                // jump that's visible against the still dealer row.
                Box(
                    Modifier.height(46.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ChipStack(hand.bet)
                }
                Text(
                    "BET $${hand.bet}",
                    color = BjColors.Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun BetBox(bet: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(132.dp, 92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            ChipStack(bet)
        }
        Text(
            if (bet > 0) "BET $$bet" else "Place your bet",
            color = BjColors.Accent,
            fontSize = if (bet > 0) 24.sp else 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

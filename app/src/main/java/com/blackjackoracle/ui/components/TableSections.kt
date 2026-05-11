package com.blackjackoracle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Text(state.phase.displayName(), color = BjColors.Neutral, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        IconButton(onClick = onHelp, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help", tint = BjColors.Neutral.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun DealerArea(state: GameState) {
    Text("D E A L E R", color = BjColors.Neutral.copy(alpha = 0.65f), fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
    Row(horizontalArrangement = Arrangement.spacedBy((-28).dp), modifier = Modifier.height(112.dp)) {
        state.dealerCards.forEachIndexed { index, card ->
            PlayingCard(card, faceDown = index == 1 && !state.dealerHoleRevealed)
        }
    }
    val upCard = state.dealerCards.firstOrNull()
    val dealerLabel = if (state.dealerHoleRevealed && state.dealerCards.isNotEmpty()) {
        HandEvaluator.evaluate(state.dealerCards).displayString()
    } else {
        upCard?.let { "shows ${it.displayString}" }.orEmpty()
    }
    Text(dealerLabel, color = BjColors.Neutral.copy(alpha = 0.65f), fontSize = 16.sp)
    state.lastAction?.let {
        Text(
            text = "${it.playerName} ${it.action}",
            color = BjColors.Neutral.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun Inscription() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "BLACKJACK PAYS 3 TO 2",
            color = BjColors.TableGold,
            fontFamily = FontFamily.Serif,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "DEALER MUST HIT SOFT 17",
            color = BjColors.TableGold,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
        )
    }
}

@Composable
fun PlayerArea(state: GameState) {
    if (state.phase == GamePhase.BETTING) {
        BetBox(state.human.pendingBet)
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        state.human.hands.forEachIndexed { index, hand ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = if (index == state.human.activeHandIndex) 1f else 0.85f },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-30).dp)) {
                    hand.cards.forEach { PlayingCard(it, faceDown = false) }
                }
                Text(
                    text = hand.outcome ?: HandEvaluator.evaluate(hand.cards).displayString(),
                    color = if (index == state.human.activeHandIndex) BjColors.Accent else BjColors.Neutral,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
                ChipStack(hand.bet)
                Text("BET $${hand.bet}", color = BjColors.Accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
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
            fontSize = if (bet > 0) 28.sp else 16.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

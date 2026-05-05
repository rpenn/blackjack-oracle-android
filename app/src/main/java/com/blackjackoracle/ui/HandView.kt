package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Hand
import com.blackjackoracle.ui.theme.BjColors

/**
 * Renders a single blackjack hand: row of cards above a small total/outcome
 * pill. Used for both human and AI hands.
 */
@Composable
fun HandView(
    hand: Hand,
    cardWidth: Dp = 50.dp,
    cardHeight: Dp = 72.dp,
    isActive: Boolean = false,
    revealAll: Boolean = true,
    showTotal: Boolean = true,
    modifier: Modifier = Modifier
) {
    val ev = HandEvaluator.evaluate(hand.cards)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(-cardWidth * 0.45f)) {
            for ((i, c) in hand.cards.withIndex()) {
                CardView(
                    card = if (revealAll) c else null,
                    faceDown = !revealAll,
                    width = cardWidth,
                    height = cardHeight
                )
            }
        }
        if (showTotal) {
            val totalText = if (revealAll) ev.displayString() else "—"
            val outcomeColor = when (hand.outcome) {
                "Win", "Blackjack" -> BjColors.Success
                "Loss", "Bust" -> BjColors.Danger
                "Push" -> BjColors.Neutral
                "Surrender" -> BjColors.OrangeWarn
                else -> if (isActive) BjColors.Accent else Color.White.copy(alpha = 0.85f)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(
                        width = if (isActive) 1.5.dp else 0.5.dp,
                        color = if (isActive) BjColors.Accent else BjColors.GlassStroke,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    hand.outcome ?: totalText,
                    color = outcomeColor,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
        if (hand.bet > 0) {
            Text(
                "$${hand.bet}" + if (hand.isDoubled) " (×2)" else "",
                color = BjColors.AccentSoft,
                style = TextStyle(fontSize = 10.sp)
            )
        }
    }
}

/**
 * Compact variant for AI seats — smaller cards, no per-hand totals shown
 * until reveal at end of round.
 */
@Composable
fun CompactHandView(
    hand: Hand,
    cardWidth: Dp = 32.dp,
    cardHeight: Dp = 46.dp,
    revealAll: Boolean = true,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    HandView(
        hand = hand,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        isActive = isActive,
        revealAll = revealAll,
        showTotal = true,
        modifier = modifier
    )
}

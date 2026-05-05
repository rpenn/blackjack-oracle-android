package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.Player
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun PlayerSeat(
    player: Player,
    isCurrent: Boolean,
    revealAll: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.alpha(if (player.isBusted) 0.4f else 1.0f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Cards (per-hand)
        if (player.hands.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    CardView(
                        card = null,
                        faceDown = true,
                        width = if (compact) 28.dp else 32.dp,
                        height = if (compact) 40.dp else 46.dp
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, h) in player.hands.withIndex()) {
                    CompactHandView(
                        hand = h,
                        cardWidth = if (compact) 28.dp else 32.dp,
                        cardHeight = if (compact) 40.dp else 46.dp,
                        revealAll = revealAll,
                        isActive = isCurrent && i == player.activeHandIndex
                    )
                }
            }
        }

        // Info pill
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .border(
                    if (isCurrent) 2.dp else 1.dp,
                    if (isCurrent) BjColors.Accent else BjColors.GlassStroke,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                player.name,
                color = Color.White,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                "$${player.chips}",
                color = BjColors.AccentSoft,
                style = TextStyle(fontSize = 11.sp)
            )
            if (player.isBusted) {
                Text("out", color = BjColors.Danger, style = TextStyle(fontSize = 10.sp))
            }
        }
    }
}

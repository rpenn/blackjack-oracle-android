package com.blackjackoracle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.Card
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun PlayingCard(
    card: Card,
    faceDown: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 75.dp,
    height: Dp = 107.dp,
) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .background(if (faceDown) Color(0xFF294986) else Color.White)
            .border(
                1.dp,
                if (faceDown) BjColors.Accent.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
    ) {
        if (faceDown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, BjColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
            )
            return@Box
        }

        val suitColor = if (card.suit.isRed) Color(0xFFFF4E55) else Color.Black
        Text(card.rankLabel, color = suitColor, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(card.suit.symbol, color = suitColor, fontSize = 44.sp, modifier = Modifier.align(Alignment.Center))
        Text(
            card.rankLabel,
            color = suitColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = 180f },
        )
    }
}

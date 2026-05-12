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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.Card
import com.blackjackoracle.ui.theme.BjColors

/// Card face:
///   - rank in the top-left corner; rotated rank in the bottom-right
///   - small suit symbol in the opposite-side corner of each rank
///     (top-right and bottom-left)
///   - center suit symbol at 85% opacity, sized to clear the corner glyphs
/// Font sizes scale to the card width — rank 0.28, corner suit 0.22, center 0.3575
/// (the iOS 0.55 base, reduced 35% per user request to remove suit overlap).
@Composable
fun PlayingCard(
    card: Card,
    faceDown: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 65.dp,
    height: Dp = 93.dp,
) {
    val w = width.value
    val rankSize = (w * 0.28f).sp
    val cornerSuitSize = (w * 0.22f).sp
    val centerSuitSize = (w * 0.3575f).sp

    Box(
        modifier
            .size(width, height)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (faceDown) Color(0xFF294986) else Color.White)
            .border(
                0.5.dp,
                Color.Black.copy(alpha = 0.18f),
                RoundedCornerShape(8.dp),
            ),
    ) {
        if (faceDown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF294986),
                                Color(red = 0.10f, green = 0.18f, blue = 0.38f),
                            ),
                        ),
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .border(
                            1.2.dp,
                            BjColors.AccentSoft.copy(alpha = 0.45f),
                            RoundedCornerShape(5.dp),
                        ),
                )
            }
            return@Box
        }

        val suitColor = if (card.suit.isRed) Color(0xFFFF4E55) else Color.Black
        val cornerPadding = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)

        // Center large suit at 85% opacity.
        Text(
            text = card.suit.symbol,
            color = suitColor.copy(alpha = 0.85f),
            fontSize = centerSuitSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )

        // Rank in top-left, rotated rank in bottom-right.
        Text(
            text = card.rankLabel,
            color = suitColor,
            fontSize = rankSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).then(cornerPadding),
        )
        Text(
            text = card.rankLabel,
            color = suitColor,
            fontSize = rankSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(cornerPadding)
                .graphicsLayer { rotationZ = 180f },
        )

        // Small suit in the opposite-side corners: top-right and bottom-left.
        Text(
            text = card.suit.symbol,
            color = suitColor,
            fontSize = cornerSuitSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopEnd).then(cornerPadding),
        )
        Text(
            text = card.suit.symbol,
            color = suitColor,
            fontSize = cornerSuitSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .then(cornerPadding)
                .graphicsLayer { rotationZ = 180f },
        )
    }
}

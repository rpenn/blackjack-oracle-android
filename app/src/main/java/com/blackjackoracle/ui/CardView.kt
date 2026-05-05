package com.blackjackoracle.ui

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.Card
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun CardView(
    card: Card?,
    faceDown: Boolean = false,
    width: Dp = 56.dp,
    height: Dp = 80.dp
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(BjColors.CardFace)
            .border(0.5.dp, Color(0f, 0f, 0f, 0.18f), RoundedCornerShape(8.dp))
    ) {
        if (faceDown || card == null) CardBack() else CardFace(card, width)
    }
}

@Composable
private fun CardBack() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(BjColors.CardBack, BjColors.CardBackDeep)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .border(
                    1.2.dp,
                    BjColors.AccentSoft.copy(alpha = 0.45f),
                    RoundedCornerShape(6.dp)
                )
        )
    }
}

@Composable
private fun CardFace(card: Card, width: Dp) {
    val color = if (card.suit.isRed) Color(0xFFC8252C) else Color(0xFF101218)
    val rankSize = (width.value * 0.28f).sp
    val suitSize = (width.value * 0.20f).sp
    val centerSize = (width.value * 0.40f).sp

    val edgeHPadding = 5.dp
    val edgeVPadding = 4.dp
    val inwardHPadding = edgeHPadding + (width.value * 0.08f).dp
    val inwardVPadding = edgeVPadding + (width.value * 0.08f).dp

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = card.rankLabel,
            color = color,
            style = TextStyle(fontSize = rankSize, fontWeight = FontWeight.Bold, lineHeight = rankSize),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = edgeHPadding, top = edgeVPadding)
        )
        Text(
            text = card.suit.symbol,
            color = color,
            style = TextStyle(fontSize = suitSize, fontWeight = FontWeight.SemiBold, lineHeight = suitSize),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = inwardHPadding, top = inwardVPadding)
        )
        Text(
            text = card.suit.symbol,
            color = color,
            style = TextStyle(fontSize = suitSize, fontWeight = FontWeight.SemiBold, lineHeight = suitSize),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = inwardHPadding, bottom = inwardVPadding)
                .rotate(180f)
        )
        Text(
            text = card.rankLabel,
            color = color,
            style = TextStyle(fontSize = rankSize, fontWeight = FontWeight.Bold, lineHeight = rankSize),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = edgeHPadding, bottom = edgeVPadding)
                .rotate(180f)
        )
        Text(
            text = card.suit.symbol,
            color = color,
            style = TextStyle(fontSize = centerSize, fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

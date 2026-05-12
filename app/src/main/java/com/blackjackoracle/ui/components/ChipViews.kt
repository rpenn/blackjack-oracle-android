package com.blackjackoracle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.ChipDecomposer
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun ChipStack(amount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        ChipDecomposer.columns(amount).forEach { column ->
            Column(verticalArrangement = Arrangement.spacedBy((-14).dp)) {
                column.reversed().forEach { ChipView(it, 22) }
            }
        }
    }
}

@Composable
fun ChipButton(
    value: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 48dp meets Material 3 accessibility minimum — chip art scales with the
    // button so the tap area and the visual stay aligned.
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        contentAlignment = Alignment.Center,
    ) {
        ChipView(value, 48)
        Text(
            text = "$$value",
            color = Color.White,
            fontSize = when {
                value >= 500 -> 9.sp
                value >= 10 -> 10.sp
                else -> 12.sp
            },
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun ChipView(value: Int, size: Int) {
    val color = when (value) {
        1 -> BjColors.ChipOne
        5 -> BjColors.ChipFive
        10 -> BjColors.ChipTen
        25 -> BjColors.ChipTwentyFive
        100 -> BjColors.ChipHundred
        500 -> BjColors.ChipFiveHundred
        else -> error("unknown chip value: $value")
    }

    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((size * 0.66f).dp)
                .clip(CircleShape)
                .background(BjColors.ChipInner),
        )
    }
}

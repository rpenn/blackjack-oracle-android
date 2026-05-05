package com.blackjackoracle.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.WinChance
import com.blackjackoracle.ui.theme.BjColors
import kotlin.math.roundToInt

/**
 * Twin-bar widget: "If you HIT" and "If you STAND" win-probability indicators.
 * The bar with higher equity is rendered slightly bolder so the right move is
 * visually obvious without being prescriptive.
 */
@Composable
fun WinChanceBars(wc: WinChance?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Win Chance",
            color = Color.White.copy(alpha = 0.85f),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        )
        val hit = wc?.ifHit ?: 0.0
        val stand = wc?.ifStand ?: 0.0
        val hitBetter = wc != null && hit >= stand
        WinChanceBar(
            label = "Hit",
            value = if (wc == null) null else hit,
            highlighted = wc != null && hitBetter
        )
        WinChanceBar(
            label = "Stand",
            value = if (wc == null) null else stand,
            highlighted = wc != null && !hitBetter
        )
    }
}

@Composable
private fun WinChanceBar(label: String, value: Double?, highlighted: Boolean) {
    val pct = (value ?: 0.0).toFloat()
    val color = barColor(pct)
    val animated by animateFloatAsState(targetValue = pct, animationSpec = tween(400), label = "wc-$label")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "if you $label",
                color = if (highlighted) Color.White else Color.White.copy(alpha = 0.6f),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
                )
            )
            Text(
                if (value == null) "—" else "${value.roundToInt()}%",
                color = color,
                style = TextStyle(
                    fontSize = if (highlighted) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (highlighted) 8.dp else 6.dp)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val w = maxWidth * (animated / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(w)
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)),
                            RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}

private fun barColor(p: Float): Color = when {
    p < 25 -> BjColors.Danger
    p < 45 -> BjColors.OrangeWarn
    p < 70 -> BjColors.Accent
    else -> BjColors.Success
}

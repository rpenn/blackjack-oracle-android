package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun BackgroundGradientBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom))
            )
    ) {
        content()
    }
}

fun Modifier.glassCard(cornerDp: Int = 16): Modifier = this
    .clip(RoundedCornerShape(cornerDp.dp))
    .background(BjColors.GlassFill, RoundedCornerShape(cornerDp.dp))
    .border(1.dp, BjColors.GlassStroke, RoundedCornerShape(cornerDp.dp))

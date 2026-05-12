package com.blackjackoracle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// One pending "+$N" label spawned by a chip tap.
/// Matches iOS `ChipFloat` — rises 70dp and fades over ~850ms.
@Immutable
data class ChipFloat(val id: Long, val amount: Int, val color: Color)

private const val ANIM_MS = 850
private const val REMOVE_DELAY_MS = 900L
private const val RISE_DP = 70

@Composable
fun ChipFloatLabel(
    float: ChipFloat,
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rise = remember(float.id) { Animatable(0f) }
    val opacity = remember(float.id) { Animatable(1f) }

    LaunchedEffect(float.id) {
        coroutineScope {
            launch { rise.animateTo(-RISE_DP.toFloat(), tween(ANIM_MS)) }
            launch { opacity.animateTo(0f, tween(ANIM_MS)) }
        }
        delay(REMOVE_DELAY_MS - ANIM_MS)
        onDone(float.id)
    }

    Text(
        text = "+$${float.amount}",
        color = float.color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .offset(y = rise.value.dp)
            .alpha(opacity.value),
    )
}

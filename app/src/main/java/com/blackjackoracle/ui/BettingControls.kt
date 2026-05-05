package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

/**
 * Place-your-bet panel shown during BETTING phase. Combines a slider with
 * quick-add chip buttons (1, 5, 10, 25) and a Deal button.
 */
@Composable
fun BettingControls(vm: GameViewModel) {
    val human = vm.humanPlayer ?: return
    val maxBet = human.chips.coerceAtLeast(GameConstants.MIN_BET)
    val bet = human.pendingBet.coerceIn(GameConstants.MIN_BET, maxBet)

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Place your bet",
                color = Color.White,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                "$$bet",
                color = BjColors.Accent,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
            )
        }

        if (maxBet > GameConstants.MIN_BET) {
            Slider(
                value = bet.toFloat(),
                valueRange = GameConstants.MIN_BET.toFloat()..maxBet.toFloat(),
                steps = 0,
                onValueChange = { vm.updateHumanPendingBet(it.toInt()) },
                colors = SliderDefaults.colors(
                    thumbColor = BjColors.Accent,
                    activeTrackColor = BjColors.Accent
                )
            )
        }

        // Quick-add chip buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            for (delta in listOf(1, 5, 10, 25)) {
                ChipButton(
                    label = "+$$delta",
                    enabled = bet + delta <= maxBet
                ) { vm.updateHumanPendingBet(bet + delta) }
            }
            ChipButton(label = "Min", enabled = true) { vm.updateHumanPendingBet(GameConstants.MIN_BET) }
            ChipButton(label = "All", enabled = true) { vm.updateHumanPendingBet(maxBet) }
        }

        // Deal button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                )
                .clickable { vm.confirmBetsAndDeal() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Deal",
                color = Color(0xFF1F1405),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ChipButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, BjColors.Accent.copy(alpha = if (enabled) 0.5f else 0.2f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

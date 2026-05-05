package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun ActionControls(vm: GameViewModel) {
    val available = vm.availableActions()
    val isHumanTurn = vm.isHumanTurn

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(12.dp)
    ) {
        // Primary row: Hit / Stand
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                title = "Hit",
                color = BjColors.InfoBlue,
                enabled = isHumanTurn && PlayerAction.Hit in available,
                modifier = Modifier.weight(1f)
            ) { vm.handlePlayerAction(PlayerAction.Hit) }

            ActionButton(
                title = "Stand",
                color = BjColors.Success,
                enabled = isHumanTurn && PlayerAction.Stand in available,
                modifier = Modifier.weight(1f)
            ) { vm.handlePlayerAction(PlayerAction.Stand) }
        }
        // Secondary row: Double / Split / Surrender (only show those that fit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                title = "Double",
                color = BjColors.Accent,
                enabled = isHumanTurn && PlayerAction.Double in available,
                modifier = Modifier.weight(1f)
            ) { vm.handlePlayerAction(PlayerAction.Double) }

            ActionButton(
                title = "Split",
                color = BjColors.AccentSoft,
                enabled = isHumanTurn && PlayerAction.Split in available,
                modifier = Modifier.weight(1f)
            ) { vm.handlePlayerAction(PlayerAction.Split) }

            ActionButton(
                title = "Surrender",
                color = BjColors.Danger,
                enabled = isHumanTurn && PlayerAction.Surrender in available,
                modifier = Modifier.weight(1f)
            ) { vm.handlePlayerAction(PlayerAction.Surrender) }
        }
    }
}

@Composable
private fun ActionButton(
    title: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = Color.White,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
    }
}

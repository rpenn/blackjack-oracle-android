package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun GameOverScreen(vm: GameViewModel) {
    val sorted = vm.state.players.sortedByDescending { it.chips }

    BackgroundGradientBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Game Over",
                color = BjColors.Accent,
                style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black)
            )
            val human = sorted.firstOrNull { it.isHuman }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if ((human?.chips ?: 0) > 0) {
                    Text(
                        "Cashed out at $${human?.chips}",
                        color = Color.White,
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    "${vm.state.handsPlayed} hands played",
                    color = BjColors.Success,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)
            ) {
                for (p in sorted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(p.name, color = Color.White)
                        Text(
                            "$${p.chips}",
                            color = if (p.isBusted) BjColors.Danger else BjColors.Accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                    )
                    .clickable { vm.returnToSetup() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "New Game",
                    color = Color.Black,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

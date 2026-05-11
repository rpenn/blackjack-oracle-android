package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun GameOverScreen(vm: GameViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Game Over", color = BjColors.Accent, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("Cashed out at $${vm.state.human.chips}", color = BjColors.Neutral, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${vm.state.handsPlayed} hands played", color = BjColors.Success)
            Spacer(Modifier.height(32.dp))
            GoldButton("NEW GAME", Modifier.fillMaxWidth()) { vm.returnToSetup() }
        }
    }
}

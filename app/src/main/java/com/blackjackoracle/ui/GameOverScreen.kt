package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.ui.theme.BlackjackOracleTheme
import com.blackjackoracle.viewmodel.GameViewModel
import com.blackjackoracle.LocalEntitlements
import com.blackjackoracle.LocalPaywall
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@Composable
fun GameOverScreen(vm: GameViewModel) {
    val entitlements = LocalEntitlements.current
    val paywall = LocalPaywall.current
    val isPremium by entitlements.isPremium.collectAsState()
    GameOverContent(
        handsPlayed = vm.state.handsPlayed,
        isPremium = isPremium,
        onGoPremium = { paywall.present("gameover_chip") },
        onNewGame = vm::returnToSetup,
    )
}

@Composable
private fun GameOverContent(handsPlayed: Int, isPremium: Boolean, onGoPremium: () -> Unit, onNewGame: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)))
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Game Over",
                color = BjColors.Accent,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "$handsPlayed hands played",
                color = BjColors.Success,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(28.dp))
            GoldButton("NEW GAME", Modifier.fillMaxWidth(), onClick = onNewGame)
            if (!isPremium) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Play smarter — Go Premium",
                    color = BjColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(onClick = onGoPremium)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GameOverPreview() {
    BlackjackOracleTheme {
        GameOverContent(handsPlayed = 27, isPremium = false, onGoPremium = {}, onNewGame = {})
    }
}

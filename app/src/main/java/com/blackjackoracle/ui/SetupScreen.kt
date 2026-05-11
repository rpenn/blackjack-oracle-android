package com.blackjackoracle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.ui.components.GlassCard
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel

@Composable
fun SetupScreen(vm: GameViewModel) {
    var showHelp by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)))
            .systemBarsPadding()
            .padding(22.dp),
    ) {
        IconButton(
            onClick = { showHelp = true },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help", tint = BjColors.Neutral.copy(alpha = 0.65f))
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Blackjack Oracle", color = BjColors.Accent, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text("Vegas rules, with Oliver at your side", color = BjColors.Neutral.copy(alpha = 0.9f), fontSize = 15.sp)
            Spacer(Modifier.height(32.dp))
            Image(painterResource(R.drawable.oliver), contentDescription = "Oliver", modifier = Modifier.size(160.dp).clip(CircleShape))
            Spacer(Modifier.height(30.dp))
            GlassCard {
                Text("Start: $100 · Min bet: $1 · Blackjack pays 3:2", color = BjColors.Neutral.copy(alpha = 0.8f), fontSize = 12.sp)
                Text("Dealer hits soft 17 · 8-deck shoe", color = BjColors.Neutral.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(42.dp))
            GoldButton("TAKE A SEAT", Modifier.fillMaxWidth()) { vm.startGame() }
        }

        if (showHelp) {
            HelpDialog(onDismiss = { showHelp = false })
        }
    }
}

package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackjackoracle.R
import com.blackjackoracle.model.GameConfig
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel
import androidx.compose.foundation.clickable

@Composable
fun SetupScreen(vm: GameViewModel) {
    var showHelp by remember { mutableStateOf(false) }

    BackgroundGradientBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 30.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Blackjack Oracle",
                    color = BjColors.Accent,
                    style = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black)
                )
                Text(
                    "Vegas rules, with Oliver at your side",
                    color = BjColors.Neutral.copy(alpha = 0.9f),
                    style = TextStyle(fontSize = 14.sp)
                )
            }

            // Oliver mascot
            AsyncImage(
                model              = R.drawable.oliver,
                contentDescription = "Oliver",
                modifier           = Modifier.size(160.dp)
            )

            // Rules info card
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(22.dp)
            ) {
                Text(
                    "Start: \$100  ·  Min bet: \$1  ·  Blackjack pays 3:2",
                    color = BjColors.Neutral.copy(alpha = 0.75f),
                    style = TextStyle(fontSize = 12.sp)
                )
                Text(
                    "Dealer hits soft 17  ·  8-deck shoe",
                    color = BjColors.Neutral.copy(alpha = 0.75f),
                    style = TextStyle(fontSize = 12.sp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start button — always 1v1 (human vs dealer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                    )
                    .clickable { vm.startGame(GameConfig(aiPlayerCount = 0)) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Take a Seat",
                    color = Color(0xFF1F1405),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        // Help button (top-right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, end = 12.dp)
        ) {
            IconButton(
                onClick  = { showHelp = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "Help",
                    tint               = BjColors.Neutral.copy(alpha = 0.55f)
                )
            }
        }

        if (showHelp) {
            HowToPlayDialog(onDismiss = { showHelp = false })
        }
    }
}

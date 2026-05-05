package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@Composable
fun SetupScreen(vm: GameViewModel) {
    var aiCount by remember { mutableIntStateOf(2) }
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

            AsyncImage(
                model = R.drawable.oliver,
                contentDescription = "Oliver",
                modifier = Modifier.size(160.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(22.dp)
            ) {
                SettingRow("AI Players at Table", aiCount.toString()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(1, 2, 3, 4, 5).forEach { n ->
                            Chip(text = n.toString(), selected = aiCount == n) {
                                aiCount = n
                            }
                        }
                    }
                }
                Text(
                    "Start: $100 · Min bet: $1 · Blackjack pays 3:2 · Dealer hits soft 17 · 8-deck shoe",
                    color = BjColors.Neutral.copy(alpha = 0.6f),
                    style = TextStyle(fontSize = 11.sp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                    )
                    .clickable {
                        vm.startGame(GameConfig(aiPlayerCount = aiCount))
                    }
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

        Box(modifier = Modifier.fillMaxSize().padding(top = 12.dp, end = 12.dp)) {
            IconButton(
                onClick = { showHelp = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "Help",
                    tint = BjColors.Neutral.copy(alpha = 0.55f)
                )
            }
        }

        if (showHelp) {
            HowToPlayDialog(onDismiss = { showHelp = false })
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Color.White,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                value,
                color = BjColors.Accent,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            )
        }
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (selected) BjColors.Accent else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) BjColors.Accent else Color.White.copy(alpha = 0.18f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.Black else Color.White,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

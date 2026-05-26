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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.ui.theme.BlackjackOracleTheme
import com.blackjackoracle.LocalEntitlements
import com.blackjackoracle.LocalPaywall
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign

@Composable
fun SetupScreen(onStart: () -> Unit) {
    val entitlements = LocalEntitlements.current
    val paywall = LocalPaywall.current
    val isPremium by entitlements.isPremium.collectAsState()
    var showHelp by remember { mutableStateOf(false) }
    val backdrop = remember { Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(backdrop)
            .systemBarsPadding()
            .padding(22.dp),
    ) {
        IconButton(
            onClick = { showHelp = true },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Help,
                contentDescription = "Help",
                tint = BjColors.Neutral.copy(alpha = 0.65f),
            )
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Blackjack Oracle",
                color = BjColors.Accent,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Vegas rules, with Oliver at your side",
                color = BjColors.Neutral.copy(alpha = 0.9f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(32.dp))
            Image(
                painterResource(R.drawable.oliver),
                contentDescription = "Oliver",
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(50.dp))
            GoldButton("TAKE A SEAT", Modifier.fillMaxWidth(), onClick = onStart)

            if (!isPremium) {
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BjColors.Accent.copy(alpha = 0.1f))
                        .border(1.dp, BjColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { paywall.present("setup_banner") }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = BjColors.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Go Premium",
                            color = BjColors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Unlock live Win Chance + Ask Oliver",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (showHelp) {
            HelpDialog(onDismiss = { showHelp = false })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    BlackjackOracleTheme {
        SetupScreen(onStart = {})
    }
}

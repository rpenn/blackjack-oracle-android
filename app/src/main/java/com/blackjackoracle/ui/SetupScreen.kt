package com.blackjackoracle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.service.ReviewPrompter
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.ui.theme.BlackjackOracleTheme

@Composable
fun SetupScreen(
    onStart: () -> Unit,
    onSettings: () -> Unit = {},
    showReviewLink: Boolean = false,
) {
    var showHelp by remember { mutableStateOf(false) }
    val backdrop = remember { Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)) }
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(backdrop)
            .systemBarsPadding()
            .padding(22.dp),
    ) {
        IconButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = BjColors.Neutral.copy(alpha = 0.65f),
            )
        }

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
            if (showReviewLink) {
                // Shown once the in-app review is spent for this version and a
                // session just ended ahead. Links to the Play listing — there
                // is no write-review composer deep link on Android.
                Text(
                    "Loving Blackjack Oracle? Leave a review",
                    color = BjColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { ReviewPrompter.openStoreListing(context) }
                        .padding(8.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            GoldButton("TAKE A SEAT", Modifier.fillMaxWidth(), onClick = onStart)

            val isPremium by LocalEntitlements.current.isPremium.collectAsState()
            val paywall = LocalPaywall.current
            if (!isPremium) {
                Spacer(Modifier.height(18.dp))
                PremiumBanner { paywall.present("setup_banner") }
            }
        }

        if (showHelp) {
            HelpDialog(onDismiss = { showHelp = false })
        }
    }
}

@Composable
private fun PremiumBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = BjColors.Accent)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                "Go Premium",
                color = BjColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Unlock live Win Chance + Ask Oliver",
                color = BjColors.Neutral.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
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

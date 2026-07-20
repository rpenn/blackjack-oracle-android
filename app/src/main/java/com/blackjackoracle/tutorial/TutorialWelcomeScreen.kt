package com.blackjackoracle.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.ui.glassCard
import com.blackjackoracle.ui.theme.BjColors

/// First-launch fork, shown instead of SetupScreen until onboarding has run:
/// play the guided practice hand, or skip straight to the game. Both paths
/// mark onboarding complete; the tutorial stays replayable from Settings.
/// Port of iOS TutorialWelcomeView.
@Composable
fun TutorialWelcomeScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val backdrop = remember { Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(backdrop)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(46.dp))
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
            Spacer(Modifier.height(28.dp))
            Image(
                painterResource(R.drawable.oliver),
                contentDescription = "Oliver",
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(22.dp),
            ) {
                Text(
                    "See a demo.",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Practice game play and see how live odds along with Oliver, the AI assistant, enhance the Blackjack experience.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft)),
                    )
                    .clickable { onStart() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Deal Me a Practice Hand",
                    color = Color(0xFF1F1405),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "I know Blackjack — skip",
                color = BjColors.Neutral.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSkip() }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

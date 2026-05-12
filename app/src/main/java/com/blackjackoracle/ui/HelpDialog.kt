package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.ui.theme.BlackjackOracleTheme

private enum class HelpTab(val title: String) { Basics("Basics"), Actions("Actions"), Rules("Rules") }

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        HelpContent(onDismiss = onDismiss)
    }
}

@Composable
private fun HelpContent(onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(HelpTab.Basics) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)))
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HelpHeader(onDismiss = onDismiss)
            TabBar(selected = tab, onSelect = { tab = it })
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    HelpTab.Basics -> BasicsTab()
                    HelpTab.Actions -> ActionsTab()
                    HelpTab.Rules -> RulesTab()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HelpHeader(onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "How to Play",
                color = BjColors.Accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Vegas Blackjack",
                color = BjColors.Neutral.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = BjColors.Neutral.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun TabBar(selected: HelpTab, onSelect: (HelpTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        HelpTab.entries.forEach { entry ->
            val isSelected = entry == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) BjColors.Accent.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) BjColors.Accent.copy(alpha = 0.45f) else Color.Transparent,
                        shape = RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(entry) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.title,
                    color = if (isSelected) BjColors.Accent else BjColors.Neutral.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BasicsTab() {
    InfoCard(
        title = "The Goal",
        bullets = listOf(
            "Beat the dealer to 21 — without going over.",
            "Closer to 21 than the dealer wins. Going over (busting) loses immediately.",
            "Number cards count their face value. J/Q/K count 10. Aces count 1 or 11.",
        ),
    )
    InfoCard(
        title = "Round Flow",
        bullets = listOf(
            "Place a bet (minimum \$1).",
            "You and the dealer get two cards each — dealer's second card is face-down.",
            "Decide what to do with your hand: hit, stand, or one of the special actions.",
            "When you're done, the dealer plays — drawing until 17 (and hitting soft 17).",
            "Closest to 21 without busting wins. Tied totals push (your bet is returned).",
        ),
    )
    InfoCard(
        title = "Meet Oliver",
        bullets = listOf(
            "Tap Oliver's button any time the table is yours.",
            "He looks at your hand, the dealer's up-card, and your odds — then recommends a play.",
            "Oliver speaks his advice aloud, so you never have to look away from the table.",
        ),
    )
}

@Composable
private fun ActionsTab() {
    InfoCard(
        title = "Hit",
        titleColor = BjColors.Success,
        bullets = listOf("Take another card. Repeat until you stand or bust."),
    )
    InfoCard(
        title = "Split",
        titleColor = BjColors.SplitYellow,
        bullets = listOf(
            "If your two cards have the same rank, split into two separate hands.",
            "Each hand gets its own card and its own bet, equal to the original.",
            "Re-split non-Aces up to four hands. Aces split only once and get one card each.",
        ),
    )
    InfoCard(
        title = "Double Down",
        titleColor = BjColors.InfoBlue,
        bullets = listOf(
            "Double your bet, take exactly one more card, and stand.",
            "Only available on your first two cards. Strong play on totals of 9, 10, or 11.",
        ),
    )
    InfoCard(
        title = "Stand",
        titleColor = BjColors.Danger,
        bullets = listOf("Keep what you have and pass the action to the dealer."),
    )
    InfoCard(
        title = "Surrender",
        titleColor = Color(0xFFE08A2A),
        bullets = listOf(
            "Forfeit the hand and get half your bet back.",
            "Only available on your first two cards, before any other action.",
            "Best on hard 16 vs dealer 9, 10, or Ace, and hard 15 vs 10.",
        ),
    )
    InfoCard(
        title = "Insurance",
        bullets = listOf(
            "Offered when the dealer shows an Ace.",
            "A side bet (half your main bet) that the dealer has blackjack.",
            "Pays 2 to 1, but generally a losing bet long-term — Oliver will tell you when (if ever) to take it.",
        ),
    )
}

@Composable
private fun RulesTab() {
    InfoCard(
        title = "This Table's Rules",
        bullets = listOf(
            "8-deck shoe. Reshuffled when about 1.25 decks remain.",
            "Blackjack pays 3 to 2 (e.g. bet \$10 → win \$15).",
            "Dealer hits soft 17 (H17).",
            "Double down on any first two cards. Double after split allowed.",
            "Re-split non-Aces up to four hands total.",
            "Aces split only once. Each split-Ace gets exactly one more card.",
            "Late surrender allowed (after dealer checks for blackjack).",
        ),
    )
    InfoCard(
        title = "Outcomes",
        bullets = listOf(
            "Win → 1 to 1 (you get back your bet plus an equal amount).",
            "Blackjack → 3 to 2.",
            "Push → bet returned, no win or loss.",
            "Loss / Bust → bet forfeit.",
            "Surrender → half the bet returned.",
        ),
    )
}

@Composable
private fun InfoCard(
    title: String,
    bullets: List<String>,
    titleColor: Color = Color.White,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            color = titleColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        bullets.forEach { bullet ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", color = BjColors.AccentSoft, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    bullet,
                    color = BjColors.Neutral.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelpContentPreview() {
    BlackjackOracleTheme {
        HelpContent(onDismiss = {})
    }
}

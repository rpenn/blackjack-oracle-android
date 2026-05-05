package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackjackoracle.ui.theme.BjColors

@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackgroundGradientBox {
            var tab by remember { mutableStateOf(HelpTab.Basics) }
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Column {
                        Text(
                            "How to Play",
                            color = BjColors.Accent,
                            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black)
                        )
                        Text(
                            "Vegas Blackjack",
                            color = BjColors.Neutral.copy(alpha = 0.6f),
                            style = TextStyle(fontSize = 11.sp)
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = BjColors.Neutral.copy(alpha = 0.6f)
                        )
                    }
                }
                TabBar(selected = tab, onChange = { tab = it })
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    when (tab) {
                        HelpTab.Basics -> BasicsTab()
                        HelpTab.Actions -> ActionsTab()
                        HelpTab.Rules -> RulesTab()
                    }
                }
            }
        }
    }
}

private enum class HelpTab(val title: String) {
    Basics("Basics"), Actions("Actions"), Rules("Rules")
}

@Composable
private fun TabBar(selected: HelpTab, onChange: (HelpTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(3.dp)
    ) {
        for (t in HelpTab.entries) {
            val sel = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) BjColors.Accent.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (sel) BjColors.Accent.copy(alpha = 0.45f) else Color.Transparent,
                        RoundedCornerShape(11.dp)
                    )
                    .clickable { onChange(t) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.title,
                    color = if (sel) BjColors.Accent else BjColors.Neutral.copy(alpha = 0.5f),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun BasicsTab() {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoCard("The Goal", listOf(
            "Beat the dealer to 21 — without going over.",
            "Closer to 21 than the dealer wins. Going over 21 (busting) loses immediately.",
            "Number cards count their face value. J/Q/K count 10. Aces count 1 or 11."
        ))
        InfoCard("Round Flow", listOf(
            "Place a bet (minimum $1).",
            "You and the dealer get two cards each — dealer's second card is face-down.",
            "Decide what to do with your hand: hit, stand, or one of the special actions.",
            "When you're done, the dealer plays — drawing until 17 (and hitting soft 17).",
            "Closest to 21 without busting wins. Tied totals push (your bet is returned)."
        ))
        InfoCard("Meet Oliver 🦉", listOf(
            "Tap Oliver's button any time the table is yours.",
            "He looks at your hand, the dealer's up-card, and your odds — then recommends a play.",
            "Oliver speaks his advice aloud, so you never have to look away from the table."
        ))
    }
}

@Composable
private fun ActionsTab() {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoCard("Hit", listOf(
            "Take another card. Repeat until you stand or bust."
        ))
        InfoCard("Stand", listOf(
            "Keep what you have and pass to the next player."
        ))
        InfoCard("Double Down", listOf(
            "Double your bet, take exactly one more card, and stand.",
            "Only available on your first two cards. Strong play on totals of 9, 10, or 11."
        ))
        InfoCard("Split", listOf(
            "If your two cards have the same rank, split into two separate hands.",
            "Each hand gets its own card and its own bet, equal to the original.",
            "You can re-split non-Aces up to four hands. Aces split only once and get one card each."
        ))
        InfoCard("Surrender", listOf(
            "Give up the hand and forfeit half your bet.",
            "Only on your first two cards. Useful on hard 16 vs a strong dealer up-card."
        ))
        InfoCard("Insurance", listOf(
            "Offered when the dealer shows an Ace.",
            "A side bet (half your main bet) that the dealer has blackjack.",
            "Pays 2 to 1, but generally a losing bet long-term — Oliver will tell you when (if ever) to take it."
        ))
    }
}

@Composable
private fun RulesTab() {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoCard("This Table's Rules", listOf(
            "8-deck shoe. Reshuffled when about 1.25 decks remain.",
            "Blackjack pays 3 to 2 (e.g. bet $10 → win $15).",
            "Dealer hits soft 17 (H17).",
            "Double down on any first two cards. Double after split allowed.",
            "Re-split non-Aces up to four hands total.",
            "Aces split only once. Each split-Ace gets exactly one more card.",
            "Late surrender allowed (after dealer checks for blackjack)."
        ))
        InfoCard("Outcomes", listOf(
            "Win → 1 to 1 (you get back your bet plus an equal amount).",
            "Blackjack → 3 to 2.",
            "Push → bet returned, no win or loss.",
            "Loss / Bust → bet forfeit.",
            "Surrender → half the bet returned."
        ))
    }
}

@Composable
private fun InfoCard(title: String, bullets: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().glassCard().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Color.White, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        for (b in bullets) {
            Row(verticalAlignment = Alignment.Top) {
                Text("•  ", color = BjColors.AccentSoft, style = TextStyle(fontSize = 13.sp))
                Text(b, color = BjColors.Neutral.copy(alpha = 0.85f), style = TextStyle(fontSize = 13.sp))
            }
        }
    }
}

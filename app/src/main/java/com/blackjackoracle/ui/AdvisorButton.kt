package com.blackjackoracle.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blackjackoracle.R
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.service.AdvisorContext
import com.blackjackoracle.service.AdvisorService
import com.blackjackoracle.service.TtsService
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AdvisorButton(
    vm: GameViewModel,
    tts: TtsService,
    modifier: Modifier = Modifier,
    showHoot: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("blackjack_settings", Context.MODE_PRIVATE) }
    var hasConsent by remember { mutableStateOf(prefs.getBoolean("advisor_consent", false)) }
    var showConsentDialog by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var lastAdvice by rememberSaveable { mutableStateOf<String?>(null) }
    var lastError by rememberSaveable { mutableStateOf<String?>(null) }
    var cacheKey by rememberSaveable { mutableStateOf("") }

    val isRoundEnd = vm.state.phase == GamePhase.ROUND_END
    val human = vm.humanPlayer
    val canAsk = isRoundEnd ||
        (human?.activeHand != null) ||
        vm.state.phase == GamePhase.INSURANCE

    fun currentCacheKey(): String {
        val handCards = human?.activeHand?.cards?.joinToString(",") { it.id } ?: ""
        val dealer = vm.state.dealerCards.joinToString(",") { it.id }
        val resultKey = if (isRoundEnd)
            vm.state.roundResults.joinToString(",") { "${it.playerName}:${it.outcomeLabel}:${it.net}" }
        else ""
        return "${vm.state.phase.toRawString()}|$handCards|$dealer|$resultKey"
    }

    val onTap: () -> Unit = handler@{
        if (isLoading) return@handler
        if (!hasConsent) {
            showConsentDialog = true
            return@handler
        }
        scope.launch {
            if (tts.isSpeaking) {
                tts.stop()
                return@launch
            }
            val key = currentCacheKey()
            val cached = lastAdvice
            if (cached != null && key == cacheKey) {
                tts.speak(cached)
                return@launch
            }
            isLoading = true
            lastError = null
            val available = vm.availableActions()
            val hand = human?.activeHand
            val handTotal = hand?.let { HandEvaluator.evaluate(it.cards).displayString() } ?: "—"
            val ctx = AdvisorContext(
                playerCards = hand?.cards,
                dealerCards = vm.state.dealerCards,
                dealerHoleRevealed = vm.state.dealerHoleRevealed,
                handTotal = handTotal,
                ifHitPct = vm.state.winChance?.ifHit,
                ifStandPct = vm.state.winChance?.ifStand,
                bet = hand?.bet ?: 0,
                phase = vm.state.phase.toRawString(),
                canHit = PlayerAction.Hit in available,
                canStand = PlayerAction.Stand in available,
                canDouble = PlayerAction.Double in available,
                canSplit = PlayerAction.Split in available,
                canSurrender = PlayerAction.Surrender in available,
                canInsure = vm.state.phase == GamePhase.INSURANCE,
                actionHistory = vm.state.actionHistory,
                roundResults = vm.state.roundResults
            )
            try {
                val text = AdvisorService.getAdvice(ctx)
                lastAdvice = text
                cacheKey = key
                isLoading = false
                tts.speak(text)
            } catch (ce: CancellationException) {
                isLoading = false
                throw ce
            } catch (e: Exception) {
                lastError = "Oliver couldn't connect."
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0f, 0f, 0f, 0.35f))
                .border(1.dp, BjColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .clickable(enabled = !isLoading && canAsk, onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft))
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = R.drawable.oliver,
                    contentDescription = "Oliver",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (showHoot) "Oliver's Hoot 🦉" else "Ask Oliver",
                    color = Color.White,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
                val status = when {
                    isLoading -> "Thinking..."
                    tts.isSpeaking -> "Speaking..."
                    showHoot -> "Tap to hear what went down"
                    else -> "Tap for advice"
                }
                Text(
                    status,
                    color = Color.White.copy(alpha = 0.7f),
                    style = TextStyle(fontSize = 11.sp)
                )
            }
            when {
                isLoading -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                tts.isSpeaking -> Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Speaking",
                    tint = BjColors.Accent
                )
                else -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speak",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        lastError?.let {
            Text(
                it,
                color = BjColors.Danger,
                style = TextStyle(fontSize = 11.sp)
            )
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text("Oliver's Advice") },
            text = {
                Text("To provide strategy tips, Oliver sends your cards and the dealer's up-card to an AI service. No personal data is shared.")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("advisor_consent", true).apply()
                    hasConsent = true
                    showConsentDialog = false
                    onTap()
                }) {
                    Text("I Understand", color = BjColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1C2A36),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            shape = RoundedCornerShape(22.dp)
        )
    }
}

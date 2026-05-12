package com.blackjackoracle.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackjackoracle.game.BlackjackTable
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.service.AdvisorContext
import com.blackjackoracle.service.AdvisorPromptBuilder
import com.blackjackoracle.service.AdvisorService
import com.blackjackoracle.service.SoundManager
import com.blackjackoracle.service.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Timing {
    const val DEAL_PER_CARD_MS = 140L
    const val DEAL_TAIL_MS = 200L
    const val DEALER_DRAW_MS = 750L
    const val SETTLEMENT_MS = 600L
    /// Brief pause after the hand is settled so the outcome badges land on the
    /// cards before the round-end overlay covers everything. Matches iOS's
    /// `roundEndDelay` of 0.70s.
    const val ROUND_END_DELAY_MS = 750L
}

data class AdvisorUiState(
    val isLoading: Boolean = false,
    val statusLabel: String = "Tap for advice",
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val table = BlackjackTable()
    private val sound = SoundManager(app.applicationContext)
    private val advisor = AdvisorService()
    private val tts = TtsService(app.applicationContext)

    private var gameJob: Job? = null
    private var askJob: Job? = null
    private var hootJob: Job? = null

    private var lastAskKey: String? = null
    private var lastAskAdvice: String? = null
    private var lastHootKey: String? = null
    private var lastHootAdvice: String? = null

    var state: GameState by mutableStateOf(table.state)
        private set

    var advisorState: AdvisorUiState by mutableStateOf(AdvisorUiState())
        private set

    var hootState: AdvisorUiState by mutableStateOf(
        AdvisorUiState(statusLabel = "Tap to hear what went down"),
    )
        private set

    // MARK: - Setup / round lifecycle

    fun startGame() {
        cancelGame()
        table.startGame()
        sync()
    }

    fun returnToSetup() {
        cancelGame()
        table.returnToSetup()
        sync()
    }

    fun updateHumanPendingBet(amount: Int) {
        table.updatePendingBet(amount)
        sync()
    }

    fun availableActions(): Set<PlayerAction> = table.availableActions()

    /// True while the human is on the clock — phase is PLAYER_TURNS, the deal
    /// animation has finished, and the active hand isn't already standing.
    /// Mirrors iOS `GameViewModel.isHumanTurn`.
    val isHumanTurn: Boolean
        get() {
            val s = state
            if (s.phase != GamePhase.PLAYER_TURNS || s.isDealAnimating) return false
            return s.human.activeHand?.isStanding == false
        }

    fun confirmBetsAndDeal() {
        if (state.phase != GamePhase.BETTING || state.human.pendingBet <= 0) return
        cancelGame()
        table.beginHand()
        sync()
        sound.playInitialDeal(viewModelScope, cards = 4)
        gameJob = viewModelScope.launch {
            repeat(2) {
                delay(Timing.DEAL_PER_CARD_MS)
                table.dealInitialCardToHuman()
                sync()
                delay(Timing.DEAL_PER_CARD_MS)
                table.dealInitialCardToDealer()
                sync()
            }
            delay(Timing.DEAL_TAIL_MS)
            table.finishInitialDeal()
            sync()
            if (state.phase == GamePhase.DEALER_TURN) runDealerAndSettle()
        }
    }

    fun handleInsurance(take: Boolean) {
        table.handleInsurance(take)
        if (take) sound.playChips()
        sync()
        if (state.phase == GamePhase.DEALER_TURN) runDealerAndSettle()
    }

    fun handlePlayerAction(action: PlayerAction) {
        table.handleAction(action)
        sync()
        when (action) {
            PlayerAction.Hit -> sound.playHit()
            PlayerAction.Stand, PlayerAction.Surrender -> sound.playStand()
            PlayerAction.Double, PlayerAction.Split -> sound.playChips()
        }
        if (state.phase == GamePhase.DEALER_TURN) runDealerAndSettle()
    }

    fun startNextHand() {
        cancelGame()
        table.startNextHand()
        sync()
    }

    // MARK: - Advisor

    fun requestAskOliverAdvice() {
        if (state.phase == GamePhase.ROUND_END || advisorState.isLoading) return
        val snapshot = state
        val available = availableActions()
        val key = AdvisorPromptBuilder.cacheKey(snapshot)
        askJob?.cancel()
        askJob = viewModelScope.launch {
            advisorState = AdvisorUiState(isLoading = true, statusLabel = "Thinking...")
            runCatching {
                val text = lastAskAdvice.takeIf { lastAskKey == key && it != null }
                    ?: withContext(Dispatchers.IO) {
                        advisor.advice(AdvisorPromptBuilder.build(AdvisorContext.from(snapshot, available)))
                    }.also {
                        lastAskKey = key
                        lastAskAdvice = it
                    }
                advisorState = AdvisorUiState(isLoading = true, statusLabel = "Speaking...")
                withContext(Dispatchers.IO) { tts.speak(text) }
            }.onFailure {
                advisorState = AdvisorUiState(statusLabel = "Advice unavailable")
                return@launch
            }
            advisorState = AdvisorUiState(statusLabel = "Tap for advice")
        }
    }

    fun requestOliversHoot() {
        if (state.phase != GamePhase.ROUND_END || hootState.isLoading) return
        val snapshot = state
        val available = availableActions()
        val key = AdvisorPromptBuilder.cacheKey(snapshot)
        hootJob?.cancel()
        hootJob = viewModelScope.launch {
            hootState = AdvisorUiState(isLoading = true, statusLabel = "Thinking...")
            runCatching {
                val text = lastHootAdvice.takeIf { lastHootKey == key && it != null }
                    ?: withContext(Dispatchers.IO) {
                        advisor.advice(AdvisorPromptBuilder.build(AdvisorContext.from(snapshot, available)))
                    }.also {
                        lastHootKey = key
                        lastHootAdvice = it
                    }
                hootState = AdvisorUiState(isLoading = true, statusLabel = "Speaking...")
                withContext(Dispatchers.IO) { tts.speak(text) }
            }.onFailure {
                hootState = AdvisorUiState(statusLabel = "Hoot unavailable")
                return@launch
            }
            hootState = AdvisorUiState(statusLabel = "Tap to hear what went down")
        }
    }

    // MARK: - Internals

    private fun runDealerAndSettle() {
        cancelGame()
        gameJob = viewModelScope.launch {
            while (table.dealerShouldDraw()) {
                delay(Timing.DEALER_DRAW_MS)
                table.dealDealerCard()
                sound.playDeal()
                sync()
            }
            delay(Timing.SETTLEMENT_MS)
            table.settleDealerTurn()
            sync()
            val results = state.roundResults
            when {
                results.any { it.net > 0 } -> sound.playWin()
                results.any { it.net < 0 } -> sound.playLose()
            }
            // Hold on the settled table so the player can see the outcome
            // badges before the overlay covers them.
            delay(Timing.ROUND_END_DELAY_MS)
            table.completeRound()
            sync()
        }
    }

    private fun sync() {
        state = table.state
    }

    private fun cancelGame() {
        gameJob?.cancel()
        gameJob = null
    }

    override fun onCleared() {
        cancelGame()
        askJob?.cancel()
        hootJob?.cancel()
        tts.release()
        sound.release()
        super.onCleared()
    }
}

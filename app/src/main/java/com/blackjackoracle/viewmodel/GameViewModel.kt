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
import com.blackjackoracle.service.AdvisorPromptBuilder
import com.blackjackoracle.service.AdvisorService
import com.blackjackoracle.service.SoundManager
import com.blackjackoracle.service.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdvisorUiState(
    val isLoading: Boolean = false,
    val statusLabel: String = "Tap for advice",
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val table = BlackjackTable()
    private val sound = SoundManager(app.applicationContext)
    private val advisor = AdvisorService()
    private val tts = TtsService(app.applicationContext)
    private var job: Job? = null
    private var askOliverJob: Job? = null
    private var hootJob: Job? = null
    private var lastAskAdviceKey: String? = null
    private var lastAskAdvice: String? = null
    private var lastHootAdviceKey: String? = null
    private var lastHootAdvice: String? = null

    var state: GameState by mutableStateOf(table.state)
        private set

    var advisorState: AdvisorUiState by mutableStateOf(AdvisorUiState())
        private set

    var hootState: AdvisorUiState by mutableStateOf(AdvisorUiState(statusLabel = "Tap to hear what went down"))
        private set

    fun startGame() { cancel(); table.startGame(); sync() }
    fun returnToSetup() { cancel(); table.returnToSetup(); sync() }
    fun updateHumanPendingBet(amount: Int) { table.updatePendingBet(amount); sync() }
    fun availableActions(): Set<PlayerAction> = table.availableActions()

    fun confirmBetsAndDeal() {
        if (state.phase != GamePhase.BETTING || state.human.pendingBet <= 0) return
        cancel(); table.beginHand(); sync(); sound.playInitialDeal(viewModelScope, 4)
        job = viewModelScope.launch {
            repeat(2) {
                delay(140); table.dealInitialCardToHuman(); sync()
                delay(140); table.dealInitialCardToDealer(); sync()
            }
            delay(200); table.finishInitialDeal(); sync(); if (state.phase == GamePhase.DEALER_TURN) runDealerAndSettle()
        }
    }
    fun handleInsurance(take: Boolean) { table.handleInsurance(take); sync(); if (state.phase == GamePhase.DEALER_TURN) runDealerAndSettle() }
    fun handlePlayerAction(action: PlayerAction) { table.handleAction(action); sync(); when (action) { PlayerAction.Hit -> sound.playHit(); PlayerAction.Stand -> sound.playStand(); PlayerAction.Double, PlayerAction.Split -> sound.playChips(); PlayerAction.Surrender -> sound.playStand() }; if (state.phase == GamePhase.DEALER_TURN) runDealerAndSettle() }
    fun startNextHand() { cancel(); table.startNextHand(); sync() }

    fun requestAskOliverAdvice() {
        if (state.phase == GamePhase.ROUND_END || advisorState.isLoading) return
        val snapshot = state
        val key = AdvisorPromptBuilder.cacheKey(snapshot)
        askOliverJob?.cancel()
        askOliverJob = viewModelScope.launch {
            advisorState = AdvisorUiState(isLoading = true, statusLabel = "Thinking...")
            runCatching {
                val text = if (lastAskAdviceKey == key && lastAskAdvice != null) {
                    lastAskAdvice.orEmpty()
                } else {
                    withContext(Dispatchers.IO) {
                        advisor.advice(AdvisorPromptBuilder.build(snapshot))
                    }.also {
                        lastAskAdviceKey = key
                        lastAskAdvice = it
                    }
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
        val key = AdvisorPromptBuilder.cacheKey(snapshot)
        hootJob?.cancel()
        hootJob = viewModelScope.launch {
            hootState = AdvisorUiState(isLoading = true, statusLabel = "Thinking...")
            runCatching {
                val text = if (lastHootAdviceKey == key && lastHootAdvice != null) {
                    lastHootAdvice.orEmpty()
                } else {
                    withContext(Dispatchers.IO) {
                        advisor.advice(AdvisorPromptBuilder.build(snapshot))
                    }.also {
                        lastHootAdviceKey = key
                        lastHootAdvice = it
                    }
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

    private fun runDealerAndSettle() {
        cancel()
        job = viewModelScope.launch {
            while (table.dealerShouldDraw()) { delay(750); table.dealDealerCard(); sound.playDeal(); sync() }
            delay(600); table.settleDealerTurn(); sync(); if (state.roundResults.any { it.net > 0 }) sound.playWin() else if (state.roundResults.any { it.net < 0 }) sound.playLose()
        }
    }
    private fun sync() { state = table.state }
    private fun cancel() { job?.cancel(); job = null }
    override fun onCleared() {
        cancel()
        askOliverJob?.cancel()
        hootJob?.cancel()
        tts.release()
        sound.release()
        super.onCleared()
    }
}

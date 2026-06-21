package com.blackjackoracle.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackjackoracle.BlackjackApp
import com.blackjackoracle.game.BlackjackTable
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.service.AdvisorContext
import com.blackjackoracle.service.AdvisorService
import com.blackjackoracle.service.SoundManager
import com.blackjackoracle.service.TtsService
import kotlinx.coroutines.CancellationException
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
    /// cards before the round-end overlay covers everything.
    const val ROUND_END_DELAY_MS = 750L
    /// How long the insurance dialog lingers before the ViewModel auto-declines
    /// for a player who can't afford the bet. Long enough to read the dialog,
    /// short enough that the broke player isn't left staring at a popup.
    const val INSURANCE_AUTO_DECLINE_MS = 1500L
}

data class AdvisorUiState(
    val isLoading: Boolean = false,
    val isSpeaking: Boolean = false,
    val statusLabel: String = "Tap for advice",
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val table = BlackjackTable()
    private val sound = SoundManager(app.applicationContext)
    private val advisor = AdvisorService()
    private val tts = TtsService(app.applicationContext)

    private val billing get() = getApplication<BlackjackApp>()
    /// Bearer token for the AI endpoints: the active trial JWT, else the
    /// RevenueCat appUserID. The server-side gate verifies it.
    private val authToken: String?
        get() = billing.entitlements.activeTrialToken ?: billing.purchases.appUserID

    private var gameJob: Job? = null
    private var askJob: Job? = null
    private var hootJob: Job? = null
    private var insuranceAutoDeclineJob: Job? = null

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

    /// Latches the after-loss paywall nudge to fire at most once per process.
    /// Lives here (not in Composable state) so it survives recomposition and
    /// config changes. Reset only when the process dies.
    var lossNudgeShown: Boolean = false

    // Setup / round lifecycle

    fun startGame() {
        cancelGame()
        table.startGame()
        sync()
    }

    fun returnToSetup() {
        stopAllJobsAndAudio()
        table.returnToSetup()
        sync()
    }

    /// Called by the host activity on STOP/background — kill any in-flight TTS
    /// so Oliver doesn't keep narrating once the user has left the app.
    fun stopSpeaking() {
        askJob?.cancel(); askJob = null
        hootJob?.cancel(); hootJob = null
        tts.stop()
        advisorState = AdvisorUiState(statusLabel = "Tap for advice")
        hootState = AdvisorUiState(statusLabel = "Tap to hear what went down")
    }

    fun updateHumanPendingBet(amount: Int) {
        sound.playChips()
        table.updatePendingBet(amount)
        sync()
    }

    fun availableActions(): Set<PlayerAction> = table.availableActions()

    /// True if the player has the chips to actually pay an insurance bet
    /// (half the main bet, minimum $1). Drives the Take Insurance button's
    /// enabled state and the auto-decline timer.
    fun canAffordInsurance(): Boolean {
        val s = state
        val bet = s.human.hands.firstOrNull()?.bet ?: return false
        return s.human.chips >= maxOf(1, bet / 2)
    }

    /// True while the human is on the clock — phase is PLAYER_TURNS, the deal
    /// animation has finished, and the active hand isn't already standing.
    val isHumanTurn: Boolean
        get() {
            val s = state
            if (s.phase != GamePhase.PLAYER_TURNS || s.isDealAnimating) return false
            return s.human.activeHand?.isStanding == false
        }

    fun confirmBetsAndDeal() {
        if (state.phase != GamePhase.BETTING || state.human.pendingBet <= 0) return
        cancelGame()
        silenceAdvisors()
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
            // Run the dealer flow inline on this same coroutine when the deal
            // resolved straight to DEALER_TURN (player BJ, dealer BJ peek).
            // Launching a separate coroutine here used to require cancelling
            // the current one, which cancels ourselves mid-block.
            when {
                state.phase == GamePhase.DEALER_TURN -> runDealerAndSettleInline()
                state.phase == GamePhase.INSURANCE && !canAffordInsurance() ->
                    scheduleInsuranceAutoDecline()
            }
        }
    }

    fun handleInsurance(take: Boolean) {
        insuranceAutoDeclineJob?.cancel(); insuranceAutoDeclineJob = null
        table.handleInsurance(take)
        if (take) sound.playChips()
        sync()
        if (state.phase == GamePhase.DEALER_TURN) launchDealerFlow()
    }

    /// Shows the insurance dialog for [Timing.INSURANCE_AUTO_DECLINE_MS] and
    /// then routes through `handleInsurance(false)` — same code path as the
    /// player tapping "No Thanks", so the dealer-BJ peek and the transition
    /// to PLAYER_TURNS / DEALER_TURN both stay in one place.
    private fun scheduleInsuranceAutoDecline() {
        insuranceAutoDeclineJob?.cancel()
        insuranceAutoDeclineJob = viewModelScope.launch {
            delay(Timing.INSURANCE_AUTO_DECLINE_MS)
            // Re-check phase: a manual tap or returnToSetup may have moved us
            // off INSURANCE while the timer was suspended.
            if (state.phase == GamePhase.INSURANCE) handleInsurance(false)
        }
    }

    fun handlePlayerAction(action: PlayerAction) {
        table.handleAction(action)
        sync()
        when (action) {
            PlayerAction.Hit -> sound.playHit()
            PlayerAction.Stand, PlayerAction.Surrender -> sound.playStand()
            PlayerAction.Double, PlayerAction.Split -> sound.playChips()
        }
        if (state.phase == GamePhase.DEALER_TURN) launchDealerFlow()
    }

    fun startNextHand() {
        cancelGame()
        silenceAdvisors()
        table.startNextHand()
        sync()
    }

    // Advisor

    fun requestAskOliverAdvice() {
        if (state.phase == GamePhase.ROUND_END || advisorState.isLoading) return
        // Tapping mid-speech pauses playback rather than restarting it. The
        // cached advice survives, so the next tap replays from the top.
        if (advisorState.isSpeaking) {
            askJob?.cancel(); askJob = null
            tts.stop()
            advisorState = AdvisorUiState(statusLabel = "Tap to replay")
            return
        }
        askJob?.cancel()
        askJob = launchAdvisor(
            idleLabel = "Tap for advice",
            failLabel = "Advice unavailable",
            setState = { advisorState = it },
            getCached = { k -> lastAskAdvice.takeIf { lastAskKey == k && it != null } },
            putCached = { k, t -> lastAskKey = k; lastAskAdvice = t },
        )
    }

    fun requestOliversHoot() {
        if (state.phase != GamePhase.ROUND_END || hootState.isLoading) return
        if (hootState.isSpeaking) {
            hootJob?.cancel(); hootJob = null
            tts.stop()
            hootState = AdvisorUiState(statusLabel = "Tap to replay")
            return
        }
        hootJob?.cancel()
        hootJob = launchAdvisor(
            idleLabel = "Tap to hear what went down",
            failLabel = "Hoot unavailable",
            setState = { hootState = it },
            getCached = { k -> lastHootAdvice.takeIf { lastHootKey == k && it != null } },
            putCached = { k, t -> lastHootKey = k; lastHootAdvice = t },
        )
    }

    // Internals

    /// Shared advisor pipeline: cache lookup → HTTP call → TTS playback (which
    /// now suspends until audio finishes). The lambdas factor out the per-lane
    /// state holders so Ask Oliver and Oliver's Hoot share one launch site.
    private fun launchAdvisor(
        idleLabel: String,
        failLabel: String,
        setState: (AdvisorUiState) -> Unit,
        getCached: (key: String) -> String?,
        putCached: (key: String, advice: String) -> Unit,
    ): Job {
        val snapshot = state
        val available = availableActions()
        val key = AdvisorService.cacheKey(snapshot)
        return viewModelScope.launch {
            setState(AdvisorUiState(isLoading = true, statusLabel = "Thinking..."))
            try {
                val token = authToken
                val text = getCached(key) ?: withContext(Dispatchers.IO) {
                    advisor.advice(AdvisorContext.from(snapshot, available), token)
                }.also { putCached(key, it) }
                // Keep the "Thinking..." spinner through the audio fetch/decode
                // — only flip to the speaking bars once playback truly begins,
                // otherwise the bars animate over silence (most visible on the
                // longer Hoot recap whose audio takes longer to fetch).
                tts.speak(text, token) {
                    setState(AdvisorUiState(isLoading = false, isSpeaking = true, statusLabel = "Speaking..."))
                }
            } catch (e: CancellationException) {
                // Don't swallow cancellation — let the job complete cleanly.
                throw e
            } catch (_: Throwable) {
                setState(AdvisorUiState(statusLabel = failLabel))
                return@launch
            }
            setState(AdvisorUiState(statusLabel = idleLabel))
        }
    }

    private suspend fun runDealerAndSettleInline() {
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
        // Hold on the settled table so the player can see the outcome badges
        // before the overlay covers them.
        delay(Timing.ROUND_END_DELAY_MS)
        table.completeRound()
        sync()
    }

    private fun launchDealerFlow() {
        cancelGame()
        gameJob = viewModelScope.launch { runDealerAndSettleInline() }
    }

    private fun sync() {
        state = table.state
    }

    private fun cancelGame() {
        gameJob?.cancel()
        gameJob = null
    }

    private fun stopAllJobsAndAudio() {
        gameJob?.cancel(); gameJob = null
        askJob?.cancel(); askJob = null
        hootJob?.cancel(); hootJob = null
        insuranceAutoDeclineJob?.cancel(); insuranceAutoDeclineJob = null
        tts.stop()
    }

    /// Advisor jobs and audio are per-hand. Without this, a Hoot started at one
    /// round's end (or its still-playing audio) bleeds into the next hand —
    /// leaving hootState.isSpeaking=true so the equalizer animates at the next
    /// round end even though the user never tapped Oliver. Cancel the jobs,
    /// silence playback, and reset both lanes to their idle labels.
    private fun silenceAdvisors() {
        askJob?.cancel(); askJob = null
        hootJob?.cancel(); hootJob = null
        tts.stop()
        advisorState = AdvisorUiState(statusLabel = "Tap for advice")
        hootState = AdvisorUiState(statusLabel = "Tap to hear what went down")
    }

    override fun onCleared() {
        stopAllJobsAndAudio()
        sound.release()
        super.onCleared()
    }
}

package com.blackjackoracle.tutorial

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackjackoracle.data.CaptionPreferences
import com.blackjackoracle.data.OnboardingPreferences
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.service.billing.EntitlementStore
import com.blackjackoracle.viewmodel.GameViewModel
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/// The steps of the guided first hand, in play order. `WAIT_…` steps are the
/// stretches where cards land or the dealer plays and no coach UI shows; the
/// others each render a spotlight + coach bubble in `TutorialOverlay`.
enum class TutorialStep {
    INACTIVE,
    WAIT_DEAL,          // cards are being dealt
    WIN_CHANCE,         // spotlight: the Win Chance bars at 18 v 6
    ASK_OLIVER_SPLIT,   // spotlight: the Ask Oliver pill — user must tap it
    OLIVER_SAYS_SPLIT,  // Oliver's split call is showing
    SPLIT_STEP,         // spotlight: controls — user must Split
    STAND_STEP,         // spotlight: controls — hand 1 is 19, user must Stand
    ASK_OLIVER_DOUBLE,  // spotlight: the pill again for hand 2's eleven
    OLIVER_SAYS_DOUBLE, // Oliver's double call is showing
    DOUBLE_STEP,        // spotlight: controls — user must Double
    WAIT_DEALER,        // dealer reveals 16, draws, busts
    FINISHED,           // closing card over the round-end state
}

/// Drives the guided first hand: advances `step` as the game moves, restricts
/// which table controls are live, grants temporary premium so the new user
/// experiences Win Chance and Ask Oliver, and narrates Oliver's canned lines.
/// Kotlin port of iOS TutorialController.
class TutorialController(
    context: Context,
    private val onboardingPrefs: OnboardingPreferences,
    captionPrefs: CaptionPreferences,
    private val scope: CoroutineScope,
) {
    var step: TutorialStep by mutableStateOf(TutorialStep.INACTIVE)
        private set
    val isActive: Boolean get() = step != TutorialStep.INACTIVE

    /// Spotlight target bounds, published by the table UI and consumed by the
    /// overlay. Lives here so both sides reach it through LocalTutorial.
    val anchors = TutorialAnchors()

    private var entitlements: EntitlementStore? = null
    private val narrator = TutorialNarrator(context.applicationContext)

    /// Synchronous mirrors of the persisted caption prefs. DataStore reads are
    /// async Flows (unlike iOS UserDefaults), but `narrate` needs the values
    /// at the instant a line fires — so collect them into plain vars here.
    private var showCaptions = false
    private var captionOnly = false

    init {
        scope.launch { captionPrefs.showCaptions.collect { showCaptions = it } }
        scope.launch { captionPrefs.captionOnly.collect { captionOnly = it } }
    }

    // Lifecycle

    fun begin(entitlements: EntitlementStore) {
        this.entitlements = entitlements
        entitlements.setTutorialGrant(true)
        step = TutorialStep.WAIT_DEAL
    }

    /// Ends the tutorial (skip or completion): drops the temporary premium
    /// grant and records that onboarding ran so the welcome screen stays gone.
    fun end() {
        narrator.stop()
        entitlements?.setTutorialGrant(false)
        entitlements = null
        scope.launch { onboardingPrefs.setCompleted(true) }
        step = TutorialStep.INACTIVE
    }

    // Advancement

    /// Re-derives the current step from game state. Idempotent — the table
    /// screen calls it whenever the deal animation, phase, or active hand
    /// changes, so a step fires exactly when its trigger condition first holds.
    fun sync(vm: GameViewModel) {
        if (!isActive) return

        // The scripted hand can't resolve early — every action is forced — but
        // keep the poker tutorial's defensive guard: if the round has resolved
        // while we're in a wait state, jump straight to the closing card rather
        // than stranding the step machine.
        if (vm.state.phase == GamePhase.ROUND_END && isWaiting) {
            finish()
            return
        }

        when (step) {
            TutorialStep.WAIT_DEAL -> {
                if (!vm.state.isDealAnimating &&
                    vm.state.phase == GamePhase.PLAYER_TURNS &&
                    vm.isHumanTurn
                ) {
                    step = TutorialStep.WIN_CHANCE
                    narrate(TutorialScript.lineDeal)
                }
            }
            else -> Unit
        }
    }

    /// True while the tutorial is waiting on game progress (deal, dealer play)
    /// rather than on a user coach interaction.
    private val isWaiting: Boolean
        get() = step == TutorialStep.WAIT_DEAL || step == TutorialStep.WAIT_DEALER

    private fun finish() {
        step = TutorialStep.FINISHED
        narrate(TutorialScript.lineClosing)
    }

    /// Tap-anywhere advancement for the blocking coach steps.
    fun advanceFromTap() {
        step = when (step) {
            TutorialStep.WIN_CHANCE -> TutorialStep.ASK_OLIVER_SPLIT
            TutorialStep.OLIVER_SAYS_SPLIT -> TutorialStep.SPLIT_STEP
            TutorialStep.OLIVER_SAYS_DOUBLE -> TutorialStep.DOUBLE_STEP
            else -> return
        }
    }

    /// The user tapped the spotlighted Ask Oliver pill.
    fun advisorTapped() {
        when (step) {
            TutorialStep.ASK_OLIVER_SPLIT -> {
                step = TutorialStep.OLIVER_SAYS_SPLIT
                narrate(TutorialScript.lineSplit)
            }
            TutorialStep.ASK_OLIVER_DOUBLE -> {
                step = TutorialStep.OLIVER_SAYS_DOUBLE
                narrate(TutorialScript.lineDouble)
            }
            else -> Unit
        }
    }

    /// The user committed a table action (observed via `vm.humanActionCount`).
    fun humanActed() {
        when (step) {
            TutorialStep.SPLIT_STEP -> {
                // Both split hands drew instantly; hand 1 (19) is now active.
                step = TutorialStep.STAND_STEP
                narrate(TutorialScript.lineStand)
            }
            TutorialStep.STAND_STEP -> {
                // Hand 2 (11) is now active.
                step = TutorialStep.ASK_OLIVER_DOUBLE
            }
            TutorialStep.DOUBLE_STEP -> {
                step = TutorialStep.WAIT_DEALER
            }
            else -> Unit
        }
    }

    // Control gating

    /// Whether a table control is tappable right now. Outside the tutorial
    /// everything is permitted; inside it, only the step's scripted action is —
    /// Hit stays visible but dimmed throughout.
    fun permits(action: PlayerAction): Boolean {
        if (!isActive) return true
        return when (step) {
            TutorialStep.SPLIT_STEP -> action == PlayerAction.Split
            TutorialStep.STAND_STEP -> action == PlayerAction.Stand
            TutorialStep.DOUBLE_STEP -> action == PlayerAction.Double
            else -> false
        }
    }

    // Narration

    /// Plays a line's pre-rendered audio, honoring the user's caption-only
    /// preference (the coach bubble always shows the text anyway).
    private fun narrate(line: TutorialLine) {
        if (showCaptions && captionOnly) return
        narrator.speak(line)
    }
}

/// Plays the tutorial's canned lines from bundled MP3s pre-rendered in the
/// production Oliver voice (see scripts/gen-tutorial-audio.js). The live TTS
/// path (`TtsService`) round-trips through the backend, which requires a
/// premium bearer token and a network connection — both wrong for a first
/// launch. On-device synthesis (android.speech.tts) remains only as a fallback
/// if a bundled file ever goes missing.
private class TutorialNarrator(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null

    fun speak(line: TutorialLine) {
        stop()
        val resId = context.resources.getIdentifier(
            line.audioResource, "raw", context.packageName,
        )
        if (resId != 0) {
            val mp = MediaPlayer.create(context, resId)
            if (mp != null) {
                mp.setOnCompletionListener { finished ->
                    finished.release()
                    if (player === finished) player = null
                }
                player = mp
                mp.start()
                return
            }
        }
        speakFallback(line.text)
    }

    private fun speakFallback(text: String) {
        tts?.let {
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tutorial")
            return
        }
        // The engine binds asynchronously; hold the utterance until it's up.
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.language = Locale.US
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tutorial")
            }
        }
        tts = engine
    }

    fun stop() {
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        tts?.stop()
    }
}

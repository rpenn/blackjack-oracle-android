package com.blackjackoracle.demo

import androidx.compose.runtime.snapshotFlow
import com.blackjackoracle.engine.BasicStrategy
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.StrategyMove
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.Suit
import com.blackjackoracle.service.billing.EntitlementStore
import com.blackjackoracle.viewmodel.DemoSeams
import com.blackjackoracle.viewmodel.GameViewModel
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY autopilot used to capture Play Store screenshots and the promo
 * video. Absent from the release build — the release source set ships a no-op
 * twin of this object (`src/release/.../demo/DemoEntry.kt`), so none of this
 * code or its strings are present in the shipped APK/AAB.
 *
 * Activated by launching MainActivity with `--ez demo_mode true`. Scripted hands
 * (deal order is player, dealer-up, player, dealer-hole, then draws):
 *
 *   Hand 1  You A♠ J♠ (Blackjack!)   Dealer 9♦ 8♥ (17)     → 3:2 payout, +$150
 *   Hand 2  You 4♠ 5♥ (hard 9)       Dealer 3♦ up, 9♣ hole
 *           Oliver: DOUBLE (the real BasicStrategy call) → 10♦ = 19
 *           Dealer 12 draws 10♠ → 22, bust               → doubled win, +$200
 *
 * Optional `--es demo_scene <blackjack21|oliver|doublewin|roundend>` freezes the
 * autopilot on a chosen beat so a single clean frame can be captured. Omitting
 * the scene plays both hands through for the promo video.
 */
object DemoEntry {

    private const val BET = 200
    private const val STARTING_CHIPS = 500

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(vm: GameViewModel, entitlements: EntitlementStore, scene: String?) {
        if (started) return
        started = true

        // Unlock premium (Oliver + Win Chance) so no paywall interrupts capture.
        entitlements.debugOverride = true

        val key = scene?.lowercase() ?: "full"

        // Hand 1 only for the blackjack beat; hand 2 only for the double-play
        // beats (it deals first so the scene reaches its moment quickly); both
        // hands for the full promo-video run.
        val hands = when (key) {
            "blackjack21" -> NATURAL_21
            "oliver", "doublewin", "roundend" -> DOUBLE_9V3
            else -> NATURAL_21 + DOUBLE_9V3
        }

        vm.enableDemo(
            DemoSeams(
                shoeCards = { hands + filler() },
                startingChips = STARTING_CHIPS,
                advice = ::advice,
            ),
            // The two "settled table" beats freeze before the round-end overlay.
            holdAt = if (key == "blackjack21" || key == "doublewin") GamePhase.SETTLEMENT else null,
        )

        // Captions on for the beats where Oliver's words should be readable on
        // screen (the advice and the round-end recap); off for the settled-table
        // beats where the cards are the star.
        vm.updateCaptionOnly(false)
        vm.updateCaptionsEnabled(key == "oliver" || key == "roundend" || key == "full")

        scope.launch {
            when (key) {
                "blackjack21" -> playBlackjackHand(vm)          // freezes at SETTLEMENT
                "oliver" -> playToDoubleDecision(vm)            // loops Oliver's advice
                "doublewin" -> playDoubleHand(vm)               // freezes at SETTLEMENT
                "roundend" -> { playDoubleHand(vm); loopHoot(vm) }
                else -> playFullPromo(vm)
            }
        }
    }

    // Beats

    /** Setup → betting → chips in → deal. Every scene starts here. */
    private suspend fun openHand(vm: GameViewModel) {
        delay(900L)
        vm.startGame()
        delay(1_200L)
        vm.updateHumanPendingBet(BET)
        delay(1_100L)
        vm.confirmBetsAndDeal()
    }

    /** Hand 1: the natural blackjack resolves itself after the deal. */
    private suspend fun playBlackjackHand(vm: GameViewModel) {
        openHand(vm)
    }

    /** Hand 2 up to the decision point, then keep Oliver speaking on a loop. */
    private suspend fun playToDoubleDecision(vm: GameViewModel) {
        openHand(vm)
        awaitPhase(vm, GamePhase.PLAYER_TURNS)
        delay(1_200L)
        // Re-fire as soon as each clip ends so the speaking state (and the
        // caption card) is live whenever the screenshot fires.
        while (true) {
            if (!vm.advisorState.isLoading && !vm.advisorState.isSpeaking) {
                vm.requestAskOliverAdvice()
            }
            delay(60L)
        }
    }

    /** Hand 2 played through: double → 19, dealer busts. */
    private suspend fun playDoubleHand(vm: GameViewModel) {
        openHand(vm)
        awaitPhase(vm, GamePhase.PLAYER_TURNS)
        delay(900L)
        vm.handlePlayerAction(PlayerAction.Double)
    }

    /** Keep Oliver's round-end Hoot speaking so the equalizer is always live. */
    private suspend fun loopHoot(vm: GameViewModel) {
        awaitPhase(vm, GamePhase.ROUND_END)
        delay(600L)
        while (vm.state.phase == GamePhase.ROUND_END) {
            if (!vm.hootState.isLoading && !vm.hootState.isSpeaking) {
                vm.requestOliversHoot()
            }
            delay(60L)
        }
    }

    /** The full ~25s promo run: blackjack, then the doubled win, then the Hoot. */
    private suspend fun playFullPromo(vm: GameViewModel) {
        // Hand 1 — natural blackjack, 3:2 payout.
        playBlackjackHand(vm)
        awaitPhase(vm, GamePhase.ROUND_END)
        delay(3_000L)

        // Hand 2 — Oliver's double. Bet restores to $100 automatically.
        vm.startNextHand()
        delay(1_400L)
        vm.confirmBetsAndDeal()
        awaitPhase(vm, GamePhase.PLAYER_TURNS)
        delay(1_400L)
        vm.requestAskOliverAdvice()
        delay(600L) // let the request register before polling it
        while (vm.advisorState.isLoading || vm.advisorState.isSpeaking) delay(100L)
        delay(500L)
        vm.handlePlayerAction(PlayerAction.Double)
        loopHoot(vm)
    }

    private suspend fun awaitPhase(vm: GameViewModel, phase: GamePhase) {
        snapshotFlow { vm.state.phase }.first { it == phase }
    }

    // Scripted cards

    /** Hand 1 — natural blackjack: A♠ J♠ vs dealer 9♦ 8♥ (17). */
    private val NATURAL_21 = listOf(
        Card(Suit.SPADES, 14),    // you: A♠
        Card(Suit.DIAMONDS, 9),   // dealer up: 9♦
        Card(Suit.SPADES, 11),    // you: J♠  → Blackjack
        Card(Suit.HEARTS, 8),     // dealer hole: 8♥ → 17, no dealer blackjack
    )

    /** Hand 2 — hard 9 vs 3: double draws 10♦ (19); dealer 12 busts with 10♠. */
    private val DOUBLE_9V3 = listOf(
        Card(Suit.SPADES, 4),     // you: 4♠
        Card(Suit.DIAMONDS, 3),   // dealer up: 3♦
        Card(Suit.HEARTS, 5),     // you: 5♥  → hard 9
        Card(Suit.CLUBS, 9),      // dealer hole: 9♣ → 12
        Card(Suit.DIAMONDS, 10),  // double card: 10♦ → 19
        Card(Suit.SPADES, 10),    // dealer draw: 10♠ → 22, bust
    )

    /**
     * Two shuffled decks behind the scripted cards. Never actually dealt in a
     * scripted run — they just keep the shoe far above the reshuffle cut so the
     * stacked order survives every hand. Seeded so even a stray extra hand is
     * deterministic.
     */
    private fun filler(): List<Card> = buildList {
        repeat(2) {
            for (suit in Suit.entries) for (rank in 2..14) add(Card(suit, rank))
        }
    }.shuffled(Random(42))

    // Offline advisor

    /**
     * Deterministic, network-free stand-in for the advisor endpoint. The
     * recommendation itself comes from the real [BasicStrategy] engine — only
     * the surrounding prose is canned (and debug-only).
     */
    private fun advice(state: GameState, available: Set<PlayerAction>): String {
        if (state.phase == GamePhase.ROUND_END) return recap(state)
        val hand = state.human.activeHand ?: return ""
        val up = state.dealerCards.firstOrNull() ?: return ""
        val move = BasicStrategy.recommend(
            playerCards = hand.cards,
            dealerUpCard = up,
            canDouble = PlayerAction.Double in available,
            canSplit = PlayerAction.Split in available,
            canSurrender = false,
        )
        val total = HandEvaluator.evaluate(hand.cards).displayString()
        val dealer = up.rankWord.lowercase()
        return when (move) {
            StrategyMove.DOUBLE ->
                "Double down. Hard $total against a dealer $dealer is a textbook " +
                    "double — get more chips in while the dealer is weak."
            StrategyMove.HIT ->
                "Hit. $total against a dealer $dealer isn't strong enough to stand on yet."
            StrategyMove.STAND ->
                "Stand. $total against a dealer $dealer — let the dealer take the risk."
            StrategyMove.SPLIT ->
                "Split. Two hands against a dealer $dealer beats one weak one."
            StrategyMove.SURRENDER ->
                "Surrender. $total against a dealer $dealer is a hand worth half your bet."
        }
    }

    private fun recap(state: GameState): String {
        val result = state.roundResults.firstOrNull()
        val won = result?.net ?: 0
        val hand = state.human.hands.firstOrNull()
        val total = hand?.let { HandEvaluator.evaluate(it.cards).displayString() } ?: ""
        return if (hand?.isDoubled == true) {
            "That double is why nine against a three matters. The dealer's three is a " +
                "bust card — they had to keep drawing, and their twelve broke on the ten. " +
                "You pressed your edge, pulled a ten for $total, and turned a small hand " +
                "into a $$won swing. Play it by the book and the book pays you back."
        } else {
            "A natural blackjack — the best hand in the game, and it pays three to two. " +
                "That's $$won on a single hand. Keep them coming."
        }
    }
}

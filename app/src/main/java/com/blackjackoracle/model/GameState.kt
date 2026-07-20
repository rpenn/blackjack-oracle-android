package com.blackjackoracle.model

import androidx.compose.runtime.Immutable

enum class GamePhase {
    SETUP, BETTING, DEALING, INSURANCE, PLAYER_TURNS, DEALER_TURN, SETTLEMENT, ROUND_END, GAME_OVER;

    fun displayName(): String = when (this) {
        SETUP -> "Setup"
        BETTING -> "Place Bet"
        DEALING -> "Dealing"
        INSURANCE -> "Insurance?"
        PLAYER_TURNS -> "Your Move"
        DEALER_TURN -> "Dealer"
        SETTLEMENT -> "Settling"
        ROUND_END -> "Round End"
        GAME_OVER -> "Game Over"
    }
}

/// Win-rate estimates (in %) for each action on the human's active hand.
@Immutable
data class WinChance(
    val ifHit: Double,
    val ifStand: Double,
    val ifDouble: Double,
    val ifSplitHand: Double? = null,
)

@Immutable
data class RoundResult(
    val playerName: String,
    val outcomeLabel: String,
    val handTotal: String,
    val net: Int,
)

@Immutable
data class LastAction(
    val playerName: String,
    val action: String,
)

@Immutable
data class HandActionLog(
    val playerName: String,
    val action: String,
    /// Total after the action, or the dealt total for "dealt" entries.
    val handTotal: String,
    /// Hand cards before this action. Empty for the initial deal entry.
    val cardsBefore: List<Card> = emptyList(),
    /// Hand total before the action. "—" for the initial deal entry.
    val totalBefore: String = "—",
    /// Dealer's up-card at the moment of the decision.
    val dealerUp: Card? = null,
    /// Engine basic-strategy recommendation at this decision point. Nil for
    /// entries that are not actionable decisions (e.g. the initial deal).
    val recommended: String? = null,
)

@Immutable
data class GameState(
    val human: Player = Player(),
    val dealerCards: List<Card> = emptyList(),
    val dealerHoleRevealed: Boolean = false,
    val phase: GamePhase = GamePhase.SETUP,
    val currentRound: Int = 0,
    val winChance: WinChance? = null,
    val roundResults: List<RoundResult> = emptyList(),
    val lastAction: LastAction? = null,
    val isDealAnimating: Boolean = false,
    val actionHistory: List<HandActionLog> = emptyList(),
    val handsPlayed: Int = 0,
    /// True while the guided first hand plays. The scripted win must feed
    /// neither the review-prompt cadence nor the "ended ahead" flag.
    val isTutorial: Boolean = false,
)

object GameConstants {
    const val STARTING_CHIPS = 100
    const val MIN_BET = 1
    const val DECK_COUNT = 8
    /// Reshuffle once ≤ 1.25 decks remain.
    const val RESHUFFLE_AT_CARDS_REMAINING = 65
    const val MAX_HANDS_PER_PLAYER = 4
    const val HUMAN_PLAYER_ID = "human"
    const val DEALER_ID = "dealer"
    const val BLACKJACK_PAYOUT_NUM = 3
    const val BLACKJACK_PAYOUT_DEN = 2
}

package com.blackjackoracle.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.Serializable

enum class GamePhase {
    SETUP,
    BETTING,           // human picks bet; AI bets are auto-set
    DEALING,           // initial 2-card deal animation in flight
    INSURANCE,         // dealer shows Ace, players may insure
    PLAYER_TURNS,      // human + AI players act in turn (one player at a time)
    DEALER_TURN,       // dealer reveals + plays per H17 rule
    SETTLEMENT,        // outcomes computed, chip transfers staged
    ROUND_END,         // overlay shown, "Next Hand" button visible
    GAME_OVER;         // human is out of chips

    fun toRawString(): String = when (this) {
        SETUP -> "setup"
        BETTING -> "betting"
        DEALING -> "dealing"
        INSURANCE -> "insurance"
        PLAYER_TURNS -> "playerTurns"
        DEALER_TURN -> "dealerTurn"
        SETTLEMENT -> "settlement"
        ROUND_END -> "roundEnd"
        GAME_OVER -> "gameOver"
    }

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

@Immutable
data class GameConfig(val aiPlayerCount: Int) : Serializable {
    companion object { private const val serialVersionUID = 1L }
}

/** Per-hand result row for the round-end summary. */
@Immutable
data class RoundResult(
    val playerName: String,
    val outcomeLabel: String,
    val handTotal: String,
    val net: Int  // signed chip delta for this hand (negative = loss)
)

@Immutable
data class LastAction(val playerName: String, val action: String)

@Immutable
data class HandActionLog(val playerName: String, val action: String, val handTotal: String) : Serializable {
    companion object { private const val serialVersionUID = 1L }
}

/**
 * Two pre-computed win-rate estimates for the human player's active hand,
 * each in 0..100. Refreshed whenever the active hand or dealer up-card changes.
 */
@Immutable
data class WinChance(val ifHit: Double, val ifStand: Double)

@Immutable
data class GameState(
    val config: GameConfig,
    val players: ImmutableList<Player>,
    /** Dealer's two-card hand. The hole card is shown face-down until dealer turn. */
    val dealerCards: ImmutableList<Card> = persistentListOf(),
    val dealerHoleRevealed: Boolean = false,
    val phase: GamePhase = GamePhase.SETUP,
    val currentPlayerIndex: Int = 0,
    val currentRound: Int = 0,
    val winChance: WinChance? = null,
    val roundResults: ImmutableList<RoundResult> = persistentListOf(),
    val lastAction: LastAction? = null,
    val isDealAnimating: Boolean = false,
    val actionHistory: ImmutableList<HandActionLog> = persistentListOf(),
    /** Total chips the human won across the session — for Game Over screen. */
    val handsPlayed: Int = 0
)

object GameConstants {
    const val STARTING_CHIPS = 100
    const val MIN_BET = 1
    const val DECK_COUNT = 8
    /** Reshuffle when ≤ 1.25 decks remain (8*52 - 1.25*52 = 351 dealt). */
    const val RESHUFFLE_AT_CARDS_REMAINING = 65
    const val MAX_HANDS_PER_PLAYER = 4
    val AI_NAMES = listOf("Alex", "Blake", "Casey", "Dana", "Ellis")
    const val HUMAN_PLAYER_ID = "human"
    const val DEALER_ID = "dealer"
    /** Blackjack pays 3:2 — i.e., bet 10 → win 15. */
    const val BLACKJACK_PAYOUT_NUM = 3
    const val BLACKJACK_PAYOUT_DEN = 2
}

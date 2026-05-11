package com.blackjackoracle.model

import androidx.compose.runtime.Immutable

enum class GamePhase {
    SETUP, BETTING, DEALING, INSURANCE, PLAYER_TURNS, DEALER_TURN, SETTLEMENT, ROUND_END, GAME_OVER;
    fun displayName(): String = when (this) {
        SETUP -> "Setup"; BETTING -> "Place Bet"; DEALING -> "Dealing"; INSURANCE -> "Insurance?"; PLAYER_TURNS -> "Your Move"; DEALER_TURN -> "Dealer"; SETTLEMENT -> "Settling"; ROUND_END -> "Round End"; GAME_OVER -> "Game Over"
    }
    fun rawName(): String = name.lowercase()
}

@Immutable data class WinChance(val ifHit: Double, val ifStand: Double, val ifDouble: Double, val ifSplitHand: Double? = null)
@Immutable data class RoundResult(val playerName: String, val outcomeLabel: String, val handTotal: String, val net: Int)
@Immutable data class LastAction(val playerName: String, val action: String)
@Immutable data class HandActionLog(val playerName: String, val action: String, val handTotal: String, val cardsBefore: List<Card> = emptyList(), val totalBefore: String = "—", val dealerUp: Card? = null, val recommended: String? = null)

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
    val handsPlayed: Int = 0
)

object GameConstants {
    const val STARTING_CHIPS = 100
    const val MIN_BET = 1
    const val DECK_COUNT = 8
    const val RESHUFFLE_AT_CARDS_REMAINING = 65
    const val MAX_HANDS_PER_PLAYER = 4
    const val HUMAN_PLAYER_ID = "human"
    const val DEALER_ID = "dealer"
    const val BLACKJACK_PAYOUT_NUM = 3
    const val BLACKJACK_PAYOUT_DEN = 2
}

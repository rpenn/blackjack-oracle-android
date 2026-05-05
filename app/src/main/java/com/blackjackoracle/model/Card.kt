package com.blackjackoracle.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

enum class Suit(val symbol: String) {
    HEARTS("♥"),
    DIAMONDS("♦"),
    CLUBS("♣"),
    SPADES("♠");

    val isRed: Boolean get() = this == HEARTS || this == DIAMONDS
}

@Immutable
data class Card(val suit: Suit, val rank: Int) : Serializable {
    val id: String get() = "$rank-${suit.name.lowercase()}"

    val rankLabel: String
        get() = when (rank) {
            14 -> "A"
            13 -> "K"
            12 -> "Q"
            11 -> "J"
            else -> rank.toString()
        }

    val displayString: String get() = "$rankLabel${suit.symbol}"

    val spokenString: String
        get() {
            val rankWord = when (rank) {
                14 -> "Ace"
                13 -> "King"
                12 -> "Queen"
                11 -> "Jack"
                10 -> "Ten"
                9 -> "Nine"
                8 -> "Eight"
                7 -> "Seven"
                6 -> "Six"
                5 -> "Five"
                4 -> "Four"
                3 -> "Three"
                2 -> "Two"
                else -> rank.toString()
            }
            val suitWord = when (suit) {
                Suit.HEARTS -> "Hearts"
                Suit.DIAMONDS -> "Diamonds"
                Suit.CLUBS -> "Clubs"
                Suit.SPADES -> "Spades"
            }
            return "$rankWord of $suitWord"
        }

    /**
     * Blackjack base value: A=11 (soft), J/Q/K=10, else face value.
     * Hand evaluator demotes Aces 11→1 when needed to avoid bust.
     */
    val blackjackValue: Int
        get() = when (rank) {
            14 -> 11
            11, 12, 13 -> 10
            else -> rank
        }

    val isAce: Boolean get() = rank == 14

    /** True if the card counts as 10 in blackjack (10/J/Q/K). */
    val isTen: Boolean get() = rank in 10..13

    companion object {
        private const val serialVersionUID = 1L
    }
}

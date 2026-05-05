package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit

/**
 * Multi-deck blackjack shoe. The game uses 8 decks and triggers a reshuffle
 * once penetration reaches the cut card at 1.25 decks remaining
 * (i.e., 8*52 - 1.25*52 = 351 cards dealt).
 */
class Shoe(val deckCount: Int = 8, private val reshuffleAtCardsRemaining: Int = 65) {
    private val cards: ArrayDeque<Card> = ArrayDeque()
    var needsReshuffle: Boolean = false
        private set

    init { reshuffle() }

    fun reshuffle() {
        cards.clear()
        repeat(deckCount) {
            for (suit in Suit.entries) {
                for (rank in 2..14) cards.add(Card(suit, rank))
            }
        }
        cards.shuffle()
        needsReshuffle = false
    }

    /**
     * Deals one card from the front of the shoe. After the deal, sets
     * [needsReshuffle] true if the cut card has been crossed; the engine
     * finishes the current round before reshuffling.
     */
    fun deal(): Card {
        if (cards.isEmpty()) reshuffle()
        val c = cards.removeFirst()
        if (cards.size <= reshuffleAtCardsRemaining) needsReshuffle = true
        return c
    }

    /** Cards remaining in the shoe — exposed for diagnostics / tests. */
    val remaining: Int get() = cards.size
}

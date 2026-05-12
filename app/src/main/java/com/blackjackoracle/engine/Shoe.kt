package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit

/// Multi-deck blackjack shoe. Defaults to an 8-deck shoe with a cut card at
/// 1.25 decks remaining, matching the iOS reference implementation.
class Shoe(
    private val deckCount: Int = 8,
    private val reshuffleAtCardsRemaining: Int = 65,
) {
    private val cards = ArrayDeque<Card>()

    var needsReshuffle: Boolean = false
        private set

    val remaining: Int get() = cards.size

    init {
        reshuffle()
    }

    fun reshuffle() {
        cards.clear()
        repeat(deckCount) {
            for (suit in Suit.entries) {
                for (rank in 2..14) {
                    cards.add(Card(suit, rank))
                }
            }
        }
        cards.shuffle()
        needsReshuffle = false
    }

    fun deal(): Card {
        if (cards.isEmpty()) reshuffle()
        val card = cards.removeFirst()
        if (cards.size <= reshuffleAtCardsRemaining) needsReshuffle = true
        return card
    }
}

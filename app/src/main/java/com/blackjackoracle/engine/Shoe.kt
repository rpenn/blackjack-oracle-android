package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit

/// Multi-deck blackjack shoe. Defaults to an 8-deck shoe with a cut card at
/// 1.25 decks remaining, matching the iOS reference implementation.
///
/// `stackedCards` lets unit tests inject a deterministic sequence; in that mode
/// `reshuffle()` re-seeds from the same stack rather than generating a fresh
/// shuffled deck.
class Shoe(
    private val deckCount: Int = 8,
    private val reshuffleAtCardsRemaining: Int = 65,
    private val stackedCards: List<Card>? = null,
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
        if (stackedCards != null) {
            cards.addAll(stackedCards)
            needsReshuffle = false
            return
        }
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
        // Reshuffles are scheduled between hands via `needsReshuffle`; reaching
        // an empty shoe mid-hand is a contract violation, not a recoverable
        // state. With 8 decks (416 cards) and a 65-card cut, the worst-case
        // single hand cannot consume more than ~30 cards, so this is loud-fail
        // territory by design.
        check(cards.isNotEmpty()) {
            "Shoe underflow mid-hand — reshuffle should have happened between hands"
        }
        val card = cards.removeFirst()
        if (cards.size <= reshuffleAtCardsRemaining) needsReshuffle = true
        return card
    }
}

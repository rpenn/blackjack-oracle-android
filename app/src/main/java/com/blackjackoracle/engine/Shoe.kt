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

    /// Cards forced to come out next, in order, ahead of the shuffled shoe.
    /// Only the tutorial's scripted hand uses this; `deal()` is the single
    /// draw seam, so rigging here scripts every deal path (initial deal,
    /// split draws, double card, dealer draws) without touching game logic.
    /// Deliberately separate from `stackedCards`: the forced queue does not
    /// participate in reshuffles and falls through to the real shoe when
    /// exhausted, so the underflow guard below stays intact.
    private val forced = ArrayDeque<Card>()

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

    /// Queues `sequence` to be dealt next, first element first, ahead of the
    /// shuffled shoe. Replaces any previously forced cards.
    fun forceNext(sequence: List<Card>) {
        forced.clear()
        forced.addAll(sequence)
    }

    fun deal(): Card {
        forced.removeFirstOrNull()?.let { return it }
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

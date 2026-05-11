package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit

class Shoe(private val deckCount: Int = 8, private val reshuffleAtCardsRemaining: Int = 65) {
    private val cards = ArrayDeque<Card>()
    var needsReshuffle: Boolean = false
        private set
    init { reshuffle() }
    fun reshuffle() {
        cards.clear()
        repeat(deckCount) { for (suit in Suit.entries) for (rank in 2..14) cards.add(Card(suit, rank)) }
        cards.shuffle(); needsReshuffle = false
    }
    fun deal(): Card { if (cards.isEmpty()) reshuffle(); val card = cards.removeFirst(); if (cards.size <= reshuffleAtCardsRemaining) needsReshuffle = true; return card }
    val remaining: Int get() = cards.size
}

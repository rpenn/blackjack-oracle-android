package com.blackjackoracle.engine

import com.blackjackoracle.model.Card

/**
 * Evaluated state of a blackjack hand: best total (≤21 if possible),
 * whether it is soft (contains an Ace counted as 11), bust, blackjack
 * (exactly two cards totalling 21 — the natural).
 */
data class HandValue(
    val total: Int,
    val isSoft: Boolean,
    val isBust: Boolean,
    val isBlackjack: Boolean
) {
    fun displayString(): String =
        when {
            isBlackjack -> "Blackjack"
            isBust -> "Bust"
            isSoft && total <= 21 -> "Soft $total"
            else -> total.toString()
        }
}

object HandEvaluator {
    fun evaluate(cards: List<Card>): HandValue {
        if (cards.isEmpty()) return HandValue(0, false, false, false)
        var total = cards.sumOf { it.blackjackValue }
        var aces = cards.count { it.isAce }
        // Demote Aces 11→1 while busting.
        while (total > 21 && aces > 0) {
            total -= 10
            aces -= 1
        }
        val soft = aces > 0 && total <= 21
        val bj = cards.size == 2 &&
            cards.any { it.isAce } &&
            cards.any { it.isTen }
        return HandValue(
            total = total,
            isSoft = soft,
            isBust = total > 21,
            isBlackjack = bj
        )
    }

    /** Two cards of equal blackjack rank (so a pair that could be split). */
    fun isPair(cards: List<Card>): Boolean {
        if (cards.size != 2) return false
        val a = cards[0]
        val b = cards[1]
        return a.rank == b.rank
    }
}

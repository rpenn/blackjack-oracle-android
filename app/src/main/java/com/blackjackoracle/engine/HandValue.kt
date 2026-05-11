package com.blackjackoracle.engine

import com.blackjackoracle.model.Card

data class HandValue(val total: Int, val isSoft: Boolean, val isBust: Boolean, val isBlackjack: Boolean) {
    fun displayString(): String = when { isBlackjack -> "Blackjack"; isBust -> "Bust"; isSoft && total <= 21 -> "Soft $total"; else -> total.toString() }
}

object HandEvaluator {
    fun evaluate(cards: List<Card>): HandValue {
        if (cards.isEmpty()) return HandValue(0, false, false, false)
        var total = cards.sumOf { it.blackjackValue }
        var aces = cards.count { it.isAce }
        while (total > 21 && aces > 0) { total -= 10; aces -= 1 }
        val soft = aces > 0 && total <= 21
        val blackjack = cards.size == 2 && cards.any { it.isAce } && cards.any { it.hasTenValue }
        return HandValue(total, soft, total > 21, blackjack)
    }
    fun isPair(cards: List<Card>) = cards.size == 2 && cards[0].rank == cards[1].rank
}

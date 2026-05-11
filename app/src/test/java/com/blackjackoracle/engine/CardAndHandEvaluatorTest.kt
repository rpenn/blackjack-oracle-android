package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit
import org.junit.Assert.*
import org.junit.Test

class CardAndHandEvaluatorTest {
    private fun c(rank: Int, suit: Suit = Suit.SPADES) = Card(suit, rank)

    @Test fun cardDerivedValuesMatchSpec() {
        val ace = c(14, Suit.HEARTS)
        assertEquals(11, ace.blackjackValue)
        assertTrue(ace.isAce)
        assertEquals("A", ace.rankLabel)
        assertEquals("Ace", ace.rankWord)
        assertEquals("A♥", ace.displayString)
        assertEquals("Ace of Hearts", ace.spokenString)
        assertTrue(c(12).hasTenValue)
        assertFalse(c(9).hasTenValue)
    }

    @Test fun evaluatorHandlesHardSoftBustAndBlackjack() {
        assertEquals("0", HandEvaluator.evaluate(emptyList()).displayString())
        assertEquals("Blackjack", HandEvaluator.evaluate(listOf(c(14), c(13))).displayString())
        assertEquals("Soft 18", HandEvaluator.evaluate(listOf(c(14), c(7))).displayString())
        assertEquals(12, HandEvaluator.evaluate(listOf(c(14), c(14))).total)
        assertEquals(21, HandEvaluator.evaluate(listOf(c(14), c(9), c(14))).total)
        assertTrue(HandEvaluator.evaluate(listOf(c(10), c(8), c(7))).isBust)
        assertFalse(HandEvaluator.evaluate(listOf(c(7), c(7), c(7))).isBlackjack)
    }

    @Test fun pairUsesExactRankNotTenValue() {
        assertTrue(HandEvaluator.isPair(listOf(c(11), c(11))))
        assertFalse(HandEvaluator.isPair(listOf(c(11), c(12))))
    }
}

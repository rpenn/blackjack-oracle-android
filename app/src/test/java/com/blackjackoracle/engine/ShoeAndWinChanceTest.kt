package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit
import org.junit.Assert.*
import org.junit.Test

class ShoeAndWinChanceTest {
    private fun c(rank: Int) = Card(Suit.SPADES, rank)

    @Test fun shoeHasCorrectSizeAndReshuffleThreshold() {
        val shoe = Shoe(deckCount = 8, reshuffleAtCardsRemaining = 65)
        assertEquals(416, shoe.remaining)
        repeat(351) { shoe.deal() }
        assertEquals(65, shoe.remaining)
        assertTrue(shoe.needsReshuffle)
        shoe.reshuffle()
        assertEquals(416, shoe.remaining)
        assertFalse(shoe.needsReshuffle)
    }

    @Test fun winChanceValuesAreBoundedAndExposeSplitOnlyWhenAllowed() {
        val wc = WinChanceCalculator.compute(listOf(c(8), c(8)), c(6), canSplit = true)
        assertTrue(wc.ifHit in 0.0..100.0)
        assertTrue(wc.ifStand in 0.0..100.0)
        assertTrue(wc.ifDouble in 0.0..100.0)
        assertNotNull(wc.ifSplitHand)
        assertNull(WinChanceCalculator.compute(listOf(c(10), c(6)), c(10), canSplit = false).ifSplitHand)
        assertEquals(0.0, WinChanceCalculator.compute(listOf(c(10), c(8), c(7)), c(6), canSplit = false).ifHit, 0.0)
    }

    @Test fun strongStandingHandBeatsWeakStandingHand() {
        val strong = WinChanceCalculator.compute(listOf(c(10), c(10)), c(6), canSplit = false)
        val weak = WinChanceCalculator.compute(listOf(c(10), c(6)), c(10), canSplit = false)
        assertTrue(strong.ifStand > weak.ifStand)
    }
}

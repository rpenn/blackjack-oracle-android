package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

class BasicStrategyTest {
    private fun c(rank: Int) = Card(Suit.CLUBS, rank)
    private fun rec(cards: List<Card>, up: Int, double: Boolean = true, split: Boolean = true, surrender: Boolean = true) = BasicStrategy.recommend(cards, c(up), double, split, surrender)

    @Test fun hardTotalsFollowH17DasChart() {
        assertEquals(StrategyMove.HIT, rec(listOf(c(5), c(3)), 6))
        assertEquals(StrategyMove.DOUBLE, rec(listOf(c(5), c(4)), 3))
        assertEquals(StrategyMove.HIT, rec(listOf(c(5), c(4)), 2))
        assertEquals(StrategyMove.DOUBLE, rec(listOf(c(6), c(4)), 9))
        assertEquals(StrategyMove.HIT, rec(listOf(c(6), c(4)), 10))
        assertEquals(StrategyMove.DOUBLE, rec(listOf(c(6), c(5)), 14))
        assertEquals(StrategyMove.STAND, rec(listOf(c(10), c(2)), 4))
        assertEquals(StrategyMove.HIT, rec(listOf(c(10), c(2)), 3))
        assertEquals(StrategyMove.STAND, rec(listOf(c(10), c(6)), 6, surrender = false))
    }

    @Test fun softTotalsAndDoubleFallbacksWork() {
        assertEquals(StrategyMove.DOUBLE, rec(listOf(c(14), c(2)), 5))
        assertEquals(StrategyMove.HIT, rec(listOf(c(14), c(2)), 5, double = false))
        assertEquals(StrategyMove.DOUBLE, rec(listOf(c(14), c(7)), 3))
        assertEquals(StrategyMove.STAND, rec(listOf(c(14), c(7)), 8))
        assertEquals(StrategyMove.HIT, rec(listOf(c(14), c(7)), 9))
        assertEquals(StrategyMove.DOUBLE, rec(listOf(c(14), c(8)), 6))
        assertEquals(StrategyMove.STAND, rec(listOf(c(14), c(9)), 10))
    }

    @Test fun pairSplitDecisionsMatchH17DasChart() {
        // A,A and 8,8 split against everything.
        assertEquals(StrategyMove.SPLIT, rec(listOf(c(14), c(14)), 10))
        assertEquals(StrategyMove.SPLIT, rec(listOf(c(8), c(8)), 14))
        // 10,10 never splits.
        assertEquals(StrategyMove.STAND, rec(listOf(c(10), c(10)), 6))
        // 9,9 splits vs 2-6, 8, 9 — stands vs 7, 10, A.
        assertEquals(StrategyMove.SPLIT, rec(listOf(c(9), c(9)), 9))
        assertEquals(StrategyMove.STAND, rec(listOf(c(9), c(9)), 7))
    }

    /// Regression test for the 6,6 vs 7 audit finding. Earlier code incorrectly
    /// split 6,6 vs 7; the H17 DAS chart says split only vs 2-6.
    @Test fun pair6sSplitVs2Through6AndHitOtherwise() {
        for (up in 2..6) {
            assertEquals("6,6 vs $up should split", StrategyMove.SPLIT, rec(listOf(c(6), c(6)), up))
        }
        assertEquals("6,6 vs 7 should hit, not split", StrategyMove.HIT, rec(listOf(c(6), c(6)), 7))
        assertEquals("6,6 vs 8 should hit", StrategyMove.HIT, rec(listOf(c(6), c(6)), 8))
        assertEquals("6,6 vs 10 should hit", StrategyMove.HIT, rec(listOf(c(6), c(6)), 10))
    }

    @Test fun surrenderTableMatchesH17Chart() {
        // 16 vs 9, 10, A.
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(6)), 9, split = false))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(6)), 10, split = false))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(6)), 14, split = false))
        // 15 vs 9, 10, A — H17 includes vs 9, which the previous table missed.
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(5)), 9, split = false))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(5)), 10, split = false))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(5)), 14, split = false))
        // 17 vs A — H17 only.
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(7)), 14, split = false))
        // Hard 14 should NEVER surrender — was wrongly surrendering vs A.
        assertEquals(StrategyMove.HIT, rec(listOf(c(10), c(4)), 14, split = false))
        assertEquals(StrategyMove.HIT, rec(listOf(c(10), c(4)), 10, split = false))
    }
}

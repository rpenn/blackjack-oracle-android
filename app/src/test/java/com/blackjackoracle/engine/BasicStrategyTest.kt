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

    @Test fun pairAndSurrenderTablesMatchSpec() {
        assertEquals(StrategyMove.SPLIT, rec(listOf(c(14), c(14)), 10))
        assertEquals(StrategyMove.STAND, rec(listOf(c(10), c(10)), 6))
        assertEquals(StrategyMove.SPLIT, rec(listOf(c(9), c(9)), 9))
        assertEquals(StrategyMove.STAND, rec(listOf(c(9), c(9)), 7))
        assertEquals(StrategyMove.SPLIT, rec(listOf(c(8), c(8)), 14))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(6)), 9, split = false))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(5)), 10, split = false))
        assertEquals(StrategyMove.SURRENDER, rec(listOf(c(10), c(4)), 14, split = false))
    }
}

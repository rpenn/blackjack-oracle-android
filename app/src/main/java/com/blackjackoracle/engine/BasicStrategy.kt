package com.blackjackoracle.engine

import com.blackjackoracle.model.Card

enum class StrategyMove { HIT, STAND, DOUBLE, SPLIT, SURRENDER }

object BasicStrategy {
    fun recommend(playerCards: List<Card>, dealerUpCard: Card, canDouble: Boolean, canSplit: Boolean, canSurrender: Boolean): StrategyMove {
        val up = upIndex(dealerUpCard)
        if (canSplit && HandEvaluator.isPair(playerCards) && pairAction(playerCards[0].rank, up) == StrategyMove.SPLIT) return StrategyMove.SPLIT
        val value = HandEvaluator.evaluate(playerCards)
        if (canSurrender && surrender(value.total, value.isSoft, up)) return StrategyMove.SURRENDER
        return if (value.isSoft) soft(value.total, up, canDouble) else hard(value.total, up, canDouble)
    }
    private fun upIndex(c: Card) = when { c.isAce -> 9; c.rank in 10..13 -> 8; else -> c.rank - 2 }
    private fun hard(total: Int, up: Int, canDouble: Boolean) = when {
        total <= 8 -> StrategyMove.HIT
        total == 9 -> if (canDouble && up in 1..4) StrategyMove.DOUBLE else StrategyMove.HIT
        total == 10 -> if (canDouble && up <= 7) StrategyMove.DOUBLE else StrategyMove.HIT
        total == 11 -> if (canDouble) StrategyMove.DOUBLE else StrategyMove.HIT
        total == 12 -> if (up in 2..4) StrategyMove.STAND else StrategyMove.HIT
        total in 13..16 -> if (up in 0..4) StrategyMove.STAND else StrategyMove.HIT
        else -> StrategyMove.STAND
    }
    private fun soft(total: Int, up: Int, canDouble: Boolean) = when (total) {
        13, 14 -> if (canDouble && up in 3..4) StrategyMove.DOUBLE else StrategyMove.HIT
        15, 16 -> if (canDouble && up in 2..4) StrategyMove.DOUBLE else StrategyMove.HIT
        17 -> if (canDouble && up in 1..4) StrategyMove.DOUBLE else StrategyMove.HIT
        18 -> when { canDouble && up in 1..4 -> StrategyMove.DOUBLE; up in 0..6 -> StrategyMove.STAND; else -> StrategyMove.HIT }
        19 -> if (canDouble && up == 4) StrategyMove.DOUBLE else StrategyMove.STAND
        20, 21 -> StrategyMove.STAND
        else -> StrategyMove.HIT
    }
    private fun pairAction(rank: Int, up: Int): StrategyMove = when (if (rank in 11..13) 10 else rank) {
        14 -> StrategyMove.SPLIT
        10 -> StrategyMove.STAND
        9 -> if (up in 0..4 || up == 6 || up == 7) StrategyMove.SPLIT else StrategyMove.STAND
        8 -> StrategyMove.SPLIT
        7 -> if (up in 0..5) StrategyMove.SPLIT else StrategyMove.HIT
        6 -> if (up in 0..5) StrategyMove.SPLIT else StrategyMove.HIT
        5 -> StrategyMove.STAND
        4 -> if (up in 3..4) StrategyMove.SPLIT else StrategyMove.HIT
        3, 2 -> if (up in 0..5) StrategyMove.SPLIT else StrategyMove.HIT
        else -> StrategyMove.HIT
    }
    private fun surrender(total: Int, soft: Boolean, up: Int): Boolean {
        if (soft) return false
        return when (total) { 14 -> up == 9; 15 -> up == 8 || up == 9; 16 -> up in 7..9; 17 -> up == 9; else -> false }
    }
}

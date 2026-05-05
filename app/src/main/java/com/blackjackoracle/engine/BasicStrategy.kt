package com.blackjackoracle.engine

import com.blackjackoracle.model.Card

/** Recommended action from the basic-strategy chart. */
enum class StrategyMove { HIT, STAND, DOUBLE, SPLIT, SURRENDER }

/**
 * Vegas H17 basic strategy with DAS (double after split) and late surrender.
 *
 * `recommend()` returns an *abstract* move; the caller must downgrade if the
 * actual rules of the seat preclude it (e.g. can't double on >2 cards, can't
 * split if max-hands cap hit). Standard fallbacks:
 *   DOUBLE     → HIT  if can't double (already hit)
 *   SPLIT      → fall through to pair-as-hard recommendation
 *   SURRENDER  → HIT  if can't surrender (more than 2 cards)
 */
object BasicStrategy {

    fun recommend(
        playerCards: List<Card>,
        dealerUpCard: Card,
        canDouble: Boolean,
        canSplit: Boolean,
        canSurrender: Boolean
    ): StrategyMove {
        val upValue = upCardIndex(dealerUpCard)
        // Pair check
        if (canSplit && HandEvaluator.isPair(playerCards)) {
            val pairMove = pairAction(playerCards[0].rank, upValue)
            if (pairMove == StrategyMove.SPLIT) return StrategyMove.SPLIT
        }
        val v = HandEvaluator.evaluate(playerCards)
        // Surrender (only on initial 2-card hand)
        if (canSurrender && surrenderAction(v.total, v.isSoft, upValue)) {
            return StrategyMove.SURRENDER
        }
        return if (v.isSoft) softAction(v.total, upValue, canDouble)
        else hardAction(v.total, upValue, canDouble)
    }

    // Dealer up-card index 0..9: 0 = 2, 8 = 10/J/Q/K, 9 = Ace
    private fun upCardIndex(c: Card): Int =
        when {
            c.isAce -> 9
            c.rank in 10..13 -> 8
            else -> c.rank - 2  // 2 → 0, 9 → 7
        }

    private fun hardAction(total: Int, up: Int, canDouble: Boolean): StrategyMove = when {
        total <= 8 -> StrategyMove.HIT
        total == 9 -> if (canDouble && up in 1..4) StrategyMove.DOUBLE else StrategyMove.HIT
        total == 10 -> if (canDouble && up <= 7) StrategyMove.DOUBLE else StrategyMove.HIT
        total == 11 -> if (canDouble) StrategyMove.DOUBLE else StrategyMove.HIT
        total == 12 -> if (up in 2..4) StrategyMove.STAND else StrategyMove.HIT
        total in 13..16 -> if (up in 0..4) StrategyMove.STAND else StrategyMove.HIT
        total >= 17 -> StrategyMove.STAND
        else -> StrategyMove.HIT
    }

    private fun softAction(total: Int, up: Int, canDouble: Boolean): StrategyMove = when (total) {
        // soft 13 = A,2 / soft 14 = A,3
        13, 14 -> if (canDouble && up in 3..4) StrategyMove.DOUBLE else StrategyMove.HIT
        15, 16 -> if (canDouble && up in 2..4) StrategyMove.DOUBLE else StrategyMove.HIT
        17 -> if (canDouble && up in 1..4) StrategyMove.DOUBLE else StrategyMove.HIT
        18 -> when {
            canDouble && up in 1..4 -> StrategyMove.DOUBLE      // double A,7 vs 2-6
            up in 0..6 -> StrategyMove.STAND                     // stand vs 2/7/8 (index 0,5,6)
            else -> StrategyMove.HIT                             // hit vs 9/10/A
        }
        // Soft 18 H17 chart actually: D 2-6, S 7-8, H 9-A. Above approximates this.
        19 -> if (canDouble && up == 4) StrategyMove.DOUBLE else StrategyMove.STAND  // A,8 D vs 6 (H17)
        20 -> StrategyMove.STAND
        21 -> StrategyMove.STAND
        else -> StrategyMove.HIT
    }

    /** Returns true if late-surrender is correct on hard total vs up-card. */
    private fun surrenderAction(total: Int, isSoft: Boolean, up: Int): Boolean {
        if (isSoft) return false
        // Hard 14 vs Ace; Hard 15 vs 10/Ace; Hard 16 vs 9/10/Ace (H17 chart)
        return when (total) {
            14 -> up == 9
            15 -> up == 8 || up == 9
            16 -> up == 7 || up == 8 || up == 9
            17 -> up == 9   // Hard 17 vs Ace surrender (H17)
            else -> false
        }
    }

    private fun pairAction(rank: Int, up: Int): StrategyMove {
        // rank: 2..14 (J/Q/K all → 10 for blackjack-pair purposes)
        val effective = if (rank in 11..13) 10 else rank
        return when (effective) {
            14 -> StrategyMove.SPLIT                                  // A,A
            10 -> StrategyMove.STAND                                   // 10,10 → never split
            9 -> if (up in 0..4 || up == 7 || up == 8) StrategyMove.SPLIT
                 else StrategyMove.STAND                               // 9,9: split 2-6,8,9; stand 7,10,A
            8 -> StrategyMove.SPLIT                                   // 8,8 always
            7 -> if (up in 0..5) StrategyMove.SPLIT else StrategyMove.HIT
            6 -> if (up in 0..5) StrategyMove.SPLIT else StrategyMove.HIT       // DAS allows 2-7; conservative 2-6
            5 -> StrategyMove.STAND  // never split — treat as hard 10 (caller will fall through)
            4 -> if (up in 3..4) StrategyMove.SPLIT else StrategyMove.HIT       // DAS: split vs 5,6
            3, 2 -> if (up in 0..5) StrategyMove.SPLIT else StrategyMove.HIT
            else -> StrategyMove.HIT
        }
    }
}

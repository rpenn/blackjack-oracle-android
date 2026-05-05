package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.WinChance

/**
 * Analytic win-chance calculator. Returns two equity numbers (in %) for the
 * human's active hand against the dealer's up-card:
 *
 *   ifStand — equity if the player stands now
 *   ifHit   — equity if the player takes exactly one more card and then
 *             plays optimally
 *
 * Equity = P(win) + 0.5·P(push). Computed under an infinite-deck
 * approximation, which is very close to correct for an 8-deck shoe and lets
 * us recurse exactly without Monte Carlo noise. Memoized on (total, isSoft)
 * so each recompute completes in well under a millisecond.
 */
object WinChance {
    /** Card-rank probabilities for an infinite shoe (10/J/Q/K share index 10). */
    private val rankProbs: List<Pair<Int, Double>> = listOf(
        2 to 1.0 / 13.0,
        3 to 1.0 / 13.0,
        4 to 1.0 / 13.0,
        5 to 1.0 / 13.0,
        6 to 1.0 / 13.0,
        7 to 1.0 / 13.0,
        8 to 1.0 / 13.0,
        9 to 1.0 / 13.0,
        10 to 4.0 / 13.0,
        11 to 1.0 / 13.0   // Ace, taken soft initially
    )

    fun compute(playerCards: List<Card>, dealerUpCard: Card): WinChance {
        val eval = HandEvaluator.evaluate(playerCards)
        if (eval.isBust) return WinChance(ifHit = 0.0, ifStand = 0.0)

        val dealerDist = dealerOutcomeDist(dealerUpCard)
        val standEq = standEquity(eval.total, dealerDist)
        val optimalMemo = HashMap<Long, Double>()
        val hitEq = forcedHitEquity(eval.total, eval.isSoft, dealerDist, optimalMemo)

        // New: Double down equity (exactly one card)
        var doubleEq: Double? = null
        if (playerCards.size == 2) {
            doubleEq = exactlyOneHitEquity(eval.total, eval.isSoft, dealerDist) * 100.0
        }

        // New: Split equity (if pair)
        var splitEq: Double? = null
        if (playerCards.size == 2 && playerCards[0].rank == playerCards[1].rank) {
            // Approximation: equity of one hand starting with this card
            val singleCardEval = HandEvaluator.evaluate(listOf(playerCards[0]))
            val memoSplit = HashMap<Long, Double>()
            splitEq = forcedHitEquity(singleCardEval.total, singleCardEval.isSoft, dealerDist, memoSplit) * 100.0
        }

        return WinChance(
            ifHit = hitEq * 100.0,
            ifStand = standEq * 100.0,
            ifDouble = doubleEq,
            ifSplit = splitEq
        )
    }

    // ---- Player side ----

    /** Equity if player takes EXACTLY one card and then stands. */
    private fun exactlyOneHitEquity(total: Int, isSoft: Boolean, dealerDist: DoubleArray): Double {
        var equity = 0.0
        for ((rank, prob) in rankProbs) {
            val draw = applyDraw(total, isSoft, rank)
            if (!draw.bust) {
                equity += prob * standEquity(draw.total, dealerDist)
            }
        }
        return equity
    }

    /** Equity if player stands at [playerTotal] given dealer's outcome distribution. */
    private fun standEquity(playerTotal: Int, dealerDist: DoubleArray): Double {
        if (playerTotal > 21) return 0.0
        var win = dealerDist[0]   // dealer bust
        var push = 0.0
        for (t in 17..21) {
            val p = dealerDist[t - 16]
            when {
                playerTotal > t -> win += p
                playerTotal == t -> push += p
            }
        }
        return win + 0.5 * push
    }

    /** Equity if player MUST take one card now, then plays optimally. */
    private fun forcedHitEquity(
        total: Int,
        isSoft: Boolean,
        dealerDist: DoubleArray,
        memo: HashMap<Long, Double>
    ): Double {
        var equity = 0.0
        for ((rank, prob) in rankProbs) {
            val draw = applyDraw(total, isSoft, rank)
            if (draw.bust) {
                // bust contributes 0 equity
                continue
            }
            equity += prob * optimalEquity(draw.total, draw.isSoft, dealerDist, memo)
        }
        return equity
    }

    /** Optimal equity from this state (player chooses better of stand/hit). */
    private fun optimalEquity(
        total: Int,
        isSoft: Boolean,
        dealerDist: DoubleArray,
        memo: HashMap<Long, Double>
    ): Double {
        if (total >= 21) return standEquity(total, dealerDist)  // 21 always stands
        val key = (total.toLong() shl 1) or (if (isSoft) 1L else 0L)
        memo[key]?.let { return it }
        val stand = standEquity(total, dealerDist)
        val hit = forcedHitEquity(total, isSoft, dealerDist, memo)
        val best = maxOf(stand, hit)
        memo[key] = best
        return best
    }

    // ---- Dealer side ----

    /**
     * Dealer outcome distribution given visible up-card. Returns DoubleArray of
     * length 6: [bust, 17, 18, 19, 20, 21]. Dealer hits to 17, hits soft 17.
     */
    private fun dealerOutcomeDist(upCard: Card): DoubleArray {
        // Up-card defines starting (total, isSoft); dealer then draws repeatedly.
        val initTotal = upCard.blackjackValue
        val initSoft = upCard.isAce
        val memo = HashMap<Long, DoubleArray>()
        return dealerDistFrom(initTotal, initSoft, memo)
    }

    private fun dealerDistFrom(
        total: Int,
        isSoft: Boolean,
        memo: HashMap<Long, DoubleArray>
    ): DoubleArray {
        val key = (total.toLong() shl 1) or (if (isSoft) 1L else 0L)
        memo[key]?.let { return it.copyOf() }
        val r = DoubleArray(6)
        // H17: dealer stands on hard 17+ and on soft 18+, hits soft 17.
        val stands = total >= 18 || (total == 17 && !isSoft)
        if (total > 21) {
            r[0] = 1.0
        } else if (stands) {
            r[total - 16] = 1.0
        } else {
            for ((rank, prob) in rankProbs) {
                val draw = applyDraw(total, isSoft, rank)
                val sub = dealerDistFrom(draw.total, draw.isSoft, memo)
                for (k in 0..5) r[k] += prob * sub[k]
            }
        }
        memo[key] = r.copyOf()
        return r
    }

    // ---- Shared helpers ----

    private data class DrawResult(val total: Int, val isSoft: Boolean, val bust: Boolean)

    /**
     * Draws a card of [rank] (where Ace=11) onto a hand of (total, isSoft)
     * and returns the resulting (total, isSoft, bust). Ace is taken as 11
     * unless that would bust; soft hands demote on bust.
     */
    private fun applyDraw(total: Int, isSoft: Boolean, rank: Int): DrawResult {
        return if (rank == 11) {
            // Ace: take as 11 if it fits, else 1
            val plus11 = total + 11
            if (plus11 <= 21) DrawResult(plus11, true, false)
            else DrawResult(total + 1, isSoft, total + 1 > 21)
        } else {
            var newTotal = total + rank
            var newSoft = isSoft
            if (newTotal > 21 && newSoft) {
                // Demote the soft Ace
                newTotal -= 10
                newSoft = false
            }
            DrawResult(newTotal, newSoft, newTotal > 21)
        }
    }
}

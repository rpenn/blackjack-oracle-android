package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.WinChance

/// Win-probability estimator for the player's active hand under each available
/// action. Uses single-card rank probabilities (10 weighted 4×) and a memoized
/// dealer outcome distribution; mirrors the iOS reference implementation.
object WinChanceCalculator {

    private val ranks: List<Pair<Int, Double>> =
        (2..9).map { it to 1.0 / 13 } + (10 to 4.0 / 13) + (11 to 1.0 / 13)

    fun compute(playerCards: List<Card>, dealerUp: Card, canSplit: Boolean): WinChance {
        val value = HandEvaluator.evaluate(playerCards)
        if (value.isBust) return WinChance(0.0, 0.0, 0.0, null)
        val dealer = dealerDistribution(dealerUp)
        val memo = HashMap<Long, Double>()
        val hit = forcedHit(value.total, value.isSoft, dealer, memo) * 100
        val stand = stand(value.total, dealer) * 100
        val double = oneCardStand(value.total, value.isSoft, dealer) * 100
        val split = if (canSplit && HandEvaluator.isPair(playerCards)) {
            splitHand(playerCards[0], dealer) * 100
        } else null
        return WinChance(hit, stand, double, split)
    }

    private fun splitHand(card: Card, dealer: DoubleArray): Double {
        val start = HandEvaluator.evaluate(listOf(card))
        val memo = HashMap<Long, Double>()
        var equity = 0.0
        for ((rank, p) in ranks) {
            val d = draw(start.total, start.isSoft, rank)
            if (!d.bust) {
                equity += p * if (card.isAce) stand(d.total, dealer) else optimal(d.total, d.soft, dealer, memo)
            }
        }
        return equity
    }

    private fun oneCardStand(total: Int, soft: Boolean, dealer: DoubleArray): Double {
        var equity = 0.0
        for ((rank, p) in ranks) {
            val d = draw(total, soft, rank)
            if (!d.bust) equity += p * stand(d.total, dealer)
        }
        return equity
    }

    private fun forcedHit(total: Int, soft: Boolean, dealer: DoubleArray, memo: HashMap<Long, Double>): Double {
        var equity = 0.0
        for ((rank, p) in ranks) {
            val d = draw(total, soft, rank)
            if (!d.bust) equity += p * optimal(d.total, d.soft, dealer, memo)
        }
        return equity
    }

    private fun optimal(total: Int, soft: Boolean, dealer: DoubleArray, memo: HashMap<Long, Double>): Double {
        if (total >= 21) return stand(total, dealer)
        val key = (total.toLong() shl 1) or (if (soft) 1 else 0).toLong()
        return memo.getOrPut(key) {
            maxOf(stand(total, dealer), forcedHit(total, soft, dealer, memo))
        }
    }

    private fun stand(player: Int, dealer: DoubleArray): Double {
        if (player > 21) return 0.0
        var win = dealer[0]                       // dealer bust
        var push = 0.0
        for (t in 17..21) {
            val p = dealer[t - 16]
            when {
                player > t -> win += p
                player == t -> push += p
            }
        }
        return win + 0.5 * push
    }

    /// Returns the dealer's outcome distribution conditioned on the post-peek
    /// game state. When the up-card is an Ace, the hole card cannot be a
    /// 10-value (else dealer would have shown blackjack and the player wouldn't
    /// be deciding). When the up-card is a 10-value, the hole cannot be an Ace.
    /// Other up-cards are unconditional. Failing to condition under-weights
    /// dealer-bust outcomes by ~5 percentage points on Ace and 10 upcards.
    private fun dealerDistribution(up: Card): DoubleArray {
        val memo = HashMap<Long, DoubleArray>()
        return when {
            up.isAce -> conditionalHoleDistribution(up, memo, excludeRank = 10, excludedMass = 4.0 / 13)
            up.hasTenValue -> conditionalHoleDistribution(up, memo, excludeRank = 11, excludedMass = 1.0 / 13)
            else -> dealerFrom(up.blackjackValue, up.isAce, memo)
        }
    }

    private fun conditionalHoleDistribution(
        up: Card,
        memo: HashMap<Long, DoubleArray>,
        excludeRank: Int,
        excludedMass: Double,
    ): DoubleArray {
        val out = DoubleArray(6)
        val normalize = 1.0 / (1.0 - excludedMass)
        for ((rank, p) in ranks) {
            if (rank == excludeRank) continue
            val d = draw(up.blackjackValue, up.isAce, rank)
            val sub = dealerFrom(d.total, d.soft, memo)
            for (i in out.indices) out[i] += p * normalize * sub[i]
        }
        return out
    }

    private fun dealerFrom(total: Int, soft: Boolean, memo: HashMap<Long, DoubleArray>): DoubleArray {
        val key = (total.toLong() shl 1) or (if (soft) 1 else 0).toLong()
        memo[key]?.let { return it.copyOf() }

        val out = DoubleArray(6)
        val stands = total >= 18 || (total == 17 && !soft)
        when {
            total > 21 -> out[0] = 1.0
            stands -> out[total - 16] = 1.0
            else -> {
                for ((rank, p) in ranks) {
                    val d = draw(total, soft, rank)
                    val sub = dealerFrom(d.total, d.soft, memo)
                    for (i in out.indices) out[i] += p * sub[i]
                }
            }
        }
        memo[key] = out.copyOf()
        return out
    }

    private data class Draw(val total: Int, val soft: Boolean, val bust: Boolean)

    private fun draw(total: Int, soft: Boolean, rank: Int): Draw {
        if (rank == 11) {
            val t = total + 11
            return if (t <= 21) Draw(t, soft = true, bust = false)
            else Draw(total + 1, soft, bust = total + 1 > 21)
        }
        var t = total + rank
        var s = soft
        if (t > 21 && s) {
            t -= 10
            s = false
        }
        return Draw(t, s, bust = t > 21)
    }
}

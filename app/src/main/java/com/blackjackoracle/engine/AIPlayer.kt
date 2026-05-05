package com.blackjackoracle.engine

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.PlayerAction
import kotlin.random.Random

/**
 * AI player decision logic.
 *
 * AIs at the table are atmospheric — each plays exactly one hand against the
 * dealer (no splits, no doubles, no insurance, no surrender) so the UI stays
 * focused on the human's hands. Decision is hit-or-stand, driven by basic
 * strategy with skill-level noise.
 *
 * Skill 1 = high noise (frequently misplays), 5 = nearly always correct.
 */
object AIPlayer {

    fun decideAction(playerCards: List<Card>, dealerUpCard: Card, skill: Int): PlayerAction {
        val s = skill.coerceIn(1, 5)
        // Probability of deviating from basic strategy
        val mistakeChance = 0.05 + (5 - s) * 0.07   // skill 1 → ~33%, skill 5 → 5%
        val correct = BasicStrategy.recommend(
            playerCards = playerCards,
            dealerUpCard = dealerUpCard,
            canDouble = false,
            canSplit = false,
            canSurrender = false
        )
        val intended = if (Random.nextDouble() < mistakeChance) {
            // Pick the wrong direction at random
            if (correct == StrategyMove.HIT) StrategyMove.STAND else StrategyMove.HIT
        } else correct

        return when (intended) {
            StrategyMove.STAND, StrategyMove.SURRENDER -> PlayerAction.Stand
            else -> PlayerAction.Hit
        }
    }

    /** AI's bet size for this round. Roughly tracks skill — higher skill bets bigger. */
    fun decideBet(skill: Int, chips: Int): Int {
        if (chips <= 0) return 0
        val s = skill.coerceIn(1, 5)
        // Skill 1 bets $1-3; skill 5 bets $5-15
        val low = s
        val high = s * 3
        val raw = Random.nextInt(low, high + 1)
        return raw.coerceAtMost(chips).coerceAtLeast(1)
    }
}

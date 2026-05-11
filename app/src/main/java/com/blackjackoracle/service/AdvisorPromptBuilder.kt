package com.blackjackoracle.service

import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState

object AdvisorPromptBuilder {
    fun cacheKey(state: GameState): String {
        return listOf(
            state.phase.name,
            state.human.activeHand?.cards?.joinToString { it.id }.orEmpty(),
            state.dealerCards.joinToString { it.id },
            state.roundResults.joinToString { "${it.outcomeLabel}:${it.net}" },
        ).joinToString("|")
    }

    fun build(state: GameState): String {
        val hand = state.human.activeHand
        val handCards = hand?.cards?.joinToString { it.spokenString } ?: "no active cards"
        val dealerUp = state.dealerCards.firstOrNull()?.spokenString ?: "no up card"
        val total = hand?.cards?.let { HandEvaluator.evaluate(it).displayString() } ?: "unknown"
        val recent = state.actionHistory.takeLast(6).joinToString("; ") { "${it.playerName} ${it.action}" }

        return if (state.phase == GamePhase.ROUND_END) {
            val dealerHand = state.dealerCards.joinToString { it.spokenString }
            val results = state.roundResults.joinToString("; ") {
                "${it.outcomeLabel}, net ${it.net} dollars"
            }
            """
            You are a blackjack advisor speaking directly to the player. Your response will be read aloud by a text-to-speech service, so write exactly as you would speak. Use plain sentences, no bullet points, no markdown, no symbols.

            The hand just finished.
            Player final hand: $handCards (total: $total)
            Dealer hand: $dealerHand
            Outcome: $results
            Player actions: $recent

            In 2 to 3 spoken sentences, recap the hand. If the player made a notable mistake or smart play, mention it.
            """.trimIndent()
        } else {
            val winChance = state.winChance
            val actionNames = buildList {
                add("hit")
                add("stand")
                if (winChance?.ifDouble != null) add("double down")
                if (winChance?.ifSplitHand != null) add("split")
                add("surrender")
                if (state.phase == GamePhase.INSURANCE) add("take insurance")
            }.joinToString()

            """
            You are a blackjack advisor speaking directly to the player. Your response will be read aloud by a text-to-speech service, so write exactly as you would speak. Use plain sentences, no bullet points, no markdown, no symbols, no dollar signs, and no percent signs.

            The player holds exactly these cards: $handCards.
            Current situation:
            Phase: ${state.phase.displayName()}
            Player hand: $handCards (total: $total)
            Dealer shows: $dealerUp
            Bet on this hand: ${hand?.bet ?: state.human.pendingBet} dollars
            Win chance if you hit: ${winChance?.ifHit?.toInt() ?: "unknown"} percent
            Win chance if you stand: ${winChance?.ifStand?.toInt() ?: "unknown"} percent
            Available actions: $actionNames
            Recent actions: $recent

            In 2 to 3 spoken sentences, recommend a single action and briefly explain the reasoning. Use Vegas basic strategy as your guide.
            """.trimIndent()
        }
    }
}

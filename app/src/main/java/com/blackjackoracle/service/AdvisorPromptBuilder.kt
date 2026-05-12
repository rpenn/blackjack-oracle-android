package com.blackjackoracle.service

import com.blackjackoracle.engine.BasicStrategy
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.StrategyMove
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.HandActionLog
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.RoundResult
import com.blackjackoracle.model.WinChance

/// Snapshot the advisor needs to write a prompt. Mirrors iOS `AdvisorContext`.
data class AdvisorContext(
    val phase: GamePhase,
    val playerCards: List<Card>,
    val dealerCards: List<Card>,
    val handTotal: String,
    val bet: Int,
    val winChance: WinChance?,
    val available: Set<PlayerAction>,
    val canInsure: Boolean,
    val actionHistory: List<HandActionLog>,
    val roundResults: List<RoundResult>,
) {
    companion object {
        fun from(state: GameState, available: Set<PlayerAction>): AdvisorContext {
            val hand = state.human.activeHand
            val cards = hand?.cards.orEmpty()
            val total = if (cards.isEmpty()) "—" else HandEvaluator.evaluate(cards).displayString()
            return AdvisorContext(
                phase = state.phase,
                playerCards = cards,
                dealerCards = state.dealerCards,
                handTotal = total,
                bet = hand?.bet ?: state.human.pendingBet,
                winChance = state.winChance,
                available = available,
                canInsure = state.phase == GamePhase.INSURANCE,
                actionHistory = state.actionHistory,
                roundResults = state.roundResults,
            )
        }
    }
}

object AdvisorPromptBuilder {

    fun cacheKey(state: GameState): String = buildString {
        append(state.phase.name); append('|')
        state.human.activeHand?.cards?.joinToString(",") { it.id }?.let(::append); append('|')
        append(state.dealerCards.joinToString(",") { it.id }); append('|')
        if (state.phase == GamePhase.ROUND_END) {
            append(state.roundResults.joinToString(",") { "${it.playerName}:${it.outcomeLabel}:${it.net}" })
        }
    }

    fun build(context: AdvisorContext): String =
        if (context.phase == GamePhase.ROUND_END) buildSummary(context) else buildAdvice(context)

    // MARK: - In-hand advice prompt

    private fun buildAdvice(c: AdvisorContext): String {
        val handCards = if (c.playerCards.isEmpty()) "none" else c.playerCards.joinToString(", ") { it.spokenString }
        val dealerUp = c.dealerCards.firstOrNull()?.let(::describeCard) ?: "unknown"

        val actions = buildList {
            if (PlayerAction.Hit in c.available) add("hit")
            if (PlayerAction.Stand in c.available) add("stand")
            if (PlayerAction.Double in c.available) add("double down")
            if (PlayerAction.Split in c.available) add("split")
            if (c.canInsure) add("take insurance")
        }

        val winLines = buildList {
            add("Win probability if you hit: ${pct(c.winChance?.ifHit)}")
            add("Win probability if you stand: ${pct(c.winChance?.ifStand)}")
            if (PlayerAction.Double in c.available) add("Win probability if you double: ${pct(c.winChance?.ifDouble)}")
            if (PlayerAction.Split in c.available) add("Win probability per hand if you split: ${pct(c.winChance?.ifSplitHand)}")
        }

        val groundTruth: String = when {
            c.canInsure -> "Engine ground truth: DECLINE insurance. (8-deck shoe gives the dealer blackjack about 31 percent of the time on an Ace upcard; insurance pays 2 to 1, so the bet is minus 7 percent EV unless you are counting cards.)"
            c.playerCards.isNotEmpty() && c.dealerCards.isNotEmpty() -> {
                val move = BasicStrategy.recommend(
                    playerCards = c.playerCards,
                    dealerUpCard = c.dealerCards.first(),
                    canDouble = PlayerAction.Double in c.available,
                    canSplit = PlayerAction.Split in c.available,
                    canSurrender = false,
                )
                "Engine ground truth (8-deck H17 DAS no-surrender basic strategy): ${strategyName(move).uppercase()}. This is mathematically correct — recommend it and explain why."
            }
            else -> "Apply 8-deck H17 DAS no-surrender basic strategy."
        }

        val historyBlock = renderHistory(c.actionHistory)
        val historySection = if (historyBlock.isEmpty()) {
            "This is the opening decision — no prior actions on this hand."
        } else {
            "Decisions made so far this hand:\n$historyBlock"
        }

        return """
            You are a blackjack advisor speaking directly to the player. Your response will be read aloud by a text-to-speech service, so write exactly as you would speak — plain sentences, no bullet points, no numbered lists, no asterisks, no markdown, no special characters, no dollar signs (say "dollars" instead), no percent signs (say "percent" instead). Just natural, flowing speech.

            House rules: 8-deck shoe, dealer hits soft 17, double after split allowed, no surrender.

            Current hand:
            Player's cards: $handCards
            Player's total: ${c.handTotal}
            Dealer's up card (the only card visible to you): $dealerUp. The dealer's hole card is face down.
            Bet: ${c.bet} dollars
            Available actions: ${actions.joinToString(", ")}

            ${winLines.joinToString("\n")}

            $historySection

            $groundTruth

            In 2 to 3 spoken sentences, recommend the engine's action and explain the reasoning in plain language. Do not contradict the engine. Be direct and conversational. When the play is non-obvious — for example doubling a 9 against a dealer 2, splitting 9s against a 9, or hitting a 12 against a 2 or 3 — say plainly what makes it counter-intuitive and why the math favors it anyway.
        """.trimIndent()
    }

    // MARK: - Round-end recap prompt

    private fun buildSummary(c: AdvisorContext): String {
        val dealerUp = c.dealerCards.firstOrNull()?.let(::describeCard) ?: "unknown"
        val dealerHand = if (c.dealerCards.isEmpty()) "—" else c.dealerCards.joinToString(", ") { it.spokenString }
        val dealerTotal = HandEvaluator.evaluate(c.dealerCards).displayString()

        val resultLines = if (c.roundResults.isEmpty()) {
            "No outcome recorded."
        } else {
            c.roundResults.joinToString("\n") { r ->
                val direction = if (r.net >= 0) "won" else "lost"
                "${r.playerName} — hand total: ${r.handTotal}, outcome: ${r.outcomeLabel}, $direction ${kotlin.math.abs(r.net)} dollars"
            }
        }

        val historyBlock = renderHistory(c.actionHistory)
        val historySection = if (historyBlock.isEmpty()) {
            "No player decisions recorded."
        } else {
            "Decision-by-decision record (the recommendation column is the engine's basic-strategy ground truth at that exact moment):\n$historyBlock"
        }

        return """
            You are a blackjack advisor speaking directly to the player. Your response will be read aloud by a text-to-speech service, so write exactly as you would speak — plain sentences, no bullet points, no numbered lists, no asterisks, no markdown, no special characters, no dollar signs (say "dollars" instead), no percent signs (say "percent" instead). Just natural, flowing speech.

            House rules: 8-deck shoe, dealer hits soft 17, double after split allowed, no surrender.

            The hand just finished. Use only the facts below — do not invent cards or totals.

            Dealer's up card during play: $dealerUp
            Dealer's full final hand (after the hole card was revealed and any draws): $dealerHand
            Dealer's final total: $dealerTotal

            Player hand results:
            $resultLines

            $historySection

            Critical evaluation rules:
            - Judge each decision against the recommendation listed for THAT decision, not against the final outcome. Doubling on a 9 versus a 4 is correct even if the final hand reaches 17.
            - The dealer's up card during the player's decisions was $dealerUp. Never reference any other dealer rank when discussing the player's choices.
            - If every recommendation matched the action taken, say the player played it correctly.
            - If any action differed from the recommendation, name that specific deviation and explain the correct play.

            In 2 to 3 spoken sentences, recap what happened and evaluate the player's decisions using the rules above. Be direct and conversational.
        """.trimIndent()
    }

    // MARK: - Helpers

    private fun renderHistory(log: List<HandActionLog>): String =
        log.joinToString("\n") { entry ->
            if (entry.cardsBefore.isEmpty()) {
                val upText = entry.dealerUp?.let { " (dealer showing ${describeCard(it)})" }.orEmpty()
                "${entry.playerName}: ${entry.action} → total ${entry.handTotal}$upText"
            } else {
                val beforeCards = entry.cardsBefore.joinToString(", ") { it.spokenString }
                val upText = entry.dealerUp?.let(::describeCard) ?: "unknown"
                val recText = entry.recommended?.let { " — engine basic strategy said: ${it.uppercase()}" }.orEmpty()
                "${entry.playerName} held [$beforeCards] (${entry.totalBefore}) vs dealer up $upText → action: ${entry.action}$recText"
            }
        }

    private fun describeCard(card: Card): String {
        val value = when {
            card.isAce -> "value 11 or 1, an Ace"
            card.hasTenValue -> "value 10"
            else -> "value ${card.rank}"
        }
        return "${card.spokenString} ($value)"
    }

    private fun strategyName(m: StrategyMove): String = when (m) {
        StrategyMove.HIT -> "hit"
        StrategyMove.STAND -> "stand"
        StrategyMove.DOUBLE -> "double down"
        StrategyMove.SPLIT -> "split"
        StrategyMove.SURRENDER -> "surrender"
    }

    private fun pct(value: Double?): String =
        if (value == null) "unavailable" else "${kotlin.math.round(value).toInt()} percent"
}

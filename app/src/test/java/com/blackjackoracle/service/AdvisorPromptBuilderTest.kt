package com.blackjackoracle.service

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.Hand
import com.blackjackoracle.model.HandActionLog
import com.blackjackoracle.model.Player
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.RoundResult
import com.blackjackoracle.model.Suit
import com.blackjackoracle.model.WinChance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorPromptBuilderTest {

    @Test fun advicePromptIncludesGroundTruthAndContext() {
        val player = listOf(Card(Suit.SPADES, 4), Card(Suit.HEARTS, 5))   // hard 9
        val dealerUp = Card(Suit.CLUBS, 3)
        val state = GameState(
            human = Player(chips = 90, hands = listOf(Hand(cards = player, bet = 10))),
            dealerCards = listOf(dealerUp, Card(Suit.DIAMONDS, 10)),
            phase = GamePhase.PLAYER_TURNS,
            winChance = WinChance(ifHit = 55.0, ifStand = 25.0, ifDouble = 60.0),
        )
        val available = setOf(PlayerAction.Hit, PlayerAction.Stand, PlayerAction.Double, PlayerAction.Surrender)
        val ctx = AdvisorContext.from(state, available)

        val prompt = AdvisorPromptBuilder.build(ctx)

        // House rules header
        assertTrue("missing house rules", prompt.contains("8-deck shoe"))
        // Engine ground truth call-out is present
        assertTrue("missing ground truth", prompt.contains("Engine ground truth"))
        // For hard 9 vs 3 with canDouble=true, basic strategy says DOUBLE
        assertTrue("expected DOUBLE recommendation", prompt.contains("DOUBLE DOWN"))
        // Win-rate facts surface in percent form (no % sign for TTS)
        assertTrue("missing percent phrasing", prompt.contains("percent"))
        // Tricky-scenario nudge present (parity with iOS)
        assertTrue("missing tricky-scenario coaching", prompt.contains("non-obvious"))
    }

    @Test fun insurancePromptDeclinesByDefault() {
        val state = GameState(
            human = Player(chips = 90, hands = listOf(Hand(cards = listOf(Card(Suit.SPADES, 10), Card(Suit.HEARTS, 7)), bet = 10))),
            dealerCards = listOf(Card(Suit.CLUBS, 14)),
            phase = GamePhase.INSURANCE,
        )
        val ctx = AdvisorContext.from(state, available = emptySet())
        val prompt = AdvisorPromptBuilder.build(ctx)
        assertTrue("expected DECLINE insurance", prompt.contains("DECLINE insurance"))
    }

    @Test fun summaryPromptUsesDecisionByDecisionEvaluation() {
        val state = GameState(
            phase = GamePhase.ROUND_END,
            dealerCards = listOf(Card(Suit.CLUBS, 7), Card(Suit.HEARTS, 13)),
            roundResults = listOf(RoundResult("You", "Win", "20", 10)),
            actionHistory = listOf(
                HandActionLog(
                    playerName = "You",
                    action = "doubled to 20",
                    handTotal = "20",
                    cardsBefore = listOf(Card(Suit.SPADES, 5), Card(Suit.HEARTS, 5)),
                    totalBefore = "10",
                    dealerUp = Card(Suit.CLUBS, 7),
                    recommended = "double down",
                ),
            ),
        )
        val ctx = AdvisorContext.from(state, available = emptySet())
        val prompt = AdvisorPromptBuilder.build(ctx)
        assertTrue("expected decision-by-decision framing", prompt.contains("Decision-by-decision record"))
        assertTrue("expected per-decision recommendation surfaced", prompt.contains("engine basic strategy said: DOUBLE DOWN"))
    }

    @Test fun cacheKeyChangesAcrossPhasesAndCards() {
        val baseHand = Hand(cards = listOf(Card(Suit.SPADES, 4), Card(Suit.HEARTS, 5)), bet = 10)
        val s1 = GameState(human = Player(hands = listOf(baseHand)), phase = GamePhase.PLAYER_TURNS)
        val s2 = s1.copy(phase = GamePhase.ROUND_END, roundResults = listOf(RoundResult("You", "Win", "20", 10)))
        assertNotEquals(AdvisorPromptBuilder.cacheKey(s1), AdvisorPromptBuilder.cacheKey(s2))

        val s3 = s1.copy(
            human = s1.human.copy(
                hands = listOf(baseHand.copy(cards = baseHand.cards + Card(Suit.DIAMONDS, 2))),
            ),
        )
        assertNotEquals(AdvisorPromptBuilder.cacheKey(s1), AdvisorPromptBuilder.cacheKey(s3))
    }

    @Test fun availableActionsListReflectsContext() {
        val player = listOf(Card(Suit.SPADES, 8), Card(Suit.HEARTS, 8))
        val state = GameState(
            human = Player(chips = 80, hands = listOf(Hand(cards = player, bet = 10))),
            dealerCards = listOf(Card(Suit.CLUBS, 9)),
            phase = GamePhase.PLAYER_TURNS,
            winChance = WinChance(ifHit = 30.0, ifStand = 20.0, ifDouble = 25.0, ifSplitHand = 45.0),
        )
        val available = setOf(PlayerAction.Hit, PlayerAction.Stand, PlayerAction.Split, PlayerAction.Surrender)
        val ctx = AdvisorContext.from(state, available)
        val prompt = AdvisorPromptBuilder.build(ctx)
        assertTrue("expected hit listed", prompt.contains("hit"))
        assertTrue("expected split listed", prompt.contains("split"))
        // Surrender intentionally omitted from advisor "available actions" because
        // we advertise no-surrender house rules in the prompt header.
        val splitWinLine = prompt.lines().firstOrNull { it.contains("split:") || it.contains("split hand") }
        assertEquals(null, splitWinLine?.takeIf { it.contains("unavailable") })
    }
}

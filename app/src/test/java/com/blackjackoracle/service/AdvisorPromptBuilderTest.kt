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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt is now assembled server-side. These tests defend rule 4 of the
 * migration: the client serializes the EXACT displayed cards into the structured
 * `state` payload as {rank, suit} primitives — cards shown == cards sent == cards
 * reasoned about — and ships the engine outputs the user sees (win chances, the
 * basic-strategy move, hand totals, the dealer's final total) as values.
 */
class AdvisorServiceTest {

    @Test fun serializesCardsAndBasicStrategyMoveAsValues() {
        val player = listOf(Card(Suit.SPADES, 4), Card(Suit.HEARTS, 5)) // hard 9
        val state = GameState(
            human = Player(chips = 90, hands = listOf(Hand(cards = player, bet = 10))),
            dealerCards = listOf(Card(Suit.CLUBS, 3), Card(Suit.DIAMONDS, 10)),
            phase = GamePhase.PLAYER_TURNS,
            winChance = WinChance(ifHit = 55.0, ifStand = 25.0, ifDouble = 60.0),
        )
        val available = setOf(PlayerAction.Hit, PlayerAction.Stand, PlayerAction.Double, PlayerAction.Surrender)
        val s = AdvisorService.makeState(AdvisorContext.from(state, available))

        // cards shown == cards sent
        val p = s.getJSONArray("playerCards")
        assertEquals(4, p.getJSONObject(0).getInt("rank"))
        assertEquals("spades", p.getJSONObject(0).getString("suit"))
        assertEquals(5, p.getJSONObject(1).getInt("rank"))
        assertEquals("hearts", p.getJSONObject(1).getString("suit"))
        assertEquals("clubs", s.getJSONArray("dealerCards").getJSONObject(0).getString("suit"))

        // hard 9 vs 3 with canDouble -> DOUBLE; sent as a value, not derived server-side
        assertEquals("double", s.getString("basicStrategyMove"))

        // available actions -> can* booleans
        assertTrue(s.getBoolean("canDouble"))
        assertTrue(s.getBoolean("canSurrender"))
        assertFalse(s.getBoolean("canSplit"))

        // win chances as values, split omitted when unavailable
        val wc = s.getJSONObject("winChances")
        assertEquals(55.0, wc.getDouble("ifHit"), 0.0001)
        assertEquals(25.0, wc.getDouble("ifStand"), 0.0001)
        assertEquals(60.0, wc.getDouble("ifDouble"), 0.0001)
        assertFalse(wc.has("ifSplit"))
    }

    @Test fun insuranceCarriesFlagAndOmitsBasicStrategyMove() {
        val state = GameState(
            human = Player(chips = 90, hands = listOf(Hand(cards = listOf(Card(Suit.SPADES, 10), Card(Suit.HEARTS, 7)), bet = 10))),
            dealerCards = listOf(Card(Suit.CLUBS, 14)),
            phase = GamePhase.INSURANCE,
        )
        val s = AdvisorService.makeState(AdvisorContext.from(state, available = emptySet()))
        assertEquals("insurance", s.getString("phase"))
        assertTrue(s.getBoolean("canInsure"))
        assertFalse(s.has("basicStrategyMove"))
    }

    @Test fun roundEndSendsDealerTotalAndStructuredHistory() {
        val state = GameState(
            phase = GamePhase.ROUND_END,
            dealerCards = listOf(Card(Suit.CLUBS, 7), Card(Suit.HEARTS, 13)), // 17
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
        val s = AdvisorService.makeState(AdvisorContext.from(state, available = emptySet()))

        assertEquals("roundEnd", s.getString("phase"))
        assertEquals("17", s.getString("dealerTotal")) // client engine value, never re-derived

        val rr = s.getJSONArray("roundResults").getJSONObject(0)
        assertEquals("You", rr.getString("playerName"))
        assertEquals("Win", rr.getString("outcomeLabel"))
        assertEquals(10, rr.getInt("net"))

        val log = s.getJSONArray("actionHistory").getJSONObject(0)
        assertEquals("doubled to 20", log.getString("action"))
        assertEquals("double down", log.getString("recommended"))
        val before = log.getJSONArray("cardsBefore")
        assertEquals(5, before.getJSONObject(0).getInt("rank"))
        assertEquals(7, log.getJSONObject("dealerUp").getInt("rank"))
    }
}

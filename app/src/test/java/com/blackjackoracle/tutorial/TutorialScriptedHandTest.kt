package com.blackjackoracle.tutorial

import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.Shoe
import com.blackjackoracle.game.BlackjackTable
import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// The engine-level measurement that green-lit the tutorial's scripted hand,
/// kept as a permanent regression test. It plays the rigged shoe through the
/// real BlackjackTable — same draw order, same rules the ViewModel drives —
/// and asserts the teaching beats land exactly as the coach copy claims:
/// 18 v 6, split, 19 and 11, double to 21, dealer busts from 16, both hands
/// paid. It also pins the Win Chance percentages quoted in TutorialOverlay's
/// coach text (61 / 59 / 73 / 67), so an engine change breaks this test
/// before it silently falsifies the script.
class TutorialScriptedHandTest {

    @Test
    fun riggedShoeDealsTheScriptedSequenceThenFallsThrough() {
        val shoe = Shoe()
        shoe.forceNext(TutorialScript.riggedCards)
        val dealt = TutorialScript.riggedCards.indices.map { shoe.deal() }
        assertEquals(TutorialScript.riggedCards, dealt)
        // The shoe keeps dealing normally once the script is exhausted.
        assertTrue(shoe.deal().rank in 2..14)
    }

    @Test
    fun scriptedHandPlaysOutAsDesigned() {
        val table = BlackjackTable()
        table.startGame(tutorial = true)
        assertTrue(table.state.isTutorial)
        assertEquals(TutorialScript.BET, table.state.human.pendingBet)

        // The ViewModel drives this sequence on an animation-paced coroutine;
        // the table API is synchronous, so drive it directly here.
        table.beginHand()
        repeat(2) {
            table.dealInitialCardToHuman()
            table.dealInitialCardToDealer()
        }
        table.finishInitialDeal()

        // Beat 1: 9-9 (18) against a dealer 6, hole card ten.
        assertEquals(GamePhase.PLAYER_TURNS, table.state.phase)
        val hand0 = table.state.human.hands[0]
        assertEquals(18, HandEvaluator.evaluate(hand0.cards).total)
        assertEquals(6, table.state.dealerCards.first().blackjackValue)
        assertEquals(2, table.state.dealerCards.size)
        assertTrue(PlayerAction.Split in table.availableActions())

        // Real engine odds at 18 v 6, as quoted in the coach copy: standing is
        // the best-looking single-hand bar (61%), each split hand slightly
        // lower (59%) — the split's edge is the second bet, not the per-hand
        // percentage. If these drift, the tutorial's wording must change too.
        val wc0 = table.state.winChance
        assertNotNull(wc0)
        wc0!!
        assertTrue("stand should read ~61.2, was ${wc0.ifStand}", abs(wc0.ifStand - 61.2) < 1.0)
        val splitEq = wc0.ifSplitHand
        assertNotNull(splitEq)
        splitEq!!
        assertTrue("split hand should read ~59.4, was $splitEq", abs(splitEq - 59.4) < 1.0)
        // EV in units of the original bet: split must carry a real edge —
        // this is what makes the demo hand worth scripting.
        val standEV = 2 * wc0.ifStand / 100 - 1
        val splitEV = 2 * (2 * splitEq / 100 - 1)
        assertTrue("split edge should exceed 0.10 bets", splitEV - standEV > 0.10)

        // Beat 2: split. Both hands draw immediately: 19 and 11.
        table.handleAction(PlayerAction.Split)
        assertEquals(2, table.state.human.hands.size)
        assertEquals(19, HandEvaluator.evaluate(table.state.human.hands[0].cards).total)
        assertEquals(11, HandEvaluator.evaluate(table.state.human.hands[1].cards).total)
        assertEquals(0, table.state.human.activeHandIndex)

        // Beat 3: hand 1's Stand bar (73%) beats the original 18's (61%).
        val wc1 = table.state.winChance
        assertNotNull(wc1)
        wc1!!
        assertTrue("stand on 19 should read ~72.7, was ${wc1.ifStand}", abs(wc1.ifStand - 72.7) < 1.0)
        assertTrue(wc1.ifStand > wc0.ifStand)
        table.handleAction(PlayerAction.Stand)
        assertEquals(1, table.state.human.activeHandIndex)

        // Beat 4: hand 2 is 11 — double is available after the split (DAS)
        // and reads 67%.
        assertTrue(PlayerAction.Double in table.availableActions())
        val wc2 = table.state.winChance
        assertNotNull(wc2)
        wc2!!
        assertTrue("double on 11 should read ~66.6, was ${wc2.ifDouble}", abs(wc2.ifDouble - 66.6) < 1.0)
        table.handleAction(PlayerAction.Double)
        assertEquals(21, HandEvaluator.evaluate(table.state.human.hands[1].cards).total)
        assertTrue(table.state.human.hands[1].isDoubled)

        // Beat 5: dealer reveals 16, must draw, busts with 25. Both hands win;
        // the doubled hand pays double.
        assertEquals(GamePhase.DEALER_TURN, table.state.phase)
        while (table.dealerShouldDraw()) table.dealDealerCard()
        val dealer = HandEvaluator.evaluate(table.state.dealerCards)
        assertTrue(dealer.isBust)
        assertEquals(25, dealer.total)

        table.settleDealerTurn()
        val results = table.state.roundResults
        assertEquals(2, results.size)
        assertTrue(results.all { it.outcomeLabel == "Win" })
        val net = results.sumOf { it.net }
        assertEquals(TutorialScript.BET * 3, net) // +bet on the 19, +2×bet on the doubled 21
        assertEquals(GameConstants.STARTING_CHIPS + net, table.state.human.chips)

        table.completeRound()
        assertEquals(GamePhase.ROUND_END, table.state.phase)
        assertTrue(table.state.isTutorial)
    }
}

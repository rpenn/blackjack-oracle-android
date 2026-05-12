package com.blackjackoracle.game

import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import org.junit.Assert.*
import org.junit.Test

class BlackjackTableTest {
    @Test fun startGameCreatesSingleHumanAndBettingState() {
        val table = BlackjackTable()
        table.startGame()
        assertEquals(GamePhase.BETTING, table.state.phase)
        assertEquals(GameConstants.STARTING_CHIPS, table.state.human.chips)
        // First-time bet starts empty; subsequent rounds restore the last bet.
        assertEquals(0, table.state.human.pendingBet)
        assertTrue(table.state.human.isHuman)
    }

    @Test fun betClampsToBankrollAndDealDeductsBet() {
        val table = BlackjackTable()
        table.startGame()
        table.updatePendingBet(500)
        assertEquals(100, table.state.human.pendingBet)
        table.beginHand()
        assertEquals(GamePhase.DEALING, table.state.phase)
        assertEquals(0, table.state.human.chips)
        assertEquals(100, table.state.human.hands.first().bet)
    }

    @Test fun initialDealTransitionsOutOfAnimating() {
        val table = BlackjackTable()
        table.startGame()
        table.updatePendingBet(10)
        table.beginHand()
        repeat(2) { table.dealInitialCardToHuman(); table.dealInitialCardToDealer() }
        table.finishInitialDeal()
        assertFalse(table.state.isDealAnimating)
        // Possible post-deal phases:
        // - PLAYER_TURNS: standard flow
        // - INSURANCE: dealer shows Ace, player can afford insurance
        // - DEALER_TURN: dealer BJ peeked, or player has a natural BJ that
        //   auto-resolves; settlement is now driven asynchronously by the
        //   ViewModel, so the table parks here until `completeRound`.
        assertTrue(
            table.state.phase == GamePhase.PLAYER_TURNS ||
                table.state.phase == GamePhase.INSURANCE ||
                table.state.phase == GamePhase.DEALER_TURN,
        )
    }

    @Test fun completeRoundFlipsSettlementToRoundEndOrGameOver() {
        val table = BlackjackTable()
        table.startGame()
        table.updatePendingBet(10)
        table.beginHand()
        // Synthesize a finished settlement directly by playing a normal hand
        // through to completion. We can't deterministically trigger a loss, so
        // we just verify the SETTLEMENT → ROUND_END flip API.
        repeat(2) { table.dealInitialCardToHuman(); table.dealInitialCardToDealer() }
        table.finishInitialDeal()
        // Force-stand whatever hand we have to push through to dealer/settlement.
        if (table.state.phase == GamePhase.PLAYER_TURNS) {
            table.handleAction(com.blackjackoracle.model.PlayerAction.Stand)
        }
        // Drive the dealer to completion synchronously.
        while (table.dealerShouldDraw()) table.dealDealerCard()
        table.settleDealerTurn()
        assertTrue(table.state.phase == GamePhase.SETTLEMENT)
        table.completeRound()
        assertTrue(table.state.phase == GamePhase.ROUND_END || table.state.phase == GamePhase.GAME_OVER)
    }
}

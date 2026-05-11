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
        assertEquals(GameConstants.MIN_BET, table.state.human.pendingBet)
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
        assertTrue(table.state.phase == GamePhase.PLAYER_TURNS || table.state.phase == GamePhase.INSURANCE || table.state.phase == GamePhase.ROUND_END || table.state.phase == GamePhase.GAME_OVER)
    }
}

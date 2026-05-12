package com.blackjackoracle.game

import com.blackjackoracle.engine.Shoe
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.Suit
import org.junit.Assert.*
import org.junit.Test

class BlackjackTableTest {

    private fun card(suit: Suit, rank: Int) = Card(suit, rank)

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
            table.handleAction(PlayerAction.Stand)
        }
        // Drive the dealer to completion synchronously.
        while (table.dealerShouldDraw()) table.dealDealerCard()
        table.settleDealerTurn()
        assertTrue(table.state.phase == GamePhase.SETTLEMENT)
        table.completeRound()
        assertTrue(table.state.phase == GamePhase.ROUND_END || table.state.phase == GamePhase.GAME_OVER)
    }

    /// Regression coverage for the split-aces lock. Each split-Ace hand must
    /// receive exactly one card and immediately stand; the player must not be
    /// able to hit either hand. Uses a stacked shoe so the test is deterministic.
    @Test fun splitAcesEachHandReceivesExactlyOneCardAndStands() {
        val deal = listOf(
            // Deal order: player1, dealer-up, player2, dealer-hole, splitCard1, splitCard2, then dealer hits.
            card(Suit.SPADES, 14),   // player gets A
            card(Suit.HEARTS, 7),    // dealer up 7 (no insurance, no peek)
            card(Suit.CLUBS, 14),    // player gets A
            card(Suit.DIAMONDS, 4),  // dealer hole 4 — dealer total 11
            card(Suit.SPADES, 5),    // first split hand draws 5 — total 16, locked
            card(Suit.HEARTS, 6),    // second split hand draws 6 — total 17, locked
            card(Suit.CLUBS, 9),     // dealer draws 9 → 20, stands
        )
        val table = BlackjackTable(Shoe(stackedCards = deal))
        table.startGame()
        table.updatePendingBet(10)
        table.beginHand()
        repeat(2) { table.dealInitialCardToHuman(); table.dealInitialCardToDealer() }
        table.finishInitialDeal()

        assertEquals("dealer 7 + 4 must not peek to insurance", GamePhase.PLAYER_TURNS, table.state.phase)
        assertTrue("split must be available on A,A", PlayerAction.Split in table.availableActions())

        table.handleAction(PlayerAction.Split)

        val hands = table.state.human.hands
        assertEquals("split must produce two hands", 2, hands.size)
        assertTrue("both hands flagged isSplitAces", hands.all { it.isSplitAces })
        assertTrue("both hands forced to standing", hands.all { it.isStanding })
        assertTrue("each split hand holds exactly 2 cards (locked)", hands.all { it.cards.size == 2 })
        // No more player decisions — the table has already advanced past PLAYER_TURNS.
        assertTrue(
            "phase must have left PLAYER_TURNS after a split-aces lock",
            table.state.phase == GamePhase.DEALER_TURN ||
                table.state.phase == GamePhase.SETTLEMENT ||
                table.state.phase == GamePhase.ROUND_END,
        )
        assertTrue(
            "no further actions should be available after split-aces lock",
            table.availableActions().isEmpty(),
        )
    }

    /// Dealer Ace must always park at INSURANCE — even when the player is
    /// all-in and can't afford the side bet. The ViewModel layer is
    /// responsible for auto-declining after 1s; the engine just guarantees
    /// the dialog gets shown.
    @Test fun insurancePhaseEnteredEvenWhenPlayerCannotAfford() {
        val deal = listOf(
            card(Suit.SPADES, 5),     // player gets 5
            card(Suit.HEARTS, 14),    // dealer up A
            card(Suit.CLUBS, 6),      // player gets 6 → 11
            card(Suit.DIAMONDS, 6),   // dealer hole 6 → no BJ
        )
        val table = BlackjackTable(Shoe(stackedCards = deal))
        table.startGame()
        // Bet the full bankroll so chips = 0 and the player can't cover the
        // half-bet insurance side action.
        table.updatePendingBet(GameConstants.STARTING_CHIPS)
        table.beginHand()
        assertEquals(0, table.state.human.chips)
        repeat(2) { table.dealInitialCardToHuman(); table.dealInitialCardToDealer() }
        table.finishInitialDeal()
        assertEquals(GamePhase.INSURANCE, table.state.phase)
        // Declining still routes through the normal post-peek path.
        table.handleInsurance(take = false)
        assertEquals(GamePhase.PLAYER_TURNS, table.state.phase)
    }

    /// Regression coverage for insurance push: player BJ + dealer BJ, with
    /// insurance taken. Main bet pushes (returned); insurance pays 2:1.
    @Test fun insurancePushPaysInsuranceAndReturnsMainBet() {
        val deal = listOf(
            card(Suit.SPADES, 14),    // player gets A
            card(Suit.HEARTS, 14),    // dealer up A — insurance offered
            card(Suit.CLUBS, 13),     // player gets K → player BJ
            card(Suit.DIAMONDS, 12),  // dealer hole Q → dealer BJ → push
        )
        val table = BlackjackTable(Shoe(stackedCards = deal))
        table.startGame()
        val startingChips = table.state.human.chips
        val mainBet = 20
        table.updatePendingBet(mainBet)
        table.beginHand()
        repeat(2) { table.dealInitialCardToHuman(); table.dealInitialCardToDealer() }
        table.finishInitialDeal()
        assertEquals(GamePhase.INSURANCE, table.state.phase)

        table.handleInsurance(take = true)
        // revealDealerAndSettle parks at DEALER_TURN; the dealer doesn't need
        // to draw because it has BJ.
        assertEquals(GamePhase.DEALER_TURN, table.state.phase)
        assertFalse(table.dealerShouldDraw())
        table.settleDealerTurn()
        assertEquals(GamePhase.SETTLEMENT, table.state.phase)

        val results = table.state.roundResults
        val mainResult = results.firstOrNull { it.outcomeLabel == "Push" }
        val insuranceResult = results.firstOrNull { it.outcomeLabel == "Insurance won" }
        assertNotNull("main hand must record a Push", mainResult)
        assertNotNull("insurance side must record a win", insuranceResult)
        assertEquals("main hand net is zero on a push", 0, mainResult!!.net)

        // Net for the round: main pushes (zero) and insurance pays 2:1, so the
        // player ends up exactly +2 × insuranceBet over the starting bankroll.
        val insuranceBet = mainBet / 2
        assertEquals(
            "chips should be starting + 2 × insuranceBet (2:1 on insurance, push on main)",
            startingChips + insuranceBet * 2,
            table.state.human.chips,
        )
        assertEquals("insurance net is +2 × insuranceBet", insuranceBet * 2, insuranceResult!!.net)
    }
}

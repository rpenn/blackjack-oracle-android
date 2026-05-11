package com.blackjackoracle.game

import com.blackjackoracle.engine.BasicStrategy
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.Shoe
import com.blackjackoracle.engine.WinChanceCalculator
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.Hand
import com.blackjackoracle.model.HandActionLog
import com.blackjackoracle.model.LastAction
import com.blackjackoracle.model.Player
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.RoundResult

class BlackjackTable(private val shoe: Shoe = Shoe(GameConstants.DECK_COUNT, GameConstants.RESHUFFLE_AT_CARDS_REMAINING)) {
    var state: GameState = GameState()
        private set
    private var lastBet = GameConstants.MIN_BET

    fun startGame() {
        if (shoe.needsReshuffle) shoe.reshuffle()
        state = GameState(human = Player(pendingBet = GameConstants.MIN_BET), phase = GamePhase.BETTING, currentRound = 1)
    }

    fun returnToSetup() { state = GameState() }

    fun updatePendingBet(amount: Int) {
        val clamped = amount.coerceIn(0, state.human.chips)
        state = state.copy(human = state.human.copy(pendingBet = clamped))
    }

    fun beginHand() {
        val bet = state.human.pendingBet
        require(state.phase == GamePhase.BETTING && bet > 0 && bet <= state.human.chips)
        lastBet = bet
        if (shoe.needsReshuffle) shoe.reshuffle()
        state = state.copy(
            human = state.human.copy(chips = state.human.chips - bet, pendingBet = 0, hands = listOf(Hand(bet = bet)), activeHandIndex = 0, insuranceBet = 0),
            dealerCards = emptyList(), dealerHoleRevealed = false, phase = GamePhase.DEALING, isDealAnimating = true,
            winChance = null, roundResults = emptyList(), lastAction = null, actionHistory = emptyList()
        )
    }

    fun dealInitialCardToHuman() {
        val activeHand = state.human.activeHand ?: return
        replaceActiveHand(activeHand.copy(cards = activeHand.cards + shoe.deal()))
    }
    fun dealInitialCardToDealer() { state = state.copy(dealerCards = state.dealerCards + shoe.deal()) }

    fun finishInitialDeal() { state = state.copy(isDealAnimating = false); postDealResolution() }

    private fun postDealResolution() {
        val up = state.dealerCards.firstOrNull() ?: return
        val hole = state.dealerCards.getOrNull(1) ?: return
        val mainBet = state.human.hands.firstOrNull()?.bet ?: 0
        if (up.isAce && state.human.chips >= maxOf(1, mainBet / 2)) { state = state.copy(phase = GamePhase.INSURANCE); return }
        if (up.hasTenValue && hole.isAce) { revealDealerAndSettle("Dealer blackjack"); return }
        beginPlayerTurns()
    }

    fun handleInsurance(take: Boolean) {
        if (state.phase != GamePhase.INSURANCE) return
        if (take) {
            val mainBet = state.human.hands.firstOrNull()?.bet ?: 0
            val amount = maxOf(1, mainBet / 2).coerceAtMost(state.human.chips)
            state = state.copy(human = state.human.copy(chips = state.human.chips - amount, insuranceBet = amount))
        }
        val dealerBj = state.dealerCards.size >= 2 && state.dealerCards[0].isAce && state.dealerCards[1].hasTenValue
        if (dealerBj) revealDealerAndSettle("Dealer blackjack") else beginPlayerTurns()
    }

    private fun beginPlayerTurns() {
        val h = state.human.activeHand
        if (h != null && HandEvaluator.evaluate(h.cards).isBlackjack) {
            replaceActiveHand(h.copy(isStanding = true)); advanceAfterAction(); return
        }
        state = state.copy(phase = GamePhase.PLAYER_TURNS)
        recomputeWinChance()
    }

    fun handleAction(action: PlayerAction) {
        if (state.phase != GamePhase.PLAYER_TURNS || !availableActions().contains(action)) return
        val before = state.human.activeHand ?: return
        val up = state.dealerCards.firstOrNull()
        val totalBefore = HandEvaluator.evaluate(before.cards).displayString()
        val recommended = if (up != null) BasicStrategy.recommend(before.cards, up, PlayerAction.Double in availableActions(), PlayerAction.Split in availableActions(), PlayerAction.Surrender in availableActions()).name.lowercase() else null
        var human = state.human
        var hand = before
        var actionText = ""
        when (action) {
            PlayerAction.Hit -> { hand = hand.copy(cards = hand.cards + shoe.deal()); val ev = HandEvaluator.evaluate(hand.cards); if (ev.isBust) hand = hand.copy(isStanding = true); actionText = "hit (${ev.displayString()})" }
            PlayerAction.Stand -> { hand = hand.copy(isStanding = true); actionText = "stood at ${HandEvaluator.evaluate(hand.cards).displayString()}" }
            PlayerAction.Double -> { val extra = minOf(human.chips, hand.bet); human = human.copy(chips = human.chips - extra); hand = hand.copy(bet = hand.bet + extra, cards = hand.cards + shoe.deal(), isDoubled = true, isStanding = true); actionText = "doubled to ${HandEvaluator.evaluate(hand.cards).displayString()}" }
            PlayerAction.Split -> {
                val splittingAces = hand.cards[0].isAce
                human = human.copy(chips = human.chips - hand.bet)
                val first = Hand(cards = listOf(hand.cards[0], shoe.deal()), bet = hand.bet, fromSplit = true, isSplitAces = splittingAces, isStanding = splittingAces)
                val second = Hand(cards = listOf(hand.cards[1], shoe.deal()), bet = hand.bet, fromSplit = true, isSplitAces = splittingAces, isStanding = splittingAces)
                val hands = human.hands.toMutableList(); hands[human.activeHandIndex] = first; hands.add(human.activeHandIndex + 1, second)
                human = human.copy(hands = hands); state = state.copy(human = human, lastAction = LastAction(human.name, "split"), actionHistory = state.actionHistory + HandActionLog(human.name, "split", "—", before.cards, totalBefore, up, recommended)); advanceAfterAction(); return
            }
            PlayerAction.Surrender -> { val refund = (hand.bet + 1) / 2; human = human.copy(chips = human.chips + refund); hand = hand.copy(isSurrendered = true, isStanding = true); actionText = "surrendered" }
        }
        val hands = human.hands.toMutableList(); hands[human.activeHandIndex] = hand; human = human.copy(hands = hands)
        state = state.copy(human = human, lastAction = LastAction(human.name, actionText), actionHistory = state.actionHistory + HandActionLog(human.name, actionText, HandEvaluator.evaluate(hand.cards).displayString(), before.cards, totalBefore, up, recommended))
        advanceAfterAction()
    }

    private fun advanceAfterAction() {
        var human = state.human
        while (true) {
            val hand = human.activeHand ?: break
            val ev = HandEvaluator.evaluate(hand.cards)
            val terminal = hand.isStanding || hand.isSurrendered || ev.isBust || (hand.isSplitAces && hand.cards.size >= 2)
            if (!terminal) break
            if (human.activeHandIndex + 1 < human.hands.size) human = human.copy(activeHandIndex = human.activeHandIndex + 1) else { state = state.copy(human = human); advanceToDealer(); return }
        }
        state = state.copy(human = human)
        recomputeWinChance()
    }

    private fun advanceToDealer() {
        val anyLive = state.human.hands.any { val ev = HandEvaluator.evaluate(it.cards); !it.isSurrendered && !ev.isBust && !(ev.isBlackjack && !it.fromSplit) }
        if (!anyLive) { revealDealerAndSettle("All hands resolved"); return }
        state = state.copy(phase = GamePhase.DEALER_TURN, dealerHoleRevealed = true, winChance = null)
    }

    fun dealerShouldDraw(): Boolean { val ev = HandEvaluator.evaluate(state.dealerCards); return !ev.isBust && !(ev.total >= 18 || (ev.total == 17 && !ev.isSoft)) }
    fun dealDealerCard() { state = state.copy(dealerCards = state.dealerCards + shoe.deal()) }
    fun settleDealerTurn() { settle() }
    private fun revealDealerAndSettle(reason: String) { state = state.copy(phase = GamePhase.DEALER_TURN, dealerHoleRevealed = true, lastAction = LastAction("Dealer", reason), winChance = null); settle() }

    private fun settle() {
        val dealer = HandEvaluator.evaluate(state.dealerCards)
        var chips = state.human.chips
        val results = mutableListOf<RoundResult>()
        val hands = state.human.hands.map { hand ->
            val ev = HandEvaluator.evaluate(hand.cards)
            val (label, payout) = resolve(hand, ev, dealer)
            chips += payout
            val resultPayout = if (hand.isSurrendered) (hand.bet + 1) / 2 else payout
            results += RoundResult(state.human.name, label, ev.displayString(), resultPayout - hand.bet)
            hand.copy(outcome = label, payout = resultPayout)
        }
        if (state.human.insuranceBet > 0) {
            val won = dealer.isBlackjack
            val payout = if (won) state.human.insuranceBet * 3 else 0
            chips += payout
            results += RoundResult(state.human.name, if (won) "Insurance won" else "Insurance lost", "—", payout - state.human.insuranceBet)
        }
        val busted = chips <= 0
        state = state.copy(human = state.human.copy(chips = chips, hands = hands, insuranceBet = 0, isBusted = busted), roundResults = results, phase = if (busted) GamePhase.GAME_OVER else GamePhase.ROUND_END, handsPlayed = state.handsPlayed + 1, winChance = null)
    }

    private fun resolve(hand: Hand, player: com.blackjackoracle.engine.HandValue, dealer: com.blackjackoracle.engine.HandValue): Pair<String, Int> {
        if (hand.isSurrendered) return "Surrender" to 0
        if (player.isBust) return "Bust" to 0
        val playerBj = player.isBlackjack && !hand.fromSplit
        if (playerBj && !dealer.isBlackjack) { val win = (hand.bet * GameConstants.BLACKJACK_PAYOUT_NUM + GameConstants.BLACKJACK_PAYOUT_DEN - 1) / GameConstants.BLACKJACK_PAYOUT_DEN; return "Blackjack" to hand.bet + win }
        if (playerBj && dealer.isBlackjack) return "Push" to hand.bet
        if (dealer.isBlackjack) return "Loss" to 0
        if (dealer.isBust || player.total > dealer.total) return "Win" to hand.bet * 2
        if (player.total == dealer.total) return "Push" to hand.bet
        return "Loss" to 0
    }

    fun startNextHand() {
        if (state.human.chips <= 0) { state = state.copy(phase = GamePhase.GAME_OVER); return }
        val restored = lastBet.coerceAtMost(state.human.chips)
        state = state.copy(human = state.human.copy(hands = emptyList(), activeHandIndex = 0, insuranceBet = 0, pendingBet = restored), dealerCards = emptyList(), dealerHoleRevealed = false, phase = GamePhase.BETTING, currentRound = state.currentRound + 1, winChance = null, roundResults = emptyList(), lastAction = null, actionHistory = emptyList())
    }

    fun availableActions(): Set<PlayerAction> {
        if (state.phase != GamePhase.PLAYER_TURNS || state.isDealAnimating) return emptySet()
        val hand = state.human.activeHand ?: return emptySet()
        if (hand.isStanding) return emptySet()
        val actions = mutableSetOf(PlayerAction.Hit, PlayerAction.Stand)
        if (hand.cards.size == 2 && !hand.isSplitAces && state.human.chips > 0) actions += PlayerAction.Double
        if (hand.cards.size == 2 && hand.cards[0].rank == hand.cards[1].rank && state.human.hands.size < GameConstants.MAX_HANDS_PER_PLAYER && state.human.chips >= hand.bet) {
            if (!(hand.cards[0].isAce && state.human.hands.any { it.isSplitAces })) actions += PlayerAction.Split
        }
        if (hand.cards.size == 2 && !hand.fromSplit) actions += PlayerAction.Surrender
        return actions
    }

    private fun replaceActiveHand(hand: Hand) { val hands = state.human.hands.toMutableList(); hands[state.human.activeHandIndex] = hand; state = state.copy(human = state.human.copy(hands = hands)) }
    private fun recomputeWinChance() { val up = state.dealerCards.firstOrNull(); val hand = state.human.activeHand; state = if (up == null || hand == null || state.phase != GamePhase.PLAYER_TURNS) state.copy(winChance = null) else state.copy(winChance = WinChanceCalculator.compute(hand.cards, up, PlayerAction.Split in availableActions())) }
}

package com.blackjackoracle.game

import com.blackjackoracle.engine.BasicStrategy
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.HandValue
import com.blackjackoracle.engine.Shoe
import com.blackjackoracle.engine.StrategyMove
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

class BlackjackTable(
    private val shoe: Shoe = Shoe(
        deckCount = GameConstants.DECK_COUNT,
        reshuffleAtCardsRemaining = GameConstants.RESHUFFLE_AT_CARDS_REMAINING,
    ),
) {
    var state: GameState = GameState()
        private set

    private var lastBet: Int = GameConstants.MIN_BET

    fun startGame() {
        if (shoe.needsReshuffle) shoe.reshuffle()
        lastBet = GameConstants.MIN_BET
        // First-time bet starts empty; subsequent rounds restore `lastBet` in
        // `startNextHand`. Mirrors iOS, where `lastHumanBet = 0` on `startGame`.
        state = GameState(
            human = Player(pendingBet = 0),
            phase = GamePhase.BETTING,
            currentRound = 1,
        )
    }

    fun returnToSetup() {
        state = GameState()
    }

    fun updatePendingBet(amount: Int) {
        val clamped = amount.coerceIn(0, state.human.chips)
        state = state.copy(human = state.human.copy(pendingBet = clamped))
    }

    fun beginHand() {
        val bet = state.human.pendingBet
        require(state.phase == GamePhase.BETTING && bet > 0 && bet <= state.human.chips) {
            "beginHand called with invalid bet=$bet in phase=${state.phase}"
        }
        lastBet = bet
        if (shoe.needsReshuffle) shoe.reshuffle()
        state = state.copy(
            human = state.human.copy(
                chips = state.human.chips - bet,
                pendingBet = 0,
                hands = listOf(Hand(bet = bet)),
                activeHandIndex = 0,
                insuranceBet = 0,
            ),
            dealerCards = emptyList(),
            dealerHoleRevealed = false,
            phase = GamePhase.DEALING,
            isDealAnimating = true,
            winChance = null,
            roundResults = emptyList(),
            lastAction = null,
            actionHistory = emptyList(),
        )
    }

    fun dealInitialCardToHuman() {
        val active = state.human.activeHand ?: return
        replaceActiveHand(active.copy(cards = active.cards + shoe.deal()))
    }

    fun dealInitialCardToDealer() {
        state = state.copy(dealerCards = state.dealerCards + shoe.deal())
    }

    fun finishInitialDeal() {
        state = state.copy(isDealAnimating = false)
        logInitialDeal()
        postDealResolution()
    }

    /// Mirrors iOS GameViewModel.logInitialDeals — gives the advisor a faithful
    /// record of what the player was actually dealt before any action was taken.
    private fun logInitialDeal() {
        val hand = state.human.hands.firstOrNull() ?: return
        if (hand.cards.size < 2) return
        val total = HandEvaluator.evaluate(hand.cards).displayString()
        val spoken = hand.cards.joinToString(" and ") { it.spokenString }
        state = state.copy(
            actionHistory = state.actionHistory + HandActionLog(
                playerName = state.human.name,
                action = "dealt $spoken",
                handTotal = total,
                cardsBefore = emptyList(),
                totalBefore = "—",
                dealerUp = state.dealerCards.firstOrNull(),
                recommended = null,
            ),
        )
    }

    private fun postDealResolution() {
        val up = state.dealerCards.firstOrNull() ?: return
        val hole = state.dealerCards.getOrNull(1) ?: return
        val mainBet = state.human.hands.firstOrNull()?.bet ?: 0
        val canAffordInsurance = state.human.chips >= maxOf(1, mainBet / 2)
        if (up.isAce) {
            if (canAffordInsurance) {
                state = state.copy(phase = GamePhase.INSURANCE)
                return
            }
            // Cannot afford insurance, but still peek for dealer blackjack.
            if (hole.hasTenValue) {
                revealDealerAndSettle("Dealer blackjack")
                return
            }
        }
        if (up.hasTenValue && hole.isAce) {
            revealDealerAndSettle("Dealer blackjack")
            return
        }
        beginPlayerTurns()
    }

    fun handleInsurance(take: Boolean) {
        if (state.phase != GamePhase.INSURANCE) return
        if (take) {
            val mainBet = state.human.hands.firstOrNull()?.bet ?: 0
            val amount = maxOf(1, mainBet / 2).coerceAtMost(state.human.chips)
            state = state.copy(
                human = state.human.copy(
                    chips = state.human.chips - amount,
                    insuranceBet = amount,
                ),
            )
        }
        val up = state.dealerCards.firstOrNull()
        val hole = state.dealerCards.getOrNull(1)
        if (up?.isAce == true && hole?.hasTenValue == true) {
            revealDealerAndSettle("Dealer blackjack")
        } else {
            beginPlayerTurns()
        }
    }

    private fun beginPlayerTurns() {
        val hand = state.human.activeHand
        if (hand != null && HandEvaluator.evaluate(hand.cards).isBlackjack) {
            replaceActiveHand(hand.copy(isStanding = true))
            advanceAfterAction()
            return
        }
        state = state.copy(phase = GamePhase.PLAYER_TURNS)
        recomputeWinChance()
    }

    fun handleAction(action: PlayerAction) {
        if (state.phase != GamePhase.PLAYER_TURNS) return
        val available = availableActions()
        if (action !in available) return

        val before = state.human.activeHand ?: return
        val up = state.dealerCards.firstOrNull()
        val totalBefore = HandEvaluator.evaluate(before.cards).displayString()
        val recommended = engineRecommendation(before, available, up)

        var human = state.human
        var hand = before
        var actionText = ""

        when (action) {
            PlayerAction.Hit -> {
                hand = hand.copy(cards = hand.cards + shoe.deal())
                val ev = HandEvaluator.evaluate(hand.cards)
                if (ev.isBust) hand = hand.copy(isStanding = true)
                actionText = "hit (${ev.displayString()})"
            }
            PlayerAction.Stand -> {
                hand = hand.copy(isStanding = true)
                actionText = "stood at ${HandEvaluator.evaluate(hand.cards).displayString()}"
            }
            PlayerAction.Double -> {
                val extra = minOf(human.chips, hand.bet)
                human = human.copy(chips = human.chips - extra)
                hand = hand.copy(
                    bet = hand.bet + extra,
                    cards = hand.cards + shoe.deal(),
                    isDoubled = true,
                    isStanding = true,
                )
                actionText = "doubled to ${HandEvaluator.evaluate(hand.cards).displayString()}"
            }
            PlayerAction.Split -> {
                val splittingAces = hand.cards[0].isAce
                human = human.copy(chips = human.chips - hand.bet)
                val first = Hand(
                    cards = listOf(hand.cards[0], shoe.deal()),
                    bet = hand.bet,
                    fromSplit = true,
                    isSplitAces = splittingAces,
                    isStanding = splittingAces,
                )
                val second = Hand(
                    cards = listOf(hand.cards[1], shoe.deal()),
                    bet = hand.bet,
                    fromSplit = true,
                    isSplitAces = splittingAces,
                    isStanding = splittingAces,
                )
                val hands = human.hands.toMutableList()
                hands[human.activeHandIndex] = first
                hands.add(human.activeHandIndex + 1, second)
                human = human.copy(hands = hands)
                val label = if (splittingAces) "split Aces" else "split"
                state = state.copy(
                    human = human,
                    lastAction = LastAction(human.name, label),
                    actionHistory = state.actionHistory + HandActionLog(
                        playerName = human.name,
                        action = label,
                        handTotal = "—",
                        cardsBefore = before.cards,
                        totalBefore = totalBefore,
                        dealerUp = up,
                        recommended = recommended,
                    ),
                )
                advanceAfterAction()
                return
            }
            PlayerAction.Surrender -> {
                val refund = (hand.bet + 1) / 2
                human = human.copy(chips = human.chips + refund)
                hand = hand.copy(isSurrendered = true, isStanding = true)
                actionText = "surrendered"
            }
        }

        val hands = human.hands.toMutableList()
        hands[human.activeHandIndex] = hand
        human = human.copy(hands = hands)
        state = state.copy(
            human = human,
            lastAction = LastAction(human.name, actionText),
            actionHistory = state.actionHistory + HandActionLog(
                playerName = human.name,
                action = actionText,
                handTotal = HandEvaluator.evaluate(hand.cards).displayString(),
                cardsBefore = before.cards,
                totalBefore = totalBefore,
                dealerUp = up,
                recommended = recommended,
            ),
        )
        advanceAfterAction()
    }

    /// Pinned to canSurrender=false so the recorded recommendation reflects the
    /// no-surrender house rules announced in the advisor prompt.
    private fun engineRecommendation(
        hand: Hand,
        available: Set<PlayerAction>,
        up: Card?,
    ): String? {
        if (up == null || hand.cards.isEmpty()) return null
        val move = BasicStrategy.recommend(
            playerCards = hand.cards,
            dealerUpCard = up,
            canDouble = PlayerAction.Double in available,
            canSplit = PlayerAction.Split in available,
            canSurrender = false,
        )
        return strategyName(move)
    }

    private fun strategyName(move: StrategyMove): String = when (move) {
        StrategyMove.HIT -> "hit"
        StrategyMove.STAND -> "stand"
        StrategyMove.DOUBLE -> "double down"
        StrategyMove.SPLIT -> "split"
        StrategyMove.SURRENDER -> "surrender"
    }

    private fun advanceAfterAction() {
        var human = state.human
        while (true) {
            val hand = human.activeHand ?: break
            val ev = HandEvaluator.evaluate(hand.cards)
            val terminal = hand.isStanding ||
                hand.isSurrendered ||
                ev.isBust ||
                (hand.isSplitAces && hand.cards.size >= 2)
            if (!terminal) break
            if (human.activeHandIndex + 1 < human.hands.size) {
                human = human.copy(activeHandIndex = human.activeHandIndex + 1)
            } else {
                state = state.copy(human = human)
                advanceToDealer()
                return
            }
        }
        state = state.copy(human = human)
        recomputeWinChance()
    }

    private fun advanceToDealer() {
        val anyLive = state.human.hands.any { h ->
            val ev = HandEvaluator.evaluate(h.cards)
            val isNaturalBj = ev.isBlackjack && !h.fromSplit
            !h.isSurrendered && !ev.isBust && !isNaturalBj
        }
        if (!anyLive) {
            revealDealerAndSettle("All hands resolved")
            return
        }
        state = state.copy(
            phase = GamePhase.DEALER_TURN,
            dealerHoleRevealed = true,
            winChance = null,
        )
    }

    fun dealerShouldDraw(): Boolean {
        // If no player hand can still beat the dealer — every hand is busted,
        // surrendered, or a natural blackjack — drawing accomplishes nothing.
        // Stop after the hole-card reveal so the dealer doesn't keep dealing
        // into a settled hand (e.g. player has BJ, dealer shows 6 + 10).
        val anyLive = state.human.hands.any { h ->
            val ev = HandEvaluator.evaluate(h.cards)
            val isNaturalBj = ev.isBlackjack && !h.fromSplit
            !h.isSurrendered && !ev.isBust && !isNaturalBj
        }
        if (!anyLive) return false

        val ev = HandEvaluator.evaluate(state.dealerCards)
        if (ev.isBust) return false
        val standsHere = ev.total >= 18 || (ev.total == 17 && !ev.isSoft)
        return !standsHere
    }

    fun dealDealerCard() {
        state = state.copy(dealerCards = state.dealerCards + shoe.deal())
    }

    fun settleDealerTurn() {
        settle()
    }

    /// Two-phase transition out of dealer turn — `settle` parks at SETTLEMENT
    /// so the table can briefly show outcome badges on the hands before the
    /// ViewModel calls `completeRound` to flip to ROUND_END / GAME_OVER.
    fun completeRound() {
        val next = if (state.human.chips <= 0) GamePhase.GAME_OVER else GamePhase.ROUND_END
        state = state.copy(phase = next)
    }

    private fun revealDealerAndSettle(reason: String) {
        // Parks at DEALER_TURN with the hole card flipped. The ViewModel drives
        // the settlement and round-end delays from here.
        state = state.copy(
            phase = GamePhase.DEALER_TURN,
            dealerHoleRevealed = true,
            lastAction = LastAction("Dealer", reason),
            winChance = null,
        )
    }

    private fun settle() {
        val dealer = HandEvaluator.evaluate(state.dealerCards)
        var chips = state.human.chips
        val results = mutableListOf<RoundResult>()
        val hands = state.human.hands.map { hand ->
            val ev = HandEvaluator.evaluate(hand.cards)
            val (label, payout) = resolve(hand, ev, dealer)
            chips += payout
            val resultPayout = if (hand.isSurrendered) (hand.bet + 1) / 2 else payout
            results += RoundResult(
                playerName = state.human.name,
                outcomeLabel = label,
                handTotal = ev.displayString(),
                net = resultPayout - hand.bet,
            )
            hand.copy(outcome = label, payout = resultPayout)
        }
        if (state.human.insuranceBet > 0) {
            val won = dealer.isBlackjack
            val payout = if (won) state.human.insuranceBet * 3 else 0
            chips += payout
            results += RoundResult(
                playerName = state.human.name,
                outcomeLabel = if (won) "Insurance won" else "Insurance lost",
                handTotal = "—",
                net = payout - state.human.insuranceBet,
            )
        }
        val busted = chips <= 0
        state = state.copy(
            human = state.human.copy(
                chips = chips,
                hands = hands,
                insuranceBet = 0,
                isBusted = busted,
            ),
            roundResults = results,
            phase = GamePhase.SETTLEMENT,
            handsPlayed = state.handsPlayed + 1,
            winChance = null,
        )
    }

    private fun resolve(hand: Hand, player: HandValue, dealer: HandValue): Pair<String, Int> {
        if (hand.isSurrendered) return "Surrender" to 0
        if (player.isBust) return "Bust" to 0
        val playerBj = player.isBlackjack && !hand.fromSplit
        if (playerBj && !dealer.isBlackjack) {
            val win = (hand.bet * GameConstants.BLACKJACK_PAYOUT_NUM + GameConstants.BLACKJACK_PAYOUT_DEN - 1) /
                GameConstants.BLACKJACK_PAYOUT_DEN
            return "Blackjack" to hand.bet + win
        }
        if (playerBj && dealer.isBlackjack) return "Push" to hand.bet
        if (dealer.isBlackjack) return "Loss" to 0
        if (dealer.isBust || player.total > dealer.total) return "Win" to hand.bet * 2
        if (player.total == dealer.total) return "Push" to hand.bet
        return "Loss" to 0
    }

    fun startNextHand() {
        if (state.human.chips <= 0) {
            state = state.copy(phase = GamePhase.GAME_OVER)
            return
        }
        if (shoe.needsReshuffle) shoe.reshuffle()
        val restored = lastBet.coerceAtMost(state.human.chips)
        state = state.copy(
            human = state.human.copy(
                hands = emptyList(),
                activeHandIndex = 0,
                insuranceBet = 0,
                pendingBet = restored,
            ),
            dealerCards = emptyList(),
            dealerHoleRevealed = false,
            phase = GamePhase.BETTING,
            currentRound = state.currentRound + 1,
            winChance = null,
            roundResults = emptyList(),
            lastAction = null,
            actionHistory = emptyList(),
        )
    }

    fun availableActions(): Set<PlayerAction> {
        if (state.phase != GamePhase.PLAYER_TURNS || state.isDealAnimating) return emptySet()
        val hand = state.human.activeHand ?: return emptySet()
        if (hand.isStanding) return emptySet()

        val actions = mutableSetOf<PlayerAction>(PlayerAction.Hit, PlayerAction.Stand)
        val twoCards = hand.cards.size == 2

        // Require enough chips to match the bet — no "double for less". This
        // makes the button hide entirely after a split has drained the bankroll
        // below the bet, instead of showing a button that ostensibly works but
        // only commits a partial second bet.
        if (twoCards && !hand.isSplitAces && state.human.chips >= hand.bet) {
            actions += PlayerAction.Double
        }
        if (twoCards &&
            hand.cards[0].rank == hand.cards[1].rank &&
            state.human.hands.size < GameConstants.MAX_HANDS_PER_PLAYER &&
            state.human.chips >= hand.bet
        ) {
            val splittingAces = hand.cards[0].isAce
            val acesAlreadySplit = state.human.hands.any { it.isSplitAces }
            if (!(splittingAces && acesAlreadySplit)) actions += PlayerAction.Split
        }
        if (twoCards && !hand.fromSplit) {
            actions += PlayerAction.Surrender
        }
        return actions
    }

    private fun replaceActiveHand(hand: Hand) {
        val hands = state.human.hands.toMutableList()
        hands[state.human.activeHandIndex] = hand
        state = state.copy(human = state.human.copy(hands = hands))
    }

    private fun recomputeWinChance() {
        val up = state.dealerCards.firstOrNull()
        val hand = state.human.activeHand
        state = if (up == null || hand == null || state.phase != GamePhase.PLAYER_TURNS) {
            state.copy(winChance = null)
        } else {
            state.copy(
                winChance = WinChanceCalculator.compute(
                    playerCards = hand.cards,
                    dealerUp = up,
                    canSplit = PlayerAction.Split in availableActions(),
                ),
            )
        }
    }
}

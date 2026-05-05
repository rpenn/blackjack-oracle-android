package com.blackjackoracle.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackjackoracle.engine.AIPlayer
import com.blackjackoracle.engine.BasicStrategy
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.Shoe
import com.blackjackoracle.engine.StrategyMove
import com.blackjackoracle.engine.WinChance
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GameConfig
import com.blackjackoracle.model.GameConstants
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.Hand
import com.blackjackoracle.model.HandActionLog
import com.blackjackoracle.model.LastAction
import com.blackjackoracle.model.Player
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.RoundResult
import com.blackjackoracle.service.SoundManager
import kotlin.random.Random
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Timing {
    const val DEAL_PER_CARD_MS = 220L
    const val DEAL_TAIL_MS = 300L
    const val AI_THINK_MS = 750L
    const val DEALER_DRAW_MS = 750L
    const val SETTLEMENT_MS = 600L
    const val ROUND_END_DELAY_MS = 700L
}

/**
 * Blackjack game state machine.
 *
 * Phase flow:
 *   SETUP → BETTING → DEALING → (INSURANCE?) → PLAYER_TURNS → DEALER_TURN
 *     → SETTLEMENT → ROUND_END → BETTING (next round) | GAME_OVER
 *
 * The human plays at index 0; AI players are indices 1..N. AI players play
 * one hand per round (no splits/doubles/insurance) — they exist for
 * atmosphere and to make pacing feel like a real table.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private var _sound: SoundManager? = null
    internal var sound: SoundManager
        get() = _sound ?: SoundManager(getApplication<Application>().applicationContext).also { _sound = it }
        internal set(value) { _sound = value }

    var state: GameState by mutableStateOf(
        GameState(config = GameConfig(aiPlayerCount = 2), players = persistentListOf())
    )
        private set

    private var shoe: Shoe = Shoe()
    private var aiJob: Job? = null
    private var dealerJob: Job? = null
    private var dealJob: Job? = null

    // -------- Setup --------

    fun startGame(config: GameConfig) {
        cancelAllJobs()
        shoe = Shoe(GameConstants.DECK_COUNT, GameConstants.RESHUFFLE_AT_CARDS_REMAINING)
        val players = createPlayers(config.aiPlayerCount)
        state = GameState(
            config = config,
            players = players,
            phase = GamePhase.BETTING,
            currentRound = 1
        )
        // Pre-set the human pendingBet to the previous bet or min — UI binds to this.
        updateHumanPendingBet(GameConstants.MIN_BET)
        autoSetAIBets()
    }

    fun returnToSetup() {
        cancelAllJobs()
        state = GameState(config = state.config, players = persistentListOf())
    }

    private fun createPlayers(aiCount: Int): ImmutableList<Player> {
        val list = mutableListOf<Player>()
        list.add(
            Player(
                id = GameConstants.HUMAN_PLAYER_ID,
                name = "You",
                chips = GameConstants.STARTING_CHIPS,
                isHuman = true
            )
        )
        for (i in 0 until aiCount) {
            list.add(
                Player(
                    id = "ai-$i",
                    name = GameConstants.AI_NAMES[i % GameConstants.AI_NAMES.size],
                    chips = GameConstants.STARTING_CHIPS,
                    isHuman = false,
                    aiSkillLevel = Random.nextInt(1, 6)
                )
            )
        }
        return list.toPersistentList()
    }

    // -------- Betting --------

    /** Called by the UI as the user adjusts the chip slider/buttons. */
    fun updateHumanPendingBet(amount: Int) {
        val human = state.players.firstOrNull { it.isHuman } ?: return
        val clamped = amount.coerceIn(0, human.chips.coerceAtLeast(0))
        val players = state.players.toMutableList()
        val idx = players.indexOfFirst { it.isHuman }
        players[idx] = players[idx].copy(pendingBet = clamped)
        state = state.copy(players = players.toPersistentList())
    }

    private fun autoSetAIBets() {
        val players = state.players.toMutableList()
        for (i in players.indices) {
            val p = players[i]
            if (p.isHuman || p.isBusted) continue
            players[i] = p.copy(pendingBet = AIPlayer.decideBet(p.aiSkillLevel, p.chips))
        }
        state = state.copy(players = players.toPersistentList())
    }

    /** Lock in bets and begin the deal. */
    fun confirmBetsAndDeal() {
        if (state.phase != GamePhase.BETTING) return
        val human = state.players.firstOrNull { it.isHuman } ?: return
        if (human.pendingBet <= 0 || human.pendingBet > human.chips) return
        startHand()
    }

    // -------- Hand lifecycle --------

    private fun startHand() {
        if (shoe.needsReshuffle) shoe.reshuffle()

        // Reset all players to fresh hands. Skip busted players (they sit out).
        val players = state.players.map { p ->
            if (p.isBusted) p
            else p.copy(
                hands = persistentListOf(),
                activeHandIndex = 0,
                insuranceBet = 0
            )
        }.toMutableList()

        // Deal the initial 2 cards to each non-busted player and the dealer.
        // Casino order: one card to each player+dealer, then second card.
        val seats = players.indices.filter { !players[it].isBusted }
        val dealerCards = mutableListOf<Card>()
        repeat(2) { pass ->
            for (idx in seats) {
                val p = players[idx]
                val hand = p.hands.firstOrNull() ?: Hand(bet = p.pendingBet)
                val updatedHand = hand.copy(cards = (hand.cards + shoe.deal()).toPersistentList())
                val newHands = if (p.hands.isEmpty()) persistentListOf(updatedHand)
                else (listOf(updatedHand) + p.hands.drop(1)).toPersistentList()
                players[idx] = p.copy(hands = newHands)
            }
            dealerCards.add(shoe.deal())
        }

        // Deduct each player's bet from chips immediately (the chips are at risk).
        for (i in players.indices) {
            val p = players[i]
            if (p.isBusted) continue
            val bet = p.hands.firstOrNull()?.bet ?: 0
            players[i] = p.copy(chips = p.chips - bet, pendingBet = 0)
        }

        state = state.copy(
            players = players.toPersistentList(),
            dealerCards = dealerCards.toPersistentList(),
            dealerHoleRevealed = false,
            phase = GamePhase.DEALING,
            currentPlayerIndex = 0,
            roundResults = persistentListOf(),
            actionHistory = persistentListOf(),
            lastAction = null,
            isDealAnimating = true,
            winChance = null
        )
        sound.playInitialDeal(seats.size + 1)

        // Brief animation pause then resolve naturals / insurance / first action.
        dealJob = viewModelScope.launch {
            delay(seats.size * 2 * Timing.DEAL_PER_CARD_MS + Timing.DEAL_TAIL_MS)
            state = state.copy(isDealAnimating = false)
            postDealResolution()
        }
    }

    /**
     * After the deal animates: if dealer up-card is an Ace, prompt insurance.
     * Otherwise either short-circuit naturals (dealer 10 + check for BJ) or go
     * straight into player action.
     */
    private fun postDealResolution() {
        val up = state.dealerCards.firstOrNull() ?: return
        val hole = state.dealerCards.getOrNull(1) ?: return

        // Mark instant naturals (player blackjack stays in hands list — settlement reads it)
        // We don't auto-stand the human's blackjack here; we let it resolve normally.

        if (up.isAce) {
            // Offer insurance to human (AIs always decline)
            state = state.copy(phase = GamePhase.INSURANCE)
            return
        }
        if (up.isTen && hole.isAce) {
            // Dealer has blackjack — instant reveal + settle
            revealDealerAndSettle(reason = "Dealer blackjack")
            return
        }
        beginPlayerTurns()
    }

    /**
     * Insurance choice from the human (after dealer shows Ace). Bet = half the
     * main hand bet. Pays 2:1 if dealer has blackjack.
     */
    fun handleInsurance(take: Boolean) {
        if (state.phase != GamePhase.INSURANCE) return
        val players = state.players.toMutableList()
        val humanIdx = players.indexOfFirst { it.isHuman }
        if (humanIdx >= 0 && take) {
            val human = players[humanIdx]
            val mainBet = human.hands.firstOrNull()?.bet ?: 0
            val insurance = (mainBet / 2).coerceAtLeast(1).coerceAtMost(human.chips)
            if (insurance > 0) {
                players[humanIdx] = human.copy(
                    chips = human.chips - insurance,
                    insuranceBet = insurance
                )
                sound.playChips()
            }
        }
        state = state.copy(players = players.toPersistentList())
        // Now check if dealer has blackjack
        val up = state.dealerCards.firstOrNull()
        val hole = state.dealerCards.getOrNull(1)
        if (up != null && hole != null && up.isAce && hole.isTen) {
            revealDealerAndSettle(reason = "Dealer blackjack")
        } else {
            beginPlayerTurns()
        }
    }

    // -------- Player turns --------

    private fun beginPlayerTurns() {
        // Find first player with a playable hand.
        val firstIdx = state.players.indexOfFirst { !it.isBusted }
        state = state.copy(
            phase = GamePhase.PLAYER_TURNS,
            currentPlayerIndex = if (firstIdx >= 0) firstIdx else 0
        )
        // If the player has a natural blackjack, auto-stand and move on.
        autoStandNaturals()
        recomputeWinChance()
        scheduleAITurnIfNeeded()
    }

    /** Mark any 2-card 21 as standing (it's a natural — no further actions). */
    private fun autoStandNaturals() {
        val players = state.players.toMutableList()
        var changed = false
        for (i in players.indices) {
            val p = players[i]
            if (p.isBusted) continue
            val newHands = p.hands.map { h ->
                val ev = HandEvaluator.evaluate(h.cards)
                if (ev.isBlackjack && !h.isStanding) {
                    changed = true
                    h.copy(isStanding = true)
                } else h
            }.toPersistentList()
            players[i] = p.copy(hands = newHands)
        }
        if (changed) state = state.copy(players = players.toPersistentList())
        // If the active player's active hand is now standing, advance.
        advancePastFinishedHands()
    }

    /** Move past any active hand that is already terminal (stand/bust/surrender). */
    private fun advancePastFinishedHands() {
        // Don't run this outside player turns — a stale AI job firing during the
        // dealer turn would otherwise spawn a second concurrent dealer-draw loop.
        if (state.phase != GamePhase.PLAYER_TURNS) return
        var safety = 32
        while (safety-- > 0) {
            val players = state.players
            val pIdx = state.currentPlayerIndex
            if (pIdx !in players.indices) {
                advanceToDealer()
                return
            }
            val p = players[pIdx]
            if (p.isBusted) {
                moveToNextPlayer()
                continue
            }
            val hand = p.activeHand
            if (hand == null) {
                moveToNextPlayer()
                continue
            }
            val ev = HandEvaluator.evaluate(hand.cards)
            val terminal = hand.isStanding || hand.isSurrendered || ev.isBust ||
                hand.isSplitAces && hand.cards.size >= 2
            if (terminal) {
                if (p.activeHandIndex + 1 < p.hands.size) {
                    val newPlayers = players.toMutableList()
                    newPlayers[pIdx] = p.copy(activeHandIndex = p.activeHandIndex + 1)
                    state = state.copy(players = newPlayers.toPersistentList())
                } else {
                    moveToNextPlayer()
                }
                continue
            }
            return  // not terminal, stop here
        }
    }

    private fun moveToNextPlayer() {
        val nextIdx = (state.currentPlayerIndex + 1).let {
            if (it < state.players.size) it else -1
        }
        if (nextIdx < 0) {
            advanceToDealer()
            return
        }
        // Reset active hand index for the next player
        val players = state.players.toMutableList()
        if (players[nextIdx].hands.isNotEmpty()) {
            players[nextIdx] = players[nextIdx].copy(activeHandIndex = 0)
        }
        state = state.copy(
            players = players.toPersistentList(),
            currentPlayerIndex = nextIdx
        )
    }

    fun handlePlayerAction(action: PlayerAction) {
        if (state.phase != GamePhase.PLAYER_TURNS) return
        val pIdx = state.currentPlayerIndex
        if (pIdx !in state.players.indices) return
        val player = state.players[pIdx]
        if (!player.isHuman) return
        applyAction(action, pIdx)
        afterAction()
    }

    private fun applyAction(action: PlayerAction, playerIdx: Int) {
        val players = state.players.toMutableList()
        var p = players[playerIdx]
        val handIdx = p.activeHandIndex
        var hand = p.hands.getOrNull(handIdx) ?: return
        var desc = ""

        when (action) {
            is PlayerAction.Hit -> {
                hand = hand.copy(cards = (hand.cards + shoe.deal()).toPersistentList())
                val ev = HandEvaluator.evaluate(hand.cards)
                desc = "hit (${ev.displayString()})"
                sound.playHit()
                if (ev.isBust) hand = hand.copy(isStanding = true)  // mark terminal
            }
            is PlayerAction.Stand -> {
                hand = hand.copy(isStanding = true)
                val ev = HandEvaluator.evaluate(hand.cards)
                desc = "stood at ${ev.displayString()}"
                sound.playStand()
            }
            is PlayerAction.Double -> {
                if (p.chips < hand.bet) return  // shouldn't happen — UI gates this
                p = p.copy(chips = p.chips - hand.bet)
                hand = hand.copy(
                    bet = hand.bet * 2,
                    cards = (hand.cards + shoe.deal()).toPersistentList(),
                    isDoubled = true,
                    isStanding = true   // exactly one card on a double
                )
                val ev = HandEvaluator.evaluate(hand.cards)
                desc = "doubled to ${ev.displayString()}"
                sound.playChips()
            }
            is PlayerAction.Split -> {
                if (hand.cards.size != 2 || hand.cards[0].rank != hand.cards[1].rank) return
                if (p.hands.size >= GameConstants.MAX_HANDS_PER_PLAYER) return
                if (p.chips < hand.bet) return
                p = p.copy(chips = p.chips - hand.bet)
                val splittingAces = hand.cards[0].isAce
                val first = Hand(
                    cards = persistentListOf(hand.cards[0], shoe.deal()),
                    bet = hand.bet,
                    fromSplit = true,
                    isSplitAces = splittingAces,
                    isStanding = splittingAces   // aces split → no further action
                )
                val second = Hand(
                    cards = persistentListOf(hand.cards[1], shoe.deal()),
                    bet = hand.bet,
                    fromSplit = true,
                    isSplitAces = splittingAces,
                    isStanding = splittingAces
                )
                val newHands = p.hands.toMutableList()
                newHands[handIdx] = first
                newHands.add(handIdx + 1, second)
                p = p.copy(hands = newHands.toPersistentList())
                desc = if (splittingAces) "split Aces" else "split"
                sound.playChips()
                // Don't write `hand` back below — hands array already updated.
                players[playerIdx] = p
                state = state.copy(
                    players = players.toPersistentList(),
                    lastAction = LastAction(p.name, desc),
                    actionHistory = (state.actionHistory + HandActionLog(p.name, desc, "—")).toPersistentList()
                )
                return
            }
            is PlayerAction.Surrender -> {
                // Refund half the bet (we already deducted the full bet at deal-time)
                val refund = hand.bet / 2
                p = p.copy(chips = p.chips + refund)
                hand = hand.copy(isSurrendered = true, isStanding = true)
                desc = "surrendered"
                sound.playStand()
            }
        }

        // Write the updated hand back
        val newHands = p.hands.toMutableList()
        newHands[handIdx] = hand
        p = p.copy(hands = newHands.toPersistentList())
        players[playerIdx] = p

        val handTotal = HandEvaluator.evaluate(hand.cards).displayString()
        state = state.copy(
            players = players.toPersistentList(),
            lastAction = LastAction(p.name, desc),
            actionHistory = (state.actionHistory + HandActionLog(p.name, desc, handTotal)).toPersistentList()
        )
    }

    private fun afterAction() {
        advancePastFinishedHands()
        if (state.phase != GamePhase.PLAYER_TURNS) return  // moved to dealer
        recomputeWinChance()
        scheduleAITurnIfNeeded()
    }

    private fun scheduleAITurnIfNeeded() {
        aiJob?.cancel()
        val pIdx = state.currentPlayerIndex
        if (pIdx !in state.players.indices) return
        val p = state.players[pIdx]
        if (p.isHuman) return
        if (p.hands.isEmpty() || p.activeHand?.isStanding == true) {
            // Shouldn't be at a finished hand — advancePastFinishedHands should have moved us
            return
        }
        aiJob = viewModelScope.launch {
            delay(Timing.AI_THINK_MS)
            runAITurn()
        }
    }

    private fun runAITurn() {
        if (state.phase != GamePhase.PLAYER_TURNS) return   // stale job guard
        val pIdx = state.currentPlayerIndex
        if (pIdx !in state.players.indices) return
        val p = state.players[pIdx]
        if (p.isHuman) return
        val hand = p.activeHand ?: return
        if (hand.isStanding) return
        val up = state.dealerCards.firstOrNull() ?: return
        val action = AIPlayer.decideAction(hand.cards, up, p.aiSkillLevel)
        applyAction(action, pIdx)
        afterAction()
    }

    // -------- Dealer + settlement --------

    private fun advanceToDealer() {
        // Kill any pending AI move so it can't fire a second dealer loop after we start.
        aiJob?.cancel(); aiJob = null
        // Cancel any orphaned prior dealer job before creating a new one.
        dealerJob?.cancel(); dealerJob = null

        // If no live hands remain (everyone busted/surrendered), skip dealer draw and settle.
        val anyLive = state.players.any { p ->
            !p.isBusted && p.hands.any { h ->
                val ev = HandEvaluator.evaluate(h.cards)
                !h.isSurrendered && !ev.isBust
            }
        }
        if (!anyLive) {
            revealDealerAndSettle(reason = "All hands resolved")
            return
        }
        state = state.copy(
            phase = GamePhase.DEALER_TURN,
            dealerHoleRevealed = true
        )
        // Dealer draws one card at a time, with a short delay for animation/SFX.
        dealerJob = viewModelScope.launch {
            while (true) {
                val ev = HandEvaluator.evaluate(state.dealerCards)
                val standsHere = ev.total >= 18 || (ev.total == 17 && !ev.isSoft) || ev.isBust
                if (standsHere) break
                delay(Timing.DEALER_DRAW_MS)
                val next = shoe.deal()
                state = state.copy(
                    dealerCards = (state.dealerCards + next).toPersistentList()
                )
                sound.playDeal()
            }
            delay(Timing.SETTLEMENT_MS)
            settle()
        }
    }

    /** Reveal dealer's hole and immediately settle (used for natural blackjacks). */
    private fun revealDealerAndSettle(reason: String) {
        state = state.copy(
            phase = GamePhase.DEALER_TURN,
            dealerHoleRevealed = true,
            lastAction = LastAction("Dealer", reason)
        )
        dealerJob = viewModelScope.launch {
            delay(Timing.SETTLEMENT_MS)
            settle()
        }
    }

    private fun settle() {
        val dealerEval = HandEvaluator.evaluate(state.dealerCards)
        val players = state.players.toMutableList()
        val results = mutableListOf<RoundResult>()
        var humanWonAny = false
        var humanLostAny = false

        for (pi in players.indices) {
            val p = players[pi]
            if (p.isBusted) continue
            val updatedHands = mutableListOf<Hand>()
            var chipsDelta = 0

            for (h in p.hands) {
                val ev = HandEvaluator.evaluate(h.cards)
                val (label, payout) = resolveHandOutcome(h, ev, dealerEval)
                chipsDelta += payout
                updatedHands.add(h.copy(outcome = label, payout = payout))
                results.add(
                    RoundResult(
                        playerName = p.name,
                        outcomeLabel = label,
                        handTotal = ev.displayString(),
                        net = payout - h.bet  // signed net (payout includes returned bet)
                    )
                )
            }

            // Insurance side bet (only for human; AIs never insure)
            if (p.insuranceBet > 0) {
                val insurancePayout = if (dealerEval.isBlackjack) p.insuranceBet * 3 else 0
                chipsDelta += insurancePayout
                if (insurancePayout > 0) {
                    results.add(
                        RoundResult(
                            playerName = p.name,
                            outcomeLabel = "Insurance won",
                            handTotal = "—",
                            net = insurancePayout - p.insuranceBet
                        )
                    )
                } else {
                    results.add(
                        RoundResult(
                            playerName = p.name,
                            outcomeLabel = "Insurance lost",
                            handTotal = "—",
                            net = -p.insuranceBet
                        )
                    )
                }
            }

            players[pi] = p.copy(
                chips = p.chips + chipsDelta,
                hands = updatedHands.toPersistentList(),
                insuranceBet = 0
            )
            if (p.isHuman) {
                if (results.takeLast(updatedHands.size).any { it.net > 0 }) humanWonAny = true
                if (results.takeLast(updatedHands.size).any { it.net < 0 }) humanLostAny = true
            }
        }

        // Mark busted (chips ≤ 0)
        for (i in players.indices) {
            if (players[i].chips <= 0) players[i] = players[i].copy(isBusted = true)
        }

        if (humanWonAny) sound.playWin()
        else if (humanLostAny) sound.playLose()

        state = state.copy(
            players = players.toPersistentList(),
            roundResults = results.toPersistentList(),
            phase = GamePhase.SETTLEMENT,
            handsPlayed = state.handsPlayed + 1
        )

        viewModelScope.launch {
            delay(Timing.ROUND_END_DELAY_MS)
            // Game over check
            val human = state.players.firstOrNull { it.isHuman }
            if (human != null && human.chips <= 0 && human.hands.all {
                    HandEvaluator.evaluate(it.cards).isBust || it.isSurrendered ||
                        (it.outcome == "Loss")
                }) {
                state = state.copy(phase = GamePhase.GAME_OVER)
            } else {
                state = state.copy(phase = GamePhase.ROUND_END)
            }
        }
    }

    /**
     * Returns (outcomeLabel, totalChipsBackToPlayer) for one hand.
     * Payout includes the original bet returned for wins/pushes; loss = 0.
     */
    private fun resolveHandOutcome(
        hand: Hand,
        playerEval: com.blackjackoracle.engine.HandValue,
        dealerEval: com.blackjackoracle.engine.HandValue
    ): Pair<String, Int> {
        if (hand.isSurrendered) return "Surrender" to 0  // half the bet was already refunded
        if (playerEval.isBust) return "Bust" to 0
        // Player blackjack — only counts as natural if NOT from a split.
        val playerBJ = playerEval.isBlackjack && !hand.fromSplit
        val dealerBJ = dealerEval.isBlackjack
        if (playerBJ && !dealerBJ) {
            // 3:2 payout. Total back = bet + bet*3/2.
            val winnings = hand.bet * GameConstants.BLACKJACK_PAYOUT_NUM /
                GameConstants.BLACKJACK_PAYOUT_DEN
            return "Blackjack" to (hand.bet + winnings)
        }
        if (playerBJ && dealerBJ) return "Push" to hand.bet
        if (dealerBJ) return "Loss" to 0
        if (dealerEval.isBust) return "Win" to (hand.bet * 2)
        return when {
            playerEval.total > dealerEval.total -> "Win" to (hand.bet * 2)
            playerEval.total == dealerEval.total -> "Push" to hand.bet
            else -> "Loss" to 0
        }
    }

    /** Advance to next round's BETTING phase, called by the UI's "Next Hand" button. */
    fun startNextHand() {
        cancelAllJobs()
        if (shoe.needsReshuffle) shoe.reshuffle()
        // Clear hands on each player; preserve chips and busted flag.
        val players = state.players.map { p ->
            p.copy(hands = persistentListOf(), activeHandIndex = 0, insuranceBet = 0, pendingBet = 0)
        }.toPersistentList()
        state = state.copy(
            players = players,
            dealerCards = persistentListOf(),
            dealerHoleRevealed = false,
            phase = GamePhase.BETTING,
            currentPlayerIndex = 0,
            currentRound = state.currentRound + 1,
            roundResults = persistentListOf(),
            actionHistory = persistentListOf(),
            lastAction = null,
            isDealAnimating = false,
            winChance = null
        )
        val human = state.players.firstOrNull { it.isHuman }
        if (human != null && human.chips <= 0) {
            state = state.copy(phase = GamePhase.GAME_OVER)
            return
        }
        updateHumanPendingBet(GameConstants.MIN_BET)
        autoSetAIBets()
    }

    // -------- Win-chance --------

    private fun recomputeWinChance() {
        val pIdx = state.currentPlayerIndex
        val p = state.players.getOrNull(pIdx)
        val up = state.dealerCards.firstOrNull()
        if (p == null || !p.isHuman || up == null) {
            state = state.copy(winChance = null)
            return
        }
        val hand = p.activeHand ?: run {
            state = state.copy(winChance = null)
            return
        }

        // Use standard compute - it now handles double/split internally
        val wc = WinChance.compute(hand.cards, up)

        // Filter out double/split if they aren't actually available to the player
        val avail = availableActions()
        val filteredWc = wc.copy(
            ifDouble = if (PlayerAction.Double in avail) wc.ifDouble else null,
            ifSplit = if (PlayerAction.Split in avail) wc.ifSplit else null
        )

        state = state.copy(winChance = filteredWc)
    }


    // -------- UI-facing helpers --------

    val humanPlayer: Player? get() = state.players.firstOrNull { it.isHuman }

    val isHumanTurn: Boolean
        get() {
            if (state.phase != GamePhase.PLAYER_TURNS) return false
            if (state.isDealAnimating) return false
            val pIdx = state.currentPlayerIndex
            val p = state.players.getOrNull(pIdx) ?: return false
            return p.isHuman && p.activeHand != null && !p.activeHand!!.isStanding
        }

    /** What actions the human can take on their currently active hand. */
    fun availableActions(): Set<PlayerAction> {
        if (!isHumanTurn) return emptySet()
        val human = humanPlayer ?: return emptySet()
        val hand = human.activeHand ?: return emptySet()
        val actions = mutableSetOf<PlayerAction>()
        actions.add(PlayerAction.Hit)
        actions.add(PlayerAction.Stand)
        if (hand.cards.size == 2 && !hand.isSplitAces && human.chips >= hand.bet) {
            actions.add(PlayerAction.Double)
        }
        if (hand.cards.size == 2 &&
            hand.cards[0].rank == hand.cards[1].rank &&
            human.hands.size < GameConstants.MAX_HANDS_PER_PLAYER &&
            human.chips >= hand.bet
        ) {
            // Ace split is allowed; further re-splitting of Aces is restricted —
            // we honor "split Aces only once" by checking that no existing hand is a split-Ace.
            val acesAlreadySplit = human.hands.any { it.isSplitAces }
            val splittingAces = hand.cards[0].isAce
            if (!(splittingAces && acesAlreadySplit)) actions.add(PlayerAction.Split)
        }
        if (hand.cards.size == 2 && !hand.fromSplit) {
            actions.add(PlayerAction.Surrender)
        }
        return actions
    }

    val basicStrategyHint: StrategyMove?
        get() {
            if (!isHumanTurn) return null
            val human = humanPlayer ?: return null
            val hand = human.activeHand ?: return null
            val up = state.dealerCards.firstOrNull() ?: return null
            val avail = availableActions()
            return BasicStrategy.recommend(
                playerCards = hand.cards,
                dealerUpCard = up,
                canDouble = PlayerAction.Double in avail,
                canSplit = PlayerAction.Split in avail,
                canSurrender = PlayerAction.Surrender in avail
            )
        }

    private fun cancelAllJobs() {
        aiJob?.cancel(); aiJob = null
        dealerJob?.cancel(); dealerJob = null
        dealJob?.cancel(); dealJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllJobs()
        _sound?.release()
    }
}

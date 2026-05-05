package com.blackjackoracle.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed class PlayerAction {
    data object Hit : PlayerAction()
    data object Stand : PlayerAction()
    data object Double : PlayerAction()
    data object Split : PlayerAction()
    data object Surrender : PlayerAction()
}

@Immutable
data class Player(
    val id: String,
    val name: String,
    val chips: Int,
    val isHuman: Boolean,
    val isBusted: Boolean = false,
    /**
     * Hands held by the player this round. One after deal, more after splits.
     * Empty between rounds.
     */
    val hands: ImmutableList<Hand> = persistentListOf(),
    /**
     * Index of the active hand (0..hands.size-1) while the player is acting.
     * Meaningless once all hands resolved.
     */
    val activeHandIndex: Int = 0,
    /** Insurance side bet (half the original bet) — paid out 2:1 if dealer has BJ. */
    val insuranceBet: Int = 0,
    /** Bet placed for the upcoming round (BETTING phase). */
    val pendingBet: Int = 0,
    val aiSkillLevel: Int = 3
) {
    val activeHand: Hand? get() = hands.getOrNull(activeHandIndex)
}

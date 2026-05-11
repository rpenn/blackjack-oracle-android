package com.blackjackoracle.model

import androidx.compose.runtime.Immutable

sealed class PlayerAction {
    data object Hit : PlayerAction()
    data object Stand : PlayerAction()
    data object Double : PlayerAction()
    data object Split : PlayerAction()
    data object Surrender : PlayerAction()
}

@Immutable
data class Player(
    val id: String = GameConstants.HUMAN_PLAYER_ID,
    val name: String = "You",
    val chips: Int = GameConstants.STARTING_CHIPS,
    val isHuman: Boolean = true,
    val isBusted: Boolean = false,
    val hands: List<Hand> = emptyList(),
    val activeHandIndex: Int = 0,
    val insuranceBet: Int = 0,
    val pendingBet: Int = 0
) {
    val activeHand: Hand? get() = hands.getOrNull(activeHandIndex)
}

package com.blackjackoracle.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

/**
 * A single blackjack hand belonging to a player. A player normally has one,
 * but can hold up to four after splits. Each hand has its own bet and
 * resolution state.
 *
 * Insurance is tracked on the player, not on individual hands.
 */
@Immutable
data class Hand(
    val id: String = UUID.randomUUID().toString(),
    val cards: ImmutableList<Card> = persistentListOf(),
    val bet: Int,
    val isStanding: Boolean = false,
    val isDoubled: Boolean = false,
    val isSurrendered: Boolean = false,
    /** True if this hand was created by splitting; affects whether it can be a "natural" blackjack. */
    val fromSplit: Boolean = false,
    /** Aces split → only one extra card per hand, no further actions. */
    val isSplitAces: Boolean = false,
    /** Outcome label set during settlement: "Win", "Loss", "Push", "Blackjack", "Bust", "Surrender". */
    val outcome: String? = null,
    val payout: Int = 0
) {
    val cardCount: Int get() = cards.size
}

enum class HandOutcome { WIN, LOSS, PUSH, BUST, BLACKJACK, SURRENDER }

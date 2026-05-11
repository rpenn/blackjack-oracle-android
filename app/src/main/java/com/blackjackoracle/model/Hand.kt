package com.blackjackoracle.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Hand(
    val id: String = UUID.randomUUID().toString(),
    val cards: List<Card> = emptyList(),
    val bet: Int,
    val isStanding: Boolean = false,
    val isDoubled: Boolean = false,
    val isSurrendered: Boolean = false,
    val fromSplit: Boolean = false,
    val isSplitAces: Boolean = false,
    val outcome: String? = null,
    val payout: Int = 0
)

package com.blackjackoracle.viewmodel

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.PlayerAction

/**
 * Production-absent puppet seams for the store-capture demo. A debug-only driver
 * (see `src/debug/.../demo/DemoEntry.kt`) populates [GameViewModel.demo] to stack
 * the shoe with scripted hands and answer advisor requests offline. In release
 * builds the driver is a no-op twin, [GameViewModel.demo] is always null, and
 * every call site falls back to normal play — none of this changes shipped
 * behaviour.
 */
class DemoSeams(
    /** Stacked shoe contents: the scripted hands' cards on top, filler behind. */
    val shoeCards: () -> List<Card>,
    /** Starting bankroll (deep enough for the scripted doubled bets). */
    val startingChips: Int,
    /**
     * Offline stand-in for the advisor endpoint. Called instead of the HTTP
     * advisor whenever demo mode is active, so capture runs deterministic and
     * network-free.
     */
    val advice: (GameState, Set<PlayerAction>) -> String,
)

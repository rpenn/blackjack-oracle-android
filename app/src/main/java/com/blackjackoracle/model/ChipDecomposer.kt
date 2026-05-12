package com.blackjackoracle.model

/// Casino chip denominations matching the iOS reference. Used to render a bet
/// as a stack of physical chips: greedy decomposition from largest denomination
/// to smallest.
object ChipDecomposer {
    private val denominations = listOf(500, 100, 25, 10, 5, 1)

    fun chipsFor(amount: Int): List<Int> {
        val chips = mutableListOf<Int>()
        var remaining = amount.coerceAtLeast(0)
        for (denomination in denominations) {
            while (remaining >= denomination) {
                chips += denomination
                remaining -= denomination
            }
        }
        return chips
    }

    /// Lays the chips out in columns, top of the column nearest the player.
    /// Caps each column at `maxColumnSize` (default 4) so tall stacks wrap.
    fun columns(amount: Int, maxColumnSize: Int = 4): List<List<Int>> {
        require(maxColumnSize > 0) { "maxColumnSize must be positive" }
        return chipsFor(amount).chunked(maxColumnSize)
    }
}

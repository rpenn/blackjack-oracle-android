package com.blackjackoracle.model

object ChipDecomposer {
    private val denominations = listOf(500, 100, 25, 10, 5, 1)
    fun chipsFor(amount: Int): List<Int> {
        val chips = mutableListOf<Int>()
        var remaining = amount.coerceAtLeast(0)
        for (denomination in denominations) while (remaining >= denomination) { chips += denomination; remaining -= denomination }
        return chips
    }
    fun columns(amount: Int, maxColumnSize: Int = 4): List<List<Int>> {
        require(maxColumnSize > 0) { "maxColumnSize must be positive" }
        return chipsFor(amount).chunked(maxColumnSize)
    }
}

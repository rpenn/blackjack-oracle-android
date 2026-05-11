package com.blackjackoracle.model

import org.junit.Assert.*
import org.junit.Test

class ChipDecomposerTest {
    @Test fun decomposesUsingAuthoritativeDenominations() {
        assertEquals(listOf(100, 25, 10, 5, 1, 1), ChipDecomposer.chipsFor(142))
        assertEquals(listOf(500, 100, 25, 10, 5, 1, 1), ChipDecomposer.chipsFor(642))
        assertEquals(emptyList<Int>(), ChipDecomposer.chipsFor(0))
        assertEquals(287, ChipDecomposer.chipsFor(287).sum())
    }
    @Test fun columnsAreAtMostFourChips() {
        val columns = ChipDecomposer.columns(142)
        assertTrue(columns.all { it.size <= 4 })
        assertEquals(142, columns.flatten().sum())
    }
}

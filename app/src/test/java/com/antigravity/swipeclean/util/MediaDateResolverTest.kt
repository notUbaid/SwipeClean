package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDateResolverTest {

    @Test
    fun resolveDateTakenMs_prefersMillisTaken() {
        val ms = 1_700_000_000_000L
        assertEquals(ms, MediaDateResolver.resolveDateTakenMs(ms, 1_600_000_000L, 1_650_000_000L))
    }

    @Test
    fun resolveDateTakenMs_normalizesSecondsInDateTakenColumn() {
        val takenSeconds = 1_700_000_000L
        assertEquals(1_700_000_000_000L, MediaDateResolver.resolveDateTakenMs(takenSeconds, 1_600_000_000L, 1_650_000_000L))
    }

    @Test
    fun resolveDateTakenMs_fallsBackToAddedThenModifiedThenZero() {
        assertEquals(1_600_000_000_000L, MediaDateResolver.resolveDateTakenMs(0L, 1_600_000_000L, 1_650_000_000L))
        assertEquals(1_650_000_000_000L, MediaDateResolver.resolveDateTakenMs(0L, 0L, 1_650_000_000L))
        assertEquals(0L, MediaDateResolver.resolveDateTakenMs(-1L, 0L, 0L))
    }
}

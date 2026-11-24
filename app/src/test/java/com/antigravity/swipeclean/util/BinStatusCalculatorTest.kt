package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinStatusCalculatorTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun newlyTrashedItem_showsSevenDaysLeft() {
        val now = 1_700_000_000_000L
        val status = BinStatusCalculator.evaluate(trashedAtMs = now, nowMs = now)
        assertFalse(status.isExpired)
        assertEquals(7, status.daysRemaining)
        assertEquals("7 days left", status.label)
    }

    @Test
    fun sixDaysElapsed_usesSingularDayLeft() {
        val trashedAt = 1_700_000_000_000L
        val now = trashedAt + (6L * dayMs) + 3_600_000L
        val status = BinStatusCalculator.evaluate(trashedAtMs = trashedAt, nowMs = now)
        assertFalse(status.isExpired)
        assertEquals(1, status.daysRemaining)
        assertEquals("1 day left", status.label)
    }

    @Test
    fun sevenDaysOrMoreElapsed_isMarkedExpired() {
        val trashedAt = 1_700_000_000_000L
        val exactSevenDays = BinStatusCalculator.evaluate(trashedAt, trashedAt + 7L * dayMs)
        assertTrue(exactSevenDays.isExpired)
        assertEquals(0, exactSevenDays.daysRemaining)
        assertEquals("Expired", exactSevenDays.label)

        val thirtyDaysLater = BinStatusCalculator.evaluate(trashedAt, trashedAt + 30L * dayMs)
        assertTrue(thirtyDaysLater.isExpired)
        assertEquals(0, thirtyDaysLater.daysRemaining)
        assertEquals("Expired", thirtyDaysLater.label)
    }

    @Test
    fun futureTimestampSkew_doesNotExceedSevenDays() {
        val now = 1_700_000_000_000L
        val status = BinStatusCalculator.evaluate(trashedAtMs = now + 10_000L, nowMs = now)
        assertFalse(status.isExpired)
        assertEquals(7, status.daysRemaining)
        assertEquals("7 days left", status.label)
    }
}

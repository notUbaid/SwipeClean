package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    @Test
    fun newUser_showsZeroStreak() {
        val visible = StreakCalculator.visibleStreak(null, 0, LocalDate.of(2026, 11, 25))
        assertEquals(0, visible)
    }

    @Test
    fun staleStreak_evenWithStoredOne_showsZero() {
        val visible = StreakCalculator.visibleStreak(null, 1, LocalDate.of(2026, 11, 25))
        assertEquals(0, visible)
    }

    @Test
    fun firstActivity_setsStreakToOne() {
        val today = LocalDate.of(2026, 11, 25)
        val (date, streak) = StreakCalculator.recordActivity(null, 0, today)
        assertEquals("2026-11-25", date)
        assertEquals(1, streak)
    }

    @Test
    fun sameDayActivity_keepsStreakUnchanged() {
        val today = LocalDate.of(2026, 11, 25)
        val (date, streak) = StreakCalculator.recordActivity("2026-11-25", 4, today)
        assertEquals("2026-11-25", date)
        assertEquals(4, streak)
    }

    @Test
    fun consecutiveDayAcrossMonthAndDstBoundary_incrementsStreak() {
        val (d1, s1) = StreakCalculator.recordActivity("2026-10-31", 1, LocalDate.of(2026, 11, 1))
        assertEquals("2026-11-01", d1)
        assertEquals(2, s1)

        val (d2, s2) = StreakCalculator.recordActivity("2026-12-31", 5, LocalDate.of(2027, 1, 1))
        assertEquals("2027-01-01", d2)
        assertEquals(6, s2)
    }

    @Test
    fun missedDay_resetsVisibleStreakToZeroAndNextActivityToOne() {
        val visibleAfterSkip = StreakCalculator.visibleStreak(
            lastActiveDateStr = "2026-11-20",
            storedStreak = 9,
            today = LocalDate.of(2026, 11, 22)
        )
        assertEquals(0, visibleAfterSkip)

        val (date, streak) = StreakCalculator.recordActivity(
            lastActiveDateStr = "2026-11-20",
            storedStreak = 9,
            today = LocalDate.of(2026, 11, 22)
        )
        assertEquals("2026-11-22", date)
        assertEquals(1, streak)
    }

    @Test
    fun yesterdayActivity_stillVisibleToday() {
        val visible = StreakCalculator.visibleStreak(
            lastActiveDateStr = "2026-11-24",
            storedStreak = 3,
            today = LocalDate.of(2026, 11, 25)
        )
        assertEquals(3, visible)
    }
}

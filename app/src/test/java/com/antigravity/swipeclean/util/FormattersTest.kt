package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class FormattersTest {

    @Test
    fun formatBytes_scalesAcrossUnits() {
        assertEquals("0 B", formatBytes(-10L))
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1.5 KB", formatBytes(1536L))
        assertEquals("2.0 MB", formatBytes(2L * 1024L * 1024L))
        assertEquals("1.50 GB", formatBytes((1.5 * 1024.0 * 1024.0 * 1024.0).toLong()))
    }

    @Test
    fun formatDuration_handlesMinutesAndHours() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:09", formatDuration(9_000L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("1:01:01", formatDuration(3_661_000L))
    }

    @Test
    fun extractMonthDateMeta_handlesZeroAndValidEpoch() {
        val undated = extractMonthDateMeta(0L, ZoneOffset.UTC)
        assertEquals("0000-00", undated.monthYearKey)
        assertEquals("Undated", undated.monthYearDisplay)
        assertEquals(0, undated.year)

        // 2024-03-15T12:00:00Z = 1710504000000L
        val dated = extractMonthDateMeta(1_710_504_000_000L, ZoneOffset.UTC)
        assertEquals("2024-03", dated.monthYearKey)
        assertEquals("03-15", dated.dayMonthKey)
        assertEquals(2024, dated.year)
        assertTrue(dated.monthYearDisplay.contains("2024"))
    }
}

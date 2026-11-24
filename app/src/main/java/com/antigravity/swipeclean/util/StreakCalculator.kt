package com.antigravity.swipeclean.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object StreakCalculator {

    fun recordActivity(
        lastActiveDateStr: String?,
        storedStreak: Int,
        today: LocalDate = LocalDate.now()
    ): Pair<String, Int> {
        val lastDate = parseDate(lastActiveDateStr)
        val newStreak = when {
            lastDate == null -> 1
            else -> {
                val diff = ChronoUnit.DAYS.between(lastDate, today)
                when {
                    diff <= 0L -> storedStreak.coerceAtLeast(1)
                    diff == 1L -> storedStreak.coerceAtLeast(1) + 1
                    else -> 1
                }
            }
        }
        return Pair(today.toString(), newStreak)
    }

    fun visibleStreak(
        lastActiveDateStr: String?,
        storedStreak: Int,
        today: LocalDate = LocalDate.now()
    ): Int {
        val lastDate = parseDate(lastActiveDateStr) ?: return 0
        val diff = ChronoUnit.DAYS.between(lastDate, today)
        if (diff > 1L) return 0
        return storedStreak.coerceAtLeast(0)
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }
}

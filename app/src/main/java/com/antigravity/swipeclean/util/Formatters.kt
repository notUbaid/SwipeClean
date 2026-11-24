package com.antigravity.swipeclean.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MonthDateMeta(
    val monthYearKey: String,
    val monthYearDisplay: String,
    val dayMonthKey: String,
    val year: Int
)

private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
private val dayMonthFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.US)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hours = minutes / 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60L, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatShortDate(
    timestampMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    if (timestampMs <= 0L) return ""
    val localDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
    return DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(localDate)
}

fun extractMonthDateMeta(
    timestampMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): MonthDateMeta {
    if (timestampMs <= 0L) {
        return MonthDateMeta(
            monthYearKey = "0000-00",
            monthYearDisplay = "Undated",
            dayMonthKey = "",
            year = 0
        )
    }
    val localDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
    val monthDisplayFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    return MonthDateMeta(
        monthYearKey = monthKeyFormatter.format(localDate),
        monthYearDisplay = monthDisplayFormatter.format(localDate),
        dayMonthKey = dayMonthFormatter.format(localDate),
        year = localDate.year
    )
}

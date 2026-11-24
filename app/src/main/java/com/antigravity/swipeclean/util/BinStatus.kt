package com.antigravity.swipeclean.util

data class BinStatus(
    val daysRemaining: Int,
    val isExpired: Boolean,
    val label: String
)

object BinStatusCalculator {
    const val RETENTION_DAYS = 7
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun evaluate(
        trashedAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        retentionDays: Int = RETENTION_DAYS
    ): BinStatus {
        val elapsedMs = maxOf(0L, nowMs - trashedAtMs)
        val retentionMs = retentionDays.toLong() * DAY_MS
        val isExpired = elapsedMs >= retentionMs

        if (isExpired) {
            return BinStatus(daysRemaining = 0, isExpired = true, label = "Expired")
        }

        val elapsedWholeDays = (elapsedMs / DAY_MS).toInt()
        val daysLeft = (retentionDays - elapsedWholeDays).coerceIn(1, retentionDays)
        val label = if (daysLeft == 1) "1 day left" else "$daysLeft days left"
        return BinStatus(daysRemaining = daysLeft, isExpired = false, label = label)
    }
}

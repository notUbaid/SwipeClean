package com.antigravity.swipeclean.util

object MediaDateResolver {
    // Timestamps between 1 and 99,999,999,999 are almost certainly seconds rather than milliseconds (1973 threshold in ms)
    private const val SECONDS_UPPER_BOUND = 99_999_999_999L

    fun resolveDateTakenMs(
        rawTaken: Long,
        rawAddedSeconds: Long,
        rawModifiedSeconds: Long
    ): Long {
        val normalizedTaken = normalizeEpochToMs(rawTaken)
        if (normalizedTaken > 0L) return normalizedTaken
        if (rawAddedSeconds > 0L) return rawAddedSeconds * 1000L
        if (rawModifiedSeconds > 0L) return rawModifiedSeconds * 1000L
        return 0L
    }

    fun normalizeEpochToMs(value: Long): Long {
        if (value <= 0L) return 0L
        return if (value <= SECONDS_UPPER_BOUND) value * 1000L else value
    }
}

package com.antigravity.swipeclean.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.antigravity.swipeclean.util.BinStatus
import com.antigravity.swipeclean.util.BinStatusCalculator
import com.antigravity.swipeclean.util.MediaFingerprint
import com.antigravity.swipeclean.util.MediaIdentity
import com.antigravity.swipeclean.util.formatBytes
import com.antigravity.swipeclean.util.formatDuration
import com.antigravity.swipeclean.util.formatShortDate

@Immutable
data class MediaKey(
    val id: Long,
    val isVideo: Boolean
)

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateTakenMs: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bucketName: String,
    val monthYearKey: String,
    val monthYearDisplay: String,
    val dayMonthKey: String,
    val year: Int,
    val isVideo: Boolean,
    val fingerprint: String = MediaFingerprint.build(isVideo, sizeBytes, displayName)
) {
    val mediaKey: MediaKey
        get() = MediaKey(id, isVideo)

    val stableKey: String
        get() = "${if (isVideo) "v" else "i"}_$id"

    fun toIdentity(): MediaIdentity = MediaIdentity(
        key = mediaKey,
        uriString = uri.toString(),
        displayName = displayName,
        isVideo = isVideo,
        sizeBytes = sizeBytes,
        fingerprint = fingerprint
    )

    val formattedSize: String
        get() = formatBytes(sizeBytes)

    val formattedDuration: String
        get() = if (isVideo && durationMs > 0L) formatDuration(durationMs) else ""

    val formattedDate: String
        get() = formatShortDate(dateTakenMs)

    val resolutionText: String
        get() {
            if (width <= 0 || height <= 0) return ""
            val mp = (width.toLong() * height.toLong()) / 1_000_000.0
            return if (mp >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MP (%dx%d)", mp, width, height)
            } else {
                "${width}x${height}"
            }
        }
}

@Immutable
data class MonthProgress(
    val monthKey: String,
    val monthDisplay: String,
    val totalCount: Int,
    val reviewedCount: Int,
    val totalSizeBytes: Long,
    val coverUri: Uri? = null
) {
    val isCompleted: Boolean
        get() = totalCount > 0 && reviewedCount >= totalCount

    val progress: Float
        get() = if (totalCount > 0) (reviewedCount.toFloat() / totalCount).coerceIn(0f, 1f) else 0f

    val formattedSize: String
        get() = formatBytes(totalSizeBytes)
}

@Immutable
data class TrashedItem(
    val mediaItem: MediaItem,
    val trashedTimestampMs: Long
) {
    fun binStatus(nowMs: Long = System.currentTimeMillis()): BinStatus =
        BinStatusCalculator.evaluate(trashedTimestampMs, nowMs)

    val isExpired: Boolean
        get() = binStatus().isExpired
}

enum class QuickCleanCategory(val key: String, val title: String) {
    SIMILAR("SMART_SIMILAR", "Similar shots"),
    LARGE_VIDEOS("SMART_LARGE_VIDEOS", "Large videos"),
    SCREENSHOTS("SMART_SCREENSHOTS", "Screenshots")
}

@Immutable
data class QuickCleanGroup(
    val category: QuickCleanCategory,
    val count: Int,
    val totalSizeBytes: Long
) {
    val formattedSize: String
        get() = formatBytes(totalSizeBytes)
}

@Immutable
data class CleanStats(
    val totalKeptCount: Int = 0,
    val binItemCount: Int = 0,
    val binTotalSizeBytes: Long = 0L,
    val expiredBinCount: Int = 0,
    val totalLaterCount: Int = 0,
    val lifetimeDeletedCount: Int = 0,
    val lifetimeFreedBytes: Long = 0L,
    val streakDays: Int = 0
) {
    val formattedBinSize: String
        get() = formatBytes(binTotalSizeBytes)

    val formattedFreedSize: String
        get() = formatBytes(lifetimeFreedBytes)
}

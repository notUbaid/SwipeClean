package com.antigravity.swipeclean.data.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val dateModifiedSeconds: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bucketName: String,
    val monthYearKey: String, // "2026-11"
    val monthYearDisplay: String, // "November 2026"
    val dayMonthKey: String, // "11-24" (for On This Day matching)
    val year: Int,
    val isVideo: Boolean
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }

    val formattedDuration: String
        get() {
            if (!isVideo || durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    val formattedDate: String
        get() {
            val millis = if (dateModifiedSeconds > 0) dateModifiedSeconds * 1000 else dateAddedSeconds * 1000
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(millis))
        }

    val resolutionText: String
        get() {
            if (width <= 0 || height <= 0) return ""
            val mp = (width.toLong() * height.toLong()) / 1_000_000.0
            return if (mp >= 1.0) {
                String.format(Locale.US, "%.1f MP (%dx%d)", mp, width, height)
            } else {
                "${width}x${height}"
            }
        }
}

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
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}

data class TrashedItem(
    val mediaItem: MediaItem,
    val trashedTimestampMs: Long
) {
    val daysRemaining: Int
        get() {
            val elapsedMs = System.currentTimeMillis() - trashedTimestampMs
            val elapsedDays = (elapsedMs / (1000L * 60 * 60 * 24)).toInt()
            return maxOf(0, 7 - elapsedDays)
        }

    val isExpired: Boolean
        get() = daysRemaining <= 0
}

data class SwipeAction(
    val item: MediaItem,
    val actionType: String, // "KEPT", "TRASHED", "BOOKMARKED"
    val timestampMs: Long
)

data class AlbumGroup(
    val key: String,
    val displayName: String,
    val count: Int,
    val totalSizeBytes: Long,
    val isMonth: Boolean
) {
    val formattedSize: String
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}

data class CleanStats(
    val totalKeptCount: Int,
    val totalTrashedCount: Int,
    val totalBookmarkedCount: Int,
    val binItemCount: Int,
    val binTotalSizeBytes: Long,
    val streakDays: Int = 1
) {
    val formattedBinSize: String
        get() {
            val mb = binTotalSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}

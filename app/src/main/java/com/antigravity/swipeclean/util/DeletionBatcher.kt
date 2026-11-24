package com.antigravity.swipeclean.util

import com.antigravity.swipeclean.data.model.MediaItem

data class DeleteBatchPlan<T>(
    val batch: List<T>,
    val remainingCount: Int
)

data class FreedSummary(
    val count: Int,
    val totalBytes: Long
)

object DeletionBatcher {
    const val MAX_BATCH_SIZE = 200

    fun <T> planBatch(items: List<T>, maxBatchSize: Int = MAX_BATCH_SIZE): DeleteBatchPlan<T> {
        if (items.isEmpty() || maxBatchSize <= 0) {
            return DeleteBatchPlan(emptyList(), items.size)
        }
        val batch = items.take(maxBatchSize)
        return DeleteBatchPlan(
            batch = batch,
            remainingCount = items.size - batch.size
        )
    }

    fun summarizeSizes(sizes: Collection<Long>): FreedSummary {
        var totalBytes = 0L
        for (size in sizes) {
            totalBytes += maxOf(0L, size)
        }
        return FreedSummary(count = sizes.size, totalBytes = totalBytes)
    }

    fun summarizeFreed(deletedItems: Collection<MediaItem>): FreedSummary =
        summarizeSizes(deletedItems.map { it.sizeBytes })

    fun formatDeletionFeedback(
        deletedCount: Int,
        failedCount: Int,
        remainingBatchCount: Int
    ): String? {
        val parts = mutableListOf<String>()
        if (deletedCount > 0) {
            val noun = if (deletedCount == 1) "item" else "items"
            parts.add("Deleted $deletedCount $noun")
        }
        if (failedCount > 0) {
            parts.add("$failedCount could not be deleted (owned by another app)")
        }
        if (remainingBatchCount > 0) {
            parts.add("$remainingBatchCount left in bin — tap delete again for next batch")
        }
        return if (parts.isEmpty()) null else parts.joinToString(". ")
    }
}

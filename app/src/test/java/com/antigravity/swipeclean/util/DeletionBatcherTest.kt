package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeletionBatcherTest {

    @Test
    fun planBatch_capsAt200AndReportsExactRemainder() {
        val items = (1..450).toList()
        val first = DeletionBatcher.planBatch(items)
        assertEquals(200, first.batch.size)
        assertEquals(250, first.remainingCount)
        assertEquals(1, first.batch.first())
        assertEquals(200, first.batch.last())

        val small = DeletionBatcher.planBatch(listOf(1, 2, 3))
        assertEquals(3, small.batch.size)
        assertEquals(0, small.remainingCount)
    }

    @Test
    fun summarizeSizes_clampsNegativeValuesAndSumsAccurately() {
        val summary = DeletionBatcher.summarizeSizes(listOf(1_500_000L, -250L, 500_000L))
        assertEquals(3, summary.count)
        assertEquals(2_000_000L, summary.totalBytes)
    }

    @Test
    fun formatDeletionFeedback_reportsRemainingBatchAndLegacyFailures() {
        assertNull(DeletionBatcher.formatDeletionFeedback(0, 0, 0))
        assertEquals(
            "Deleted 1 item",
            DeletionBatcher.formatDeletionFeedback(1, 0, 0)
        )
        assertEquals(
            "Deleted 200 items. 3 could not be deleted (owned by another app). 45 left in bin — tap delete again for next batch",
            DeletionBatcher.formatDeletionFeedback(200, 3, 45)
        )
    }
}

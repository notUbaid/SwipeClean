package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFilterSortTest {

    private data class StubMedia(
        val id: Long,
        val isVideo: Boolean,
        val sizeBytes: Long,
        val dateTakenMs: Long
    )

    private val sampleItems = listOf(
        StubMedia(id = 1L, isVideo = false, sizeBytes = 500L, dateTakenMs = 1_000L),
        StubMedia(id = 2L, isVideo = true, sizeBytes = 5_000L, dateTakenMs = 3_000L),
        StubMedia(id = 3L, isVideo = false, sizeBytes = 2_000L, dateTakenMs = 2_000L),
        StubMedia(id = 4L, isVideo = true, sizeBytes = 1_000L, dateTakenMs = 3_000L),
        StubMedia(id = 5L, isVideo = false, sizeBytes = 500L, dateTakenMs = 4_000L)
    )

    private fun run(filter: MediaTypeFilter, sortOrder: MediaSortOrder): List<Long> =
        MediaFilterSort.sortAndFilter(
            items = sampleItems,
            filter = filter,
            sortOrder = sortOrder,
            isVideoOf = { it.isVideo },
            sizeOf = { it.sizeBytes },
            dateOf = { it.dateTakenMs },
            idOf = { it.id }
        ).map { it.id }

    @Test
    fun filtersAllPhotosAndVideosCorrectly() {
        assertEquals(listOf(5L, 4L, 2L, 3L, 1L), run(MediaTypeFilter.ALL, MediaSortOrder.NEWEST))
        assertEquals(listOf(5L, 3L, 1L), run(MediaTypeFilter.PHOTOS, MediaSortOrder.NEWEST))
        assertEquals(listOf(4L, 2L), run(MediaTypeFilter.VIDEOS, MediaSortOrder.NEWEST))
    }

    @Test
    fun sortsOldestWithIdTieBreaker() {
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L), run(MediaTypeFilter.ALL, MediaSortOrder.OLDEST))
    }

    @Test
    fun sortsLargestAndSmallestWithDateTieBreaker() {
        assertEquals(listOf(2L, 3L, 4L, 5L, 1L), run(MediaTypeFilter.ALL, MediaSortOrder.LARGEST))
        assertEquals(listOf(5L, 1L, 4L, 3L, 2L), run(MediaTypeFilter.ALL, MediaSortOrder.SMALLEST))
    }

    @Test
    fun combinesVideoFilterWithLargestSort() {
        assertEquals(listOf(2L, 4L), run(MediaTypeFilter.VIDEOS, MediaSortOrder.LARGEST))
        assertEquals(listOf(4L, 2L), run(MediaTypeFilter.VIDEOS, MediaSortOrder.SMALLEST))
    }
}

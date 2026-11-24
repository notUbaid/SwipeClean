package com.antigravity.swipeclean.util

import com.antigravity.swipeclean.data.model.MediaItem

enum class MediaTypeFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos")
}

enum class MediaSortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first")
}

object MediaFilterSort {

    fun apply(
        items: List<MediaItem>,
        filter: MediaTypeFilter,
        sortOrder: MediaSortOrder
    ): List<MediaItem> = sortAndFilter(
        items = items,
        filter = filter,
        sortOrder = sortOrder,
        isVideoOf = { it.isVideo },
        sizeOf = { it.sizeBytes },
        dateOf = { it.dateTakenMs },
        idOf = { it.id }
    )

    inline fun <T> sortAndFilter(
        items: List<T>,
        filter: MediaTypeFilter,
        sortOrder: MediaSortOrder,
        crossinline isVideoOf: (T) -> Boolean,
        crossinline sizeOf: (T) -> Long,
        crossinline dateOf: (T) -> Long,
        crossinline idOf: (T) -> Long
    ): List<T> {
        val filtered = when (filter) {
            MediaTypeFilter.ALL -> items
            MediaTypeFilter.PHOTOS -> items.filter { !isVideoOf(it) }
            MediaTypeFilter.VIDEOS -> items.filter { isVideoOf(it) }
        }

        val comparator = when (sortOrder) {
            MediaSortOrder.NEWEST -> compareByDescending<T> { dateOf(it) }.thenByDescending { idOf(it) }
            MediaSortOrder.OLDEST -> compareBy<T> { dateOf(it) }.thenBy { idOf(it) }
            MediaSortOrder.LARGEST -> compareByDescending<T> { sizeOf(it) }.thenByDescending { dateOf(it) }
            MediaSortOrder.SMALLEST -> compareBy<T> { sizeOf(it) }.thenByDescending { dateOf(it) }
        }

        return filtered.sortedWith(comparator)
    }
}

package com.antigravity.swipeclean.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.antigravity.swipeclean.data.db.SwipeCleanDbHelper
import com.antigravity.swipeclean.data.model.AlbumGroup
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.data.model.MonthProgress
import com.antigravity.swipeclean.data.model.TrashedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val dbHelper: SwipeCleanDbHelper = SwipeCleanDbHelper.getInstance(context)

    suspend fun loadAllMedia(selectedGroupKey: String? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()

        val allItems = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allItems)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allItems)

        allItems.sortByDescending { it.dateModifiedSeconds }

        // Filter out items already reviewed (kept, trashed, or bookmarked)
        val unreviewed = allItems.filter { !reviewedIds.contains(it.id) }

        when (selectedGroupKey) {
            "SMART_SIMILAR" -> loadSimilarPhotos()
            "SMART_LARGE_VIDEOS" -> loadLargeVideos()
            "SMART_SCREENSHOTS" -> loadScreenshots()
            null, "", "ALL" -> unreviewed
            else -> {
                unreviewed.filter { item ->
                    item.monthYearKey == selectedGroupKey || item.bucketName == selectedGroupKey
                }
            }
        }
    }

    suspend fun loadSimilarPhotos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()
        val allImages = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allImages)

        val unreviewed = allImages.filter { !reviewedIds.contains(it.id) }
            .sortedWith(compareBy({ it.bucketName }, { it.dateModifiedSeconds }))

        val similarPhotos = mutableListOf<MediaItem>()
        val clusterSet = mutableSetOf<Long>()

        for (i in 0 until unreviewed.size - 1) {
            val curr = unreviewed[i]
            val next = unreviewed[i + 1]

            if (curr.bucketName == next.bucketName &&
                kotlin.math.abs(curr.dateModifiedSeconds - next.dateModifiedSeconds) <= 5 &&
                curr.width == next.width && curr.height == next.height
            ) {
                if (clusterSet.add(curr.id)) similarPhotos.add(curr)
                if (clusterSet.add(next.id)) similarPhotos.add(next)
            }
        }
        similarPhotos
    }

    suspend fun loadLargeVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()
        val allVideos = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allVideos)

        allVideos.filter { !reviewedIds.contains(it.id) && it.sizeBytes >= 40 * 1024 * 1024 }
            .sortedByDescending { it.sizeBytes }
    }

    suspend fun loadScreenshots(): List<MediaItem> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()
        val allImages = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allImages)

        allImages.filter { item ->
            !reviewedIds.contains(item.id) &&
                    (item.bucketName.contains("screenshot", ignoreCase = true) ||
                     item.displayName.contains("screenshot", ignoreCase = true))
        }.sortedByDescending { it.dateModifiedSeconds }
    }

    suspend fun loadMonthProgressList(): List<MonthProgress> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()

        val allItems = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allItems)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allItems)

        allItems.sortByDescending { it.dateModifiedSeconds }

        val monthMap = mutableMapOf<String, MutableList<MediaItem>>()
        for (item in allItems) {
            monthMap.getOrPut(item.monthYearKey) { mutableListOf() }.add(item)
        }

        monthMap.entries
            .sortedByDescending { it.key }
            .map { (monthKey, items) ->
                val display = items.first().monthYearDisplay
                val totalCount = items.size
                val reviewedCount = items.count { reviewedIds.contains(it.id) }
                val totalSize = items.sumOf { it.sizeBytes }
                val coverUri = items.firstOrNull()?.uri

                MonthProgress(
                    monthKey = monthKey,
                    monthDisplay = display,
                    totalCount = totalCount,
                    reviewedCount = reviewedCount,
                    totalSizeBytes = totalSize,
                    coverUri = coverUri
                )
            }
    }

    suspend fun loadOnThisDayMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()

        val allItems = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allItems)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allItems)

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val todayMmDd = SimpleDateFormat("MM-dd", Locale.US).format(Date())

        allItems.filter { item ->
            !reviewedIds.contains(item.id) &&
                    item.dayMonthKey == todayMmDd &&
                    item.year < currentYear
        }.sortedByDescending { it.year }
    }

    suspend fun loadBookmarkedMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val bookmarkedIds = dbHelper.getAllBookmarkedIds()
        if (bookmarkedIds.isEmpty()) return@withContext emptyList()

        val allItems = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allItems)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allItems)

        val map = allItems.associateBy { it.id }
        bookmarkedIds.mapNotNull { map[it] }
    }

    suspend fun loadTrashedItems(): List<TrashedItem> = withContext(Dispatchers.IO) {
        val trashedMap = dbHelper.getAllTrashedMap()
        if (trashedMap.isEmpty()) return@withContext emptyList()

        val allItems = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allItems)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allItems)

        val itemMap = allItems.associateBy { it.id }

        trashedMap.mapNotNull { (mediaId, trashedAt) ->
            itemMap[mediaId]?.let { mediaItem ->
                TrashedItem(mediaItem = mediaItem, trashedTimestampMs = trashedAt)
            }
        }.sortedByDescending { it.trashedTimestampMs }
    }

    suspend fun loadAlbumGroups(): List<AlbumGroup> = withContext(Dispatchers.IO) {
        val reviewedIds = dbHelper.getAllReviewedIds()

        val allItems = mutableListOf<MediaItem>()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, allItems)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, allItems)

        val unreviewed = allItems.filter { !reviewedIds.contains(it.id) }

        val monthGroups = mutableMapOf<String, Pair<String, MutableList<MediaItem>>>()
        val albumGroups = mutableMapOf<String, MutableList<MediaItem>>()

        var allTotalSize = 0L
        for (item in unreviewed) {
            allTotalSize += item.sizeBytes
            val monthPair = monthGroups.getOrPut(item.monthYearKey) {
                Pair(item.monthYearDisplay, mutableListOf())
            }
            monthPair.second.add(item)

            val bucket = item.bucketName.ifEmpty { "Other" }
            albumGroups.getOrPut(bucket) { mutableListOf() }.add(item)
        }

        val result = mutableListOf<AlbumGroup>()

        result.add(
            AlbumGroup(
                key = "ALL",
                displayName = "All Photos & Videos",
                count = unreviewed.size,
                totalSizeBytes = allTotalSize,
                isMonth = false
            )
        )

        val similar = loadSimilarPhotos()
        if (similar.isNotEmpty()) {
            result.add(
                AlbumGroup(
                    key = "SMART_SIMILAR",
                    displayName = "⚡ Similar & Burst Shots",
                    count = similar.size,
                    totalSizeBytes = similar.sumOf { it.sizeBytes },
                    isMonth = false
                )
            )
        }

        val largeVideos = loadLargeVideos()
        if (largeVideos.isNotEmpty()) {
            result.add(
                AlbumGroup(
                    key = "SMART_LARGE_VIDEOS",
                    displayName = "🎬 Large Videos (> 40MB)",
                    count = largeVideos.size,
                    totalSizeBytes = largeVideos.sumOf { it.sizeBytes },
                    isMonth = false
                )
            )
        }

        val screenshots = loadScreenshots()
        if (screenshots.isNotEmpty()) {
            result.add(
                AlbumGroup(
                    key = "SMART_SCREENSHOTS",
                    displayName = "📸 Screenshots",
                    count = screenshots.size,
                    totalSizeBytes = screenshots.sumOf { it.sizeBytes },
                    isMonth = false
                )
            )
        }

        monthGroups.entries
            .sortedByDescending { it.key }
            .forEach { (key, pair) ->
                val (display, list) = pair
                result.add(
                    AlbumGroup(
                        key = key,
                        displayName = display,
                        count = list.size,
                        totalSizeBytes = list.sumOf { it.sizeBytes },
                        isMonth = true
                    )
                )
            }

        albumGroups.entries
            .sortedByDescending { it.value.size }
            .forEach { (bucketName, list) ->
                result.add(
                    AlbumGroup(
                        key = bucketName,
                        displayName = bucketName,
                        count = list.size,
                        totalSizeBytes = list.sumOf { it.sizeBytes },
                        isMonth = false
                    )
                )
            }

        result
    }

    private fun loadMediaFromUri(
        contentUri: Uri,
        isVideo: Boolean,
        destination: MutableList<MediaItem>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            if (isVideo) MediaStore.Video.VideoColumns.DURATION else MediaStore.MediaColumns._ID
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(contentUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val dateAddCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
                val monthDisplayFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val dayMonthFormat = SimpleDateFormat("MM-dd", Locale.US)
                val yearFormat = SimpleDateFormat("yyyy", Locale.US)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(contentUri, id)
                    val displayName = cursor.getString(nameCol) ?: "Media_$id"
                    val mimeType = cursor.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*"
                    val size = cursor.getLong(sizeCol)
                    val dateMod = cursor.getLong(dateModCol)
                    val dateAdd = cursor.getLong(dateAddCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val bucket = cursor.getString(bucketCol) ?: "Default"
                    val duration = if (isVideo && durationCol >= 0) cursor.getLong(durationCol) else 0L

                    val timestampMs = if (dateMod > 0) dateMod * 1000 else if (dateAdd > 0) dateAdd * 1000 else System.currentTimeMillis()
                    val dateObj = Date(timestampMs)
                    val monthKey = monthKeyFormat.format(dateObj)
                    val monthDisplay = monthDisplayFormat.format(dateObj)
                    val dayMonth = dayMonthFormat.format(dateObj)
                    val year = yearFormat.format(dateObj).toIntOrNull() ?: 2026

                    destination.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = size,
                            dateAddedSeconds = dateAdd,
                            dateModifiedSeconds = dateMod,
                            durationMs = duration,
                            width = width,
                            height = height,
                            bucketName = bucket,
                            monthYearKey = monthKey,
                            monthYearDisplay = monthDisplay,
                            dayMonthKey = dayMonth,
                            year = year,
                            isVideo = isVideo
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createDeletePendingIntent(uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    suspend fun deletePermanentlyLegacy(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (uri in uris) {
            try {
                count += contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        count
    }
}

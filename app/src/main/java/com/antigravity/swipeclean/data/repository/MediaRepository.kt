package com.antigravity.swipeclean.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.data.model.MediaKey
import com.antigravity.swipeclean.data.model.MonthProgress
import com.antigravity.swipeclean.data.model.QuickCleanCategory
import com.antigravity.swipeclean.data.model.QuickCleanGroup
import com.antigravity.swipeclean.data.model.TrashedItem
import com.antigravity.swipeclean.util.PerceptualHash
import com.antigravity.swipeclean.util.extractMonthDateMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class LegacyDeleteResult(
    val deletedItems: List<MediaItem>,
    val failedCount: Int
)

class MediaRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val scanMutex = Mutex()
    private val mediaDirty = AtomicBoolean(false)
    private var isObserverRegistered = false

    @Volatile
    private var cachedLibrary: List<MediaItem> = emptyList()

    @Volatile
    private var cachedByKey: Map<MediaKey, MediaItem> = emptyMap()

    @Volatile
    private var hasScannedOnce: Boolean = false

    private val dHashCache = HashMap<MediaKey, Long?>()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            mediaDirty.set(true)
        }
    }

    fun startObserving() {
        if (isObserverRegistered) return
        isObserverRegistered = true
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun stopObserving() {
        if (!isObserverRegistered) return
        isObserverRegistered = false
        contentResolver.unregisterContentObserver(contentObserver)
    }

    fun consumeDirty(): Boolean = mediaDirty.getAndSet(false)

    suspend fun getLibrary(forceRefresh: Boolean = false): List<MediaItem> {
        if (!forceRefresh && hasScannedOnce) {
            return cachedLibrary
        }
        return scanMutex.withLock {
            if (!forceRefresh && hasScannedOnce) {
                return@withLock cachedLibrary
            }
            withContext(Dispatchers.IO) {
                val items = ArrayList<MediaItem>(512)
                queryMediaTable(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, items)
                queryMediaTable(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, items)
                items.sortByDescending { it.dateTakenMs }

                val byKey = HashMap<MediaKey, MediaItem>(items.size)
                for (item in items) {
                    byKey[item.mediaKey] = item
                }
                cachedLibrary = items
                cachedByKey = byKey
                hasScannedOnce = true
                mediaDirty.set(false)
                items
            }
        }
    }

    fun findByKey(key: MediaKey): MediaItem? = cachedByKey[key]

    suspend fun computeDeck(
        allItems: List<MediaItem>,
        groupKey: String?,
        reviewedKeys: Set<MediaKey>
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        val unreviewed = allItems.filter { !reviewedKeys.contains(it.mediaKey) }
        when (groupKey) {
            QuickCleanCategory.SIMILAR.key -> findSimilarPhotos(unreviewed)
            QuickCleanCategory.LARGE_VIDEOS.key -> findLargeVideos(unreviewed)
            QuickCleanCategory.SCREENSHOTS.key -> findScreenshots(unreviewed)
            "ON_THIS_DAY" -> buildOnThisDay(unreviewed)
            null, "", "ALL" -> unreviewed
            else -> unreviewed.filter { it.monthYearKey == groupKey }
        }
    }

    suspend fun buildMonthProgressList(
        allItems: List<MediaItem>,
        reviewedKeys: Set<MediaKey>
    ): List<MonthProgress> = withContext(Dispatchers.Default) {
        val monthMap = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in allItems) {
            monthMap.getOrPut(item.monthYearKey) { mutableListOf() }.add(item)
        }

        monthMap.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<MediaItem>>> { it.key })
            .map { (monthKey, items) ->
                MonthProgress(
                    monthKey = monthKey,
                    monthDisplay = items.first().monthYearDisplay,
                    totalCount = items.size,
                    reviewedCount = items.count { reviewedKeys.contains(it.mediaKey) },
                    totalSizeBytes = items.sumOf { maxOf(0L, it.sizeBytes) },
                    coverUri = items.firstOrNull()?.uri
                )
            }
    }

    suspend fun buildOnThisDay(unreviewed: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.Default) {
        val today = LocalDate.now()
        val currentYear = today.year
        val todayMmDd = DateTimeFormatter.ofPattern("MM-dd", Locale.US).format(today)

        unreviewed.filter { item ->
            item.year in 1901 until currentYear && item.dayMonthKey == todayMmDd
        }.sortedByDescending { it.year }
    }

    suspend fun buildQuickCleanGroups(unreviewed: List<MediaItem>): List<QuickCleanGroup> = withContext(Dispatchers.Default) {
        val similar = findSimilarPhotos(unreviewed)
        val largeVideos = findLargeVideos(unreviewed)
        val screenshots = findScreenshots(unreviewed)

        buildList {
            if (similar.isNotEmpty()) {
                add(
                    QuickCleanGroup(
                        category = QuickCleanCategory.SIMILAR,
                        count = similar.size,
                        totalSizeBytes = similar.sumOf { maxOf(0L, it.sizeBytes) }
                    )
                )
            }
            if (largeVideos.isNotEmpty()) {
                add(
                    QuickCleanGroup(
                        category = QuickCleanCategory.LARGE_VIDEOS,
                        count = largeVideos.size,
                        totalSizeBytes = largeVideos.sumOf { maxOf(0L, it.sizeBytes) }
                    )
                )
            }
            if (screenshots.isNotEmpty()) {
                add(
                    QuickCleanGroup(
                        category = QuickCleanCategory.SCREENSHOTS,
                        count = screenshots.size,
                        totalSizeBytes = screenshots.sumOf { maxOf(0L, it.sizeBytes) }
                    )
                )
            }
        }
    }

    private suspend fun findSimilarPhotos(unreviewed: List<MediaItem>): List<MediaItem> {
        val images = unreviewed
            .filter { !it.isVideo }
            .sortedWith(compareBy<MediaItem>({ it.bucketName }, { it.dateTakenMs }))

        if (images.size < 2) return emptyList()

        val similarPhotos = mutableListOf<MediaItem>()
        val addedKeys = HashSet<MediaKey>()

        for (i in 0 until images.size - 1) {
            val curr = images[i]
            val next = images[i + 1]

            val timeDiffMs = kotlin.math.abs(curr.dateTakenMs - next.dateTakenMs)
            if (curr.bucketName != next.bucketName || timeDiffMs > 8_000L || !sameAspect(curr, next)) {
                continue
            }

            val h1 = loadOrCacheDHash(curr)
            val h2 = loadOrCacheDHash(next)
            val isVisualMatch = if (h1 != null && h2 != null) {
                PerceptualHash.hammingDistance(h1, h2) <= PerceptualHash.DEFAULT_SIMILARITY_THRESHOLD
            } else {
                curr.width == next.width && curr.height == next.height && timeDiffMs <= 5_000L
            }

            if (isVisualMatch) {
                if (addedKeys.add(curr.mediaKey)) similarPhotos.add(curr)
                if (addedKeys.add(next.mediaKey)) similarPhotos.add(next)
            }
        }
        return similarPhotos
    }

    private fun sameAspect(a: MediaItem, b: MediaItem): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val r1 = a.width.toFloat() / a.height.toFloat()
        val r2 = b.width.toFloat() / b.height.toFloat()
        return kotlin.math.abs(r1 - r2) <= 0.1f
    }

    private suspend fun loadOrCacheDHash(item: MediaItem): Long? {
        synchronized(dHashCache) {
            if (dHashCache.containsKey(item.mediaKey)) {
                return dHashCache[item.mediaKey]
            }
        }
        val hash = withContext(Dispatchers.IO) {
            runCatching {
                val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(item.uri, Size(64, 64), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        contentResolver,
                        item.id,
                        MediaStore.Images.Thumbnails.MICRO_KIND,
                        null
                    )
                }
                bmp?.let {
                    val computed = PerceptualHash.computeDHash(it)
                    it.recycle()
                    computed
                }
            }.getOrNull()
        }
        synchronized(dHashCache) {
            dHashCache[item.mediaKey] = hash
        }
        return hash
    }

    private fun findLargeVideos(unreviewed: List<MediaItem>): List<MediaItem> =
        unreviewed
            .filter { it.isVideo && it.sizeBytes >= 40L * 1024L * 1024L }
            .sortedByDescending { it.sizeBytes }

    private fun findScreenshots(unreviewed: List<MediaItem>): List<MediaItem> =
        unreviewed
            .filter { item ->
                !item.isVideo &&
                    (item.bucketName.contains("screenshot", ignoreCase = true) ||
                        item.displayName.contains("screenshot", ignoreCase = true))
            }
            .sortedByDescending { it.dateTakenMs }

    suspend fun buildLaterList(
        allItems: List<MediaItem>,
        laterKeys: List<MediaKey>
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        if (laterKeys.isEmpty()) return@withContext emptyList()
        val map = allItems.associateBy { it.mediaKey }
        laterKeys.mapNotNull { map[it] }
    }

    suspend fun buildTrashedList(
        allItems: List<MediaItem>,
        trashedMap: Map<MediaKey, Long>
    ): List<TrashedItem> = withContext(Dispatchers.Default) {
        if (trashedMap.isEmpty()) return@withContext emptyList()
        val map = allItems.associateBy { it.mediaKey }
        trashedMap.mapNotNull { (key, trashedAt) ->
            map[key]?.let { item -> TrashedItem(item, trashedAt) }
        }.sortedByDescending { it.trashedTimestampMs }
    }

    @Suppress("DEPRECATION")
    private fun queryMediaTable(
        contentUri: Uri,
        isVideo: Boolean,
        destination: MutableList<MediaItem>
    ) {
        val dateTakenColName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.DATE_TAKEN
        } else {
            "datetaken"
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            dateTakenColName,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            if (isVideo) MediaStore.Video.VideoColumns.DURATION else MediaStore.MediaColumns._ID
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        runCatching {
            contentResolver.query(contentUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val takenCol = cursor.getColumnIndex(dateTakenColName)
                val addCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(contentUri, id)
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "Media_$id" else "Media_$id"
                    val mimeType = if (mimeCol >= 0) {
                        cursor.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*"
                    } else if (isVideo) {
                        "video/*"
                    } else {
                        "image/*"
                    }
                    val size = if (sizeCol >= 0) maxOf(0L, cursor.getLong(sizeCol)) else 0L
                    val rawTaken = if (takenCol >= 0 && !cursor.isNull(takenCol)) cursor.getLong(takenCol) else 0L
                    val rawAdd = if (addCol >= 0 && !cursor.isNull(addCol)) cursor.getLong(addCol) else 0L
                    val rawMod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                    val width = if (widthCol >= 0) maxOf(0, cursor.getInt(widthCol)) else 0
                    val height = if (heightCol >= 0) maxOf(0, cursor.getInt(heightCol)) else 0
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Other" else "Other"
                    val duration = if (isVideo && durationCol >= 0) maxOf(0L, cursor.getLong(durationCol)) else 0L

                    val dateTakenMs = when {
                        rawTaken > 0L -> rawTaken
                        rawAdd > 0L -> rawAdd * 1000L
                        rawMod > 0L -> rawMod * 1000L
                        else -> 0L
                    }

                    val dateMeta = extractMonthDateMeta(dateTakenMs)

                    destination.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = size,
                            dateTakenMs = dateTakenMs,
                            durationMs = duration,
                            width = width,
                            height = height,
                            bucketName = bucket,
                            monthYearKey = dateMeta.monthYearKey,
                            monthYearDisplay = dateMeta.monthYearDisplay,
                            dayMonthKey = dateMeta.dayMonthKey,
                            year = dateMeta.year,
                            isVideo = isVideo
                        )
                    )
                }
            }
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

    suspend fun deletePermanentlyLegacy(items: List<MediaItem>): LegacyDeleteResult = withContext(Dispatchers.IO) {
        val deleted = mutableListOf<MediaItem>()
        var failed = 0
        for (item in items) {
            val removed = runCatching {
                contentResolver.delete(item.uri, null, null) > 0
            }.getOrDefault(false)
            if (removed) {
                deleted.add(item)
            } else {
                failed++
            }
        }
        LegacyDeleteResult(deletedItems = deleted, failedCount = failed)
    }
}

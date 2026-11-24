package com.antigravity.swipeclean.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
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
import com.antigravity.swipeclean.util.MediaDateResolver
import com.antigravity.swipeclean.util.MediaFilterSort
import com.antigravity.swipeclean.util.MediaFingerprint
import com.antigravity.swipeclean.util.MediaSortOrder
import com.antigravity.swipeclean.util.MediaTypeFilter
import com.antigravity.swipeclean.util.PerceptualHash
import com.antigravity.swipeclean.util.extractMonthDateMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val scanMutex = Mutex()
    private val thumbDispatcher = Dispatchers.IO.limitedParallelism(4)
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
                currentCoroutineContext().ensureActive()
                queryMediaTable(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, items)
                currentCoroutineContext().ensureActive()
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
        reviewedKeys: Set<MediaKey>,
        typeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
        sortOrder: MediaSortOrder = MediaSortOrder.NEWEST
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        val unreviewed = allItems.filter { !reviewedKeys.contains(it.mediaKey) }
        val scoped = when (groupKey) {
            QuickCleanCategory.SIMILAR.key -> findSimilarPhotos(unreviewed)
            QuickCleanCategory.LARGE_VIDEOS.key -> findLargeVideos(unreviewed)
            QuickCleanCategory.SCREENSHOTS.key -> findScreenshots(unreviewed)
            "ON_THIS_DAY" -> buildOnThisDay(unreviewed)
            null, "", "ALL" -> unreviewed
            else -> unreviewed.filter { it.monthYearKey == groupKey }
        }
        MediaFilterSort.apply(scoped, typeFilter, sortOrder)
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

    suspend fun buildFastQuickCleanGroups(unreviewed: List<MediaItem>): List<QuickCleanGroup> = withContext(Dispatchers.Default) {
        val largeVideos = findLargeVideos(unreviewed)
        val screenshots = findScreenshots(unreviewed)

        buildList {
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

    suspend fun buildSimilarQuickCleanGroup(unreviewed: List<MediaItem>): QuickCleanGroup? = withContext(Dispatchers.Default) {
        val similar = findSimilarPhotos(unreviewed)
        if (similar.isEmpty()) return@withContext null
        QuickCleanGroup(
            category = QuickCleanCategory.SIMILAR,
            count = similar.size,
            totalSizeBytes = similar.sumOf { maxOf(0L, it.sizeBytes) }
        )
    }

    suspend fun findSimilarPhotos(unreviewed: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.Default) {
        val images = unreviewed
            .filter { !it.isVideo }
            .sortedWith(compareBy<MediaItem>({ it.bucketName }, { it.dateTakenMs }))

        if (images.size < 2) return@withContext emptyList()

        // Collect candidate burst indices first without blocking UI
        val candidatePairs = ArrayList<Pair<Int, Int>>()
        val candidateItems = LinkedHashSet<MediaItem>()
        for (i in 0 until images.size - 1) {
            currentCoroutineContext().ensureActive()
            val curr = images[i]
            val next = images[i + 1]
            val timeDiffMs = kotlin.math.abs(curr.dateTakenMs - next.dateTakenMs)
            if (curr.bucketName == next.bucketName && timeDiffMs <= 8_000L && sameAspect(curr, next)) {
                candidatePairs.add(i to (i + 1))
                candidateItems.add(curr)
                candidateItems.add(next)
                if (candidateItems.size >= 400) break
            }
        }

        if (candidatePairs.isEmpty()) return@withContext emptyList()

        // Prefetch hashes concurrently with bounded parallelism
        coroutineScope {
            candidateItems.map { item ->
                async(thumbDispatcher) {
                    loadOrCacheDHash(item)
                }
            }.awaitAll()
        }

        val similarPhotos = mutableListOf<MediaItem>()
        val addedKeys = HashSet<MediaKey>()
        for ((i, j) in candidatePairs) {
            val curr = images[i]
            val next = images[j]
            val timeDiffMs = kotlin.math.abs(curr.dateTakenMs - next.dateTakenMs)
            val h1 = synchronized(dHashCache) { dHashCache[curr.mediaKey] }
            val h2 = synchronized(dHashCache) { dHashCache[next.mediaKey] }

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
        similarPhotos
    }

    private fun sameAspect(a: MediaItem, b: MediaItem): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val r1 = a.width.toFloat() / a.height.toFloat()
        val r2 = b.width.toFloat() / b.height.toFloat()
        return kotlin.math.abs(r1 - r2) <= 0.1f
    }

    private fun loadOrCacheDHash(item: MediaItem): Long? {
        synchronized(dHashCache) {
            if (dHashCache.containsKey(item.mediaKey)) {
                return dHashCache[item.mediaKey]
            }
        }
        val hash = runCatching {
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

        synchronized(dHashCache) {
            if (dHashCache.size >= 2000) {
                dHashCache.clear()
            }
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
    private suspend fun queryMediaTable(
        contentUri: Uri,
        isVideo: Boolean,
        destination: MutableList<MediaItem>
    ) {
        val dateTakenColName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.DATE_TAKEN
        } else {
            "datetaken"
        }

        val fullProjection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(dateTakenColName)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        val fallbackProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val cursor: Cursor? = try {
            contentResolver.query(contentUri, fullProjection, null, null, sortOrder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (_: Exception) {
            contentResolver.query(contentUri, fallbackProjection, null, null, sortOrder)
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndex(dateTakenColName)
            val addCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val bucketCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durationCol = if (isVideo) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

            var rowCount = 0
            while (c.moveToNext()) {
                if (++rowCount and 127 == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(contentUri, id)
                val rawDisplayName = if (nameCol >= 0) c.getString(nameCol) else null
                val displayName = rawDisplayName ?: "Media_$id"
                val mimeType = if (mimeCol >= 0) {
                    c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*"
                } else if (isVideo) {
                    "video/*"
                } else {
                    "image/*"
                }
                val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) maxOf(0L, c.getLong(sizeCol)) else 0L
                val rawTaken = if (takenCol >= 0 && !c.isNull(takenCol)) c.getLong(takenCol) else 0L
                val rawAdd = if (addCol >= 0 && !c.isNull(addCol)) c.getLong(addCol) else 0L
                val rawMod = if (modCol >= 0 && !c.isNull(modCol)) c.getLong(modCol) else 0L
                val width = if (widthCol >= 0 && !c.isNull(widthCol)) maxOf(0, c.getInt(widthCol)) else 0
                val height = if (heightCol >= 0 && !c.isNull(heightCol)) maxOf(0, c.getInt(heightCol)) else 0
                val bucket = if (bucketCol >= 0) c.getString(bucketCol) ?: "Other" else "Other"
                val duration = if (isVideo && durationCol >= 0 && !c.isNull(durationCol)) maxOf(0L, c.getLong(durationCol)) else 0L

                val dateTakenMs = MediaDateResolver.resolveDateTakenMs(rawTaken, rawAdd, rawMod)
                val dateMeta = extractMonthDateMeta(dateTakenMs)
                val fingerprint = MediaFingerprint.build(isVideo, size, rawDisplayName)

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
                        isVideo = isVideo,
                        fingerprint = fingerprint
                    )
                )
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

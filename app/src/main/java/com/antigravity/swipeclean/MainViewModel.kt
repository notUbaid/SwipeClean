package com.antigravity.swipeclean

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.swipeclean.data.db.SwipeCleanDbHelper
import com.antigravity.swipeclean.data.model.CleanStats
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.data.model.MediaKey
import com.antigravity.swipeclean.data.model.MonthProgress
import com.antigravity.swipeclean.data.model.QuickCleanCategory
import com.antigravity.swipeclean.data.model.QuickCleanGroup
import com.antigravity.swipeclean.data.model.TrashedItem
import com.antigravity.swipeclean.data.repository.MediaRepository
import com.antigravity.swipeclean.util.DeletionBatcher
import com.antigravity.swipeclean.util.MediaSortOrder
import com.antigravity.swipeclean.util.MediaTypeFilter
import com.antigravity.swipeclean.util.SwipeUndoStack
import com.antigravity.swipeclean.util.UndoStackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class AllLibrarySummary(
    val count: Int = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val totalSizeBytes: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val dbHelper = SwipeCleanDbHelper.getInstance(application)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _mediaDeck = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaDeck: StateFlow<List<MediaItem>> = _mediaDeck.asStateFlow()

    private val _monthProgressList = MutableStateFlow<List<MonthProgress>>(emptyList())
    val monthProgressList: StateFlow<List<MonthProgress>> = _monthProgressList.asStateFlow()

    private val _quickCleanGroups = MutableStateFlow<List<QuickCleanGroup>>(emptyList())
    val quickCleanGroups: StateFlow<List<QuickCleanGroup>> = _quickCleanGroups.asStateFlow()

    private val _unreviewedSummary = MutableStateFlow(AllLibrarySummary())
    val unreviewedSummary: StateFlow<AllLibrarySummary> = _unreviewedSummary.asStateFlow()

    private val _onThisDayList = MutableStateFlow<List<MediaItem>>(emptyList())
    val onThisDayList: StateFlow<List<MediaItem>> = _onThisDayList.asStateFlow()

    private val _laterList = MutableStateFlow<List<MediaItem>>(emptyList())
    val laterList: StateFlow<List<MediaItem>> = _laterList.asStateFlow()

    private val _trashedList = MutableStateFlow<List<TrashedItem>>(emptyList())
    val trashedList: StateFlow<List<TrashedItem>> = _trashedList.asStateFlow()

    private val _activeDeckKey = MutableStateFlow<String?>("ALL")
    private val _activeDeckTitle = MutableStateFlow("All items")
    val activeDeckTitle: StateFlow<String> = _activeDeckTitle.asStateFlow()

    private val _typeFilter = MutableStateFlow(MediaTypeFilter.ALL)
    val typeFilter: StateFlow<MediaTypeFilter> = _typeFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(MediaSortOrder.NEWEST)
    val sortOrder: StateFlow<MediaSortOrder> = _sortOrder.asStateFlow()

    private val _stagedForDeletion = MutableStateFlow<List<MediaItem>>(emptyList())
    val stagedForDeletion: StateFlow<List<MediaItem>> = _stagedForDeletion.asStateFlow()

    private val _showReviewDeletions = MutableStateFlow(false)
    val showReviewDeletions: StateFlow<Boolean> = _showReviewDeletions.asStateFlow()

    private val _stats = MutableStateFlow(CleanStats())
    val stats: StateFlow<CleanStats> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _inspectItem = MutableStateFlow<MediaItem?>(null)
    val inspectItem: StateFlow<MediaItem?> = _inspectItem.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val sessionActionStack = SwipeUndoStack<MediaItem>()
    private var inFlightKeys = HashSet<MediaKey>()
    private var cachedReviewedKeys = HashSet<MediaKey>()

    private val loadSeq = AtomicLong(0L)
    private var loadJob: Job? = null
    private var similarJob: Job? = null
    @Volatile
    private var hasCompletedInitialLoad = false

    init {
        repository.startObserving()
    }

    override fun onCleared() {
        repository.stopObserving()
        super.onCleared()
    }

    fun ensureInitialLoaded() {
        if (hasCompletedInitialLoad || loadJob?.isActive == true) return
        loadData(
            groupKey = _activeDeckKey.value ?: "ALL",
            forceLibraryScan = false,
            showSpinner = true
        )
    }

    fun loadData(
        groupKey: String? = _activeDeckKey.value,
        forceLibraryScan: Boolean = false,
        showSpinner: Boolean = _monthProgressList.value.isEmpty()
    ) {
        _activeDeckKey.value = groupKey
        val mySeq = loadSeq.incrementAndGet()
        loadJob?.cancel()
        similarJob?.cancel()

        loadJob = viewModelScope.launch {
            if (showSpinner) {
                _isLoading.value = true
            }
            _scanError.value = null

            try {
                val allItems = repository.getLibrary(forceRefresh = forceLibraryScan)
                val (reviewedKeys, laterKeys, trashedMap, statsSnapshot, hasDbUndo) =
                    withContext(dbDispatcher) {
                        SnapshotTuple(
                            reviewed = dbHelper.getAllReviewedKeys(),
                            later = dbHelper.getAllLaterKeys(),
                            trashed = dbHelper.getAllTrashedMap(),
                            stats = dbHelper.getStats(),
                            hasUndo = dbHelper.hasUndoHistory()
                        )
                    }

                val stagedKeys = _stagedForDeletion.value.map { it.mediaKey }.toSet()
                cachedReviewedKeys = reviewedKeys.toHashSet()
                val effectiveExcluded = reviewedKeys + stagedKeys

                val unreviewed = allItems.filter { !effectiveExcluded.contains(it.mediaKey) }
                val months = repository.buildMonthProgressList(allItems, reviewedKeys)
                val onThisDay = repository.buildOnThisDay(unreviewed)
                val fastQuickGroups = repository.buildFastQuickCleanGroups(unreviewed)
                val later = repository.buildLaterList(allItems, laterKeys)
                val trashed = repository.buildTrashedList(allItems, trashedMap)
                val deck = repository.computeDeck(
                    allItems = allItems,
                    groupKey = groupKey,
                    reviewedKeys = effectiveExcluded,
                    typeFilter = _typeFilter.value,
                    sortOrder = _sortOrder.value
                )

                if (loadSeq.get() != mySeq) return@launch

                _mediaDeck.value = deck
                _monthProgressList.value = months
                _onThisDayList.value = onThisDay
                _quickCleanGroups.value = fastQuickGroups
                val videoCount = unreviewed.count { it.isVideo }
                _unreviewedSummary.value = AllLibrarySummary(
                    count = unreviewed.size,
                    photoCount = unreviewed.size - videoCount,
                    videoCount = videoCount,
                    totalSizeBytes = unreviewed.sumOf { maxOf(0L, it.sizeBytes) }
                )
                _laterList.value = later
                _trashedList.value = trashed
                _stats.value = statsSnapshot
                _canUndo.value = sessionActionStack.isNotEmpty() || hasDbUndo
                inFlightKeys.clear()
                hasCompletedInitialLoad = true
                _isLoading.value = false

                // Compute visual similarity asynchronously after UI unlocks so startup never stalls on thumbnails
                launchSimilarDetection(mySeq, unreviewed, fastQuickGroups)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (loadSeq.get() == mySeq) {
                    _scanError.value = t.message ?: "Unable to read media library"
                }
            } finally {
                if (loadSeq.get() == mySeq) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun launchSimilarDetection(
        expectedSeq: Long,
        unreviewedSnapshot: List<MediaItem>,
        baseGroups: List<QuickCleanGroup>
    ) {
        similarJob = viewModelScope.launch {
            val similarGroup = runCatching {
                repository.buildSimilarQuickCleanGroup(unreviewedSnapshot)
            }.getOrNull() ?: return@launch

            if (loadSeq.get() == expectedSeq) {
                val combined = buildList {
                    add(similarGroup)
                    addAll(baseGroups.filter { it.category != QuickCleanCategory.SIMILAR })
                }
                _quickCleanGroups.value = combined
            }
        }
    }

    fun onAppResumed() {
        if (repository.consumeDirty()) {
            loadData(
                groupKey = _activeDeckKey.value,
                forceLibraryScan = true,
                showSpinner = false
            )
        } else {
            viewModelScope.launch {
                val statsSnapshot = withContext(dbDispatcher) { dbHelper.getStats() }
                _stats.value = statsSnapshot
            }
        }
    }

    fun startAllItemsDeck() {
        _typeFilter.value = MediaTypeFilter.ALL
        _sortOrder.value = MediaSortOrder.NEWEST
        _activeDeckTitle.value = "All items"
        loadData(groupKey = "ALL", forceLibraryScan = false, showSpinner = false)
    }

    fun startFilteredAllDeck(
        filter: MediaTypeFilter = MediaTypeFilter.ALL,
        sort: MediaSortOrder = MediaSortOrder.NEWEST,
        title: String
    ) {
        _typeFilter.value = filter
        _sortOrder.value = sort
        _activeDeckTitle.value = title
        loadData(groupKey = "ALL", forceLibraryScan = false, showSpinner = false)
    }

    fun startMonthDeck(monthKey: String, monthDisplay: String) {
        _typeFilter.value = MediaTypeFilter.ALL
        _sortOrder.value = MediaSortOrder.NEWEST
        _activeDeckTitle.value = monthDisplay
        loadData(groupKey = monthKey, forceLibraryScan = false, showSpinner = false)
    }

    fun startQuickCleanDeck(category: QuickCleanCategory) {
        _typeFilter.value = MediaTypeFilter.ALL
        _sortOrder.value = if (category == QuickCleanCategory.LARGE_VIDEOS) {
            MediaSortOrder.LARGEST
        } else {
            MediaSortOrder.NEWEST
        }
        _activeDeckTitle.value = category.title
        loadData(
            groupKey = category.key,
            forceLibraryScan = false,
            showSpinner = category == QuickCleanCategory.SIMILAR
        )
    }

    fun startOnThisDayDeck() {
        _typeFilter.value = MediaTypeFilter.ALL
        _sortOrder.value = MediaSortOrder.NEWEST
        _activeDeckTitle.value = "On this day"
        loadData(groupKey = "ON_THIS_DAY", forceLibraryScan = false, showSpinner = false)
    }

    fun updateDeckFilterAndSort(
        filter: MediaTypeFilter = _typeFilter.value,
        sort: MediaSortOrder = _sortOrder.value
    ) {
        _typeFilter.value = filter
        _sortOrder.value = sort
        recomputeActiveDeckFast()
    }

    fun clearDeckFilter() {
        _typeFilter.value = MediaTypeFilter.ALL
        _sortOrder.value = MediaSortOrder.NEWEST
        recomputeActiveDeckFast()
    }

    private fun recomputeActiveDeckFast() {
        viewModelScope.launch {
            val allItems = repository.getLibrary(forceRefresh = false)
            val stagedKeys = _stagedForDeletion.value.map { it.mediaKey }.toSet()
            val excluded = cachedReviewedKeys + stagedKeys
            val deck = repository.computeDeck(
                allItems = allItems,
                groupKey = _activeDeckKey.value,
                reviewedKeys = excluded,
                typeFilter = _typeFilter.value,
                sortOrder = _sortOrder.value
            )
            _mediaDeck.value = deck
            inFlightKeys.clear()
        }
    }

    fun swipeRight(item: MediaItem) {
        val key = item.mediaKey
        if (!inFlightKeys.add(key) || _mediaDeck.value.none { it.mediaKey == key }) return

        sessionActionStack.push(UndoStackItem(key, SwipeCleanDbHelper.STATUS_KEPT, isStagedOnly = false, payload = item))
        _mediaDeck.value = _mediaDeck.value.filter { it.mediaKey != key }
        cachedReviewedKeys.add(key)
        adjustMonthProgress(item.monthYearKey, +1)
        adjustUnreviewedSummary(-1, -item.sizeBytes, item.isVideo)
        removeFromDerivedQuickLists(key)
        _canUndo.value = true

        if (_mediaDeck.value.isEmpty() && _stagedForDeletion.value.isNotEmpty()) {
            _showReviewDeletions.value = true
        }

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.markKept(item)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
        }
    }

    fun swipeLeft(item: MediaItem) {
        val key = item.mediaKey
        if (!inFlightKeys.add(key) || _mediaDeck.value.none { it.mediaKey == key }) return

        sessionActionStack.push(UndoStackItem(key, SwipeCleanDbHelper.STATUS_TRASHED, isStagedOnly = true, payload = item))
        _mediaDeck.value = _mediaDeck.value.filter { it.mediaKey != key }
        _stagedForDeletion.value = listOf(item) + _stagedForDeletion.value
        adjustUnreviewedSummary(-1, -item.sizeBytes, item.isVideo)
        removeFromDerivedQuickLists(key)
        _canUndo.value = true

        if (_mediaDeck.value.isEmpty()) {
            _showReviewDeletions.value = true
        }
    }

    fun swipeLater(item: MediaItem) {
        val key = item.mediaKey
        if (!inFlightKeys.add(key) || _mediaDeck.value.none { it.mediaKey == key }) return

        sessionActionStack.push(UndoStackItem(key, SwipeCleanDbHelper.STATUS_LATER, isStagedOnly = false, payload = item))
        _mediaDeck.value = _mediaDeck.value.filter { it.mediaKey != key }
        _laterList.value = listOf(item) + _laterList.value
        cachedReviewedKeys.add(key)
        adjustMonthProgress(item.monthYearKey, +1)
        adjustUnreviewedSummary(-1, -item.sizeBytes, item.isVideo)
        removeFromDerivedQuickLists(key)
        _canUndo.value = true

        if (_mediaDeck.value.isEmpty() && _stagedForDeletion.value.isNotEmpty()) {
            _showReviewDeletions.value = true
        }

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.markLater(item)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
        }
    }

    fun openReviewDeletionsScreen() {
        if (_stagedForDeletion.value.isNotEmpty()) {
            _showReviewDeletions.value = true
        }
    }

    fun closeReviewDeletionsScreen() {
        _showReviewDeletions.value = false
    }

    fun commitStagedDeletions(finalDeletions: List<MediaItem>) {
        val stagedSnapshot = _stagedForDeletion.value
        if (stagedSnapshot.isEmpty()) {
            _showReviewDeletions.value = false
            return
        }

        val finalKeys = finalDeletions.map { it.mediaKey }.toSet()
        val keptFromStaged = stagedSnapshot.filter { !finalKeys.contains(it.mediaKey) }
        val allStagedKeys = stagedSnapshot.map { it.mediaKey }.toSet()

        sessionActionStack.removeStagedForKeys(allStagedKeys)
        _stagedForDeletion.value = emptyList()
        _showReviewDeletions.value = false

        val nowMs = System.currentTimeMillis()
        _trashedList.value = finalDeletions.map { TrashedItem(it, nowMs) } + _trashedList.value

        for (item in stagedSnapshot) {
            cachedReviewedKeys.add(item.mediaKey)
            adjustMonthProgress(item.monthYearKey, +1)
        }

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.commitReviewBatch(keptItems = keptFromStaged, trashedItems = finalDeletions)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
        }
    }

    fun undo() {
        val lastAction = sessionActionStack.popOrNull()
        if (lastAction != null) {
            val item = lastAction.payload
            val key = lastAction.key
            inFlightKeys.remove(key)

            if (lastAction.isStagedOnly) {
                _stagedForDeletion.value = _stagedForDeletion.value.filter { it.mediaKey != key }
                _mediaDeck.value = listOf(item) + _mediaDeck.value
                _showReviewDeletions.value = false
                adjustUnreviewedSummary(+1, item.sizeBytes, item.isVideo)
                refreshQuickDerivedGroupsFromLibrary()
                viewModelScope.launch {
                    val hasDbUndo = withContext(dbDispatcher) { dbHelper.hasUndoHistory() }
                    _canUndo.value = sessionActionStack.isNotEmpty() || hasDbUndo
                }
                return
            }

            when (lastAction.actionType) {
                SwipeCleanDbHelper.STATUS_TRASHED -> {
                    _trashedList.value = _trashedList.value.filter { it.mediaItem.mediaKey != key }
                }
                SwipeCleanDbHelper.STATUS_LATER -> {
                    _laterList.value = _laterList.value.filter { it.mediaKey != key }
                }
            }
            cachedReviewedKeys.remove(key)
            adjustMonthProgress(item.monthYearKey, -1)
            adjustUnreviewedSummary(+1, item.sizeBytes, item.isVideo)
            _mediaDeck.value = listOf(item) + _mediaDeck.value
            _showReviewDeletions.value = false
            refreshQuickDerivedGroupsFromLibrary()

            viewModelScope.launch {
                val (updatedStats, hasDbUndo) = withContext(dbDispatcher) {
                    dbHelper.restoreItem(key)
                    dbHelper.getStats() to dbHelper.hasUndoHistory()
                }
                _stats.value = updatedStats
                _canUndo.value = sessionActionStack.isNotEmpty() || hasDbUndo
            }
            return
        }

        viewModelScope.launch {
            val popped = withContext(dbDispatcher) { dbHelper.popUndo() }
            if (popped == null) {
                _canUndo.value = false
                return@launch
            }

            val key = popped.mediaKey
            inFlightKeys.remove(key)
            cachedReviewedKeys.remove(key)

            val allItems = repository.getLibrary(forceRefresh = false)
            val restoredItem = repository.findByKey(key)
                ?: allItems.firstOrNull { it.mediaKey == key }

            when (popped.action) {
                SwipeCleanDbHelper.STATUS_TRASHED -> {
                    _trashedList.value = _trashedList.value.filter { it.mediaItem.mediaKey != key }
                }
                SwipeCleanDbHelper.STATUS_LATER -> {
                    _laterList.value = _laterList.value.filter { it.mediaKey != key }
                }
            }

            if (restoredItem != null) {
                if (_mediaDeck.value.none { it.mediaKey == key }) {
                    _mediaDeck.value = listOf(restoredItem) + _mediaDeck.value
                }
                adjustMonthProgress(restoredItem.monthYearKey, -1)
                adjustUnreviewedSummary(+1, restoredItem.sizeBytes, restoredItem.isVideo)
            }

            val (updatedStats, hasDbUndo) = withContext(dbDispatcher) {
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasDbUndo
            refreshQuickDerivedGroupsFromLibrary()
        }
    }

    fun keepFromLater(item: MediaItem) {
        val key = item.mediaKey
        _laterList.value = _laterList.value.filter { it.mediaKey != key }
        cachedReviewedKeys.add(key)

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.markKept(item)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
        }
    }

    fun trashFromLater(item: MediaItem) {
        val key = item.mediaKey
        _laterList.value = _laterList.value.filter { it.mediaKey != key }
        _trashedList.value = listOf(TrashedItem(item, System.currentTimeMillis())) + _trashedList.value
        cachedReviewedKeys.add(key)

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.markTrashed(item)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
        }
    }

    fun restoreFromBin(trashedItem: TrashedItem) {
        val item = trashedItem.mediaItem
        val key = item.mediaKey
        inFlightKeys.remove(key)
        cachedReviewedKeys.remove(key)
        _trashedList.value = _trashedList.value.filter { it.mediaItem.mediaKey != key }
        sessionActionStack.removeByKey(key)
        adjustMonthProgress(item.monthYearKey, -1)
        adjustUnreviewedSummary(+1, item.sizeBytes, item.isVideo)

        if (itemMatchesActiveDeck(item) && _mediaDeck.value.none { it.mediaKey == key }) {
            _mediaDeck.value = listOf(item) + _mediaDeck.value
        }

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.restoreItem(key)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
            refreshQuickDerivedGroupsFromLibrary()
        }
    }

    fun restoreAllFromBin() {
        val currentTrashed = _trashedList.value
        if (currentTrashed.isEmpty()) return

        val keys = currentTrashed.map { it.mediaItem.mediaKey }
        for (item in currentTrashed) {
            val key = item.mediaItem.mediaKey
            inFlightKeys.remove(key)
            cachedReviewedKeys.remove(key)
        }
        _trashedList.value = emptyList()

        viewModelScope.launch {
            val (updatedStats, hasUndo) = withContext(dbDispatcher) {
                dbHelper.restoreAllKeys(keys)
                dbHelper.getStats() to dbHelper.hasUndoHistory()
            }
            _stats.value = updatedStats
            _canUndo.value = sessionActionStack.isNotEmpty() || hasUndo
            loadData(groupKey = _activeDeckKey.value, forceLibraryScan = false, showSpinner = false)
        }
    }

    fun requestPermanentDeleteLegacy(
        batchItems: List<MediaItem>,
        remainingAfterBatch: Int
    ) {
        viewModelScope.launch {
            val result = repository.deletePermanentlyLegacy(batchItems)
            onPermanentDeletionCompleted(
                deletedItems = result.deletedItems,
                remainingBatchCount = remainingAfterBatch,
                failedCount = result.failedCount
            )
        }
    }

    fun onPermanentDeletionCompleted(
        deletedItems: List<MediaItem>,
        remainingBatchCount: Int = 0,
        failedCount: Int = 0
    ) {
        viewModelScope.launch {
            val deletedKeys = deletedItems.map { it.mediaKey }.toSet()
            withContext(dbDispatcher) {
                dbHelper.recordPermanentDeletion(deletedItems)
            }
            _trashedList.value = _trashedList.value.filter { !deletedKeys.contains(it.mediaItem.mediaKey) }

            DeletionBatcher.formatDeletionFeedback(
                deletedCount = deletedItems.size,
                failedCount = failedCount,
                remainingBatchCount = remainingBatchCount
            )?.let { message ->
                _toastEvents.tryEmit(message)
            }

            loadData(groupKey = _activeDeckKey.value, forceLibraryScan = true, showSpinner = false)
        }
    }

    fun createDeletePendingIntent(uris: List<Uri>) = repository.createDeletePendingIntent(uris)

    fun resetKeptHistory() {
        sessionActionStack.removeByActionType(SwipeCleanDbHelper.STATUS_KEPT)
        viewModelScope.launch {
            withContext(dbDispatcher) {
                dbHelper.resetKeptHistory()
            }
            loadData(groupKey = _activeDeckKey.value, forceLibraryScan = false, showSpinner = false)
        }
    }

    fun setInspectItem(item: MediaItem?) {
        _inspectItem.value = item
    }

    private fun adjustMonthProgress(monthYearKey: String, delta: Int) {
        if (delta == 0) return
        _monthProgressList.value = _monthProgressList.value.map { month ->
            if (month.monthKey == monthYearKey) {
                val updated = (month.reviewedCount + delta).coerceIn(0, month.totalCount)
                month.copy(reviewedCount = updated)
            } else {
                month
            }
        }
    }

    private fun adjustUnreviewedSummary(countDelta: Int, bytesDelta: Long, isVideo: Boolean) {
        val current = _unreviewedSummary.value
        val nextPhotos = if (isVideo) current.photoCount else maxOf(0, current.photoCount + countDelta)
        val nextVideos = if (isVideo) maxOf(0, current.videoCount + countDelta) else current.videoCount
        _unreviewedSummary.value = AllLibrarySummary(
            count = maxOf(0, current.count + countDelta),
            photoCount = nextPhotos,
            videoCount = nextVideos,
            totalSizeBytes = maxOf(0L, current.totalSizeBytes + bytesDelta)
        )
    }

    private fun removeFromDerivedQuickLists(key: MediaKey) {
        _onThisDayList.value = _onThisDayList.value.filter { it.mediaKey != key }
        refreshQuickDerivedGroupsFromLibrary()
    }

    private fun refreshQuickDerivedGroupsFromLibrary() {
        viewModelScope.launch {
            val allItems = repository.getLibrary(forceRefresh = false)
            val stagedKeys = _stagedForDeletion.value.map { it.mediaKey }.toSet()
            val excluded = cachedReviewedKeys + stagedKeys
            val unreviewed = allItems.filter { !excluded.contains(it.mediaKey) }
            _onThisDayList.value = repository.buildOnThisDay(unreviewed)

            val existingSimilar = _quickCleanGroups.value.firstOrNull {
                it.category == QuickCleanCategory.SIMILAR
            }
            val fast = repository.buildFastQuickCleanGroups(unreviewed)
            _quickCleanGroups.value = if (existingSimilar != null) {
                listOf(existingSimilar) + fast
            } else {
                fast
            }
        }
    }

    private fun itemMatchesActiveDeck(item: MediaItem): Boolean {
        val matchesTypeFilter = when (_typeFilter.value) {
            MediaTypeFilter.ALL -> true
            MediaTypeFilter.PHOTOS -> !item.isVideo
            MediaTypeFilter.VIDEOS -> item.isVideo
        }
        if (!matchesTypeFilter) return false

        return when (val key = _activeDeckKey.value) {
            null, "", "ALL" -> true
            QuickCleanCategory.LARGE_VIDEOS.key -> item.isVideo && item.sizeBytes >= 40L * 1024L * 1024L
            QuickCleanCategory.SCREENSHOTS.key -> !item.isVideo &&
                (item.bucketName.contains("screenshot", ignoreCase = true) ||
                    item.displayName.contains("screenshot", ignoreCase = true))
            QuickCleanCategory.SIMILAR.key, "ON_THIS_DAY" -> false
            else -> item.monthYearKey == key
        }
    }

    private data class SnapshotTuple(
        val reviewed: Set<MediaKey>,
        val later: List<MediaKey>,
        val trashed: Map<MediaKey, Long>,
        val stats: CleanStats,
        val hasUndo: Boolean
    )
}

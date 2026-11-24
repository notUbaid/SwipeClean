package com.antigravity.swipeclean

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.swipeclean.data.db.SwipeCleanDbHelper
import com.antigravity.swipeclean.data.model.AlbumGroup
import com.antigravity.swipeclean.data.model.CleanStats
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.data.model.MonthProgress
import com.antigravity.swipeclean.data.model.SwipeAction
import com.antigravity.swipeclean.data.model.TrashedItem
import com.antigravity.swipeclean.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val dbHelper = SwipeCleanDbHelper.getInstance(application)

    private val _mediaDeck = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaDeck: StateFlow<List<MediaItem>> = _mediaDeck.asStateFlow()

    private val _monthProgressList = MutableStateFlow<List<MonthProgress>>(emptyList())
    val monthProgressList: StateFlow<List<MonthProgress>> = _monthProgressList.asStateFlow()

    private val _onThisDayList = MutableStateFlow<List<MediaItem>>(emptyList())
    val onThisDayList: StateFlow<List<MediaItem>> = _onThisDayList.asStateFlow()

    private val _bookmarkedList = MutableStateFlow<List<MediaItem>>(emptyList())
    val bookmarkedList: StateFlow<List<MediaItem>> = _bookmarkedList.asStateFlow()

    private val _trashedList = MutableStateFlow<List<TrashedItem>>(emptyList())
    val trashedList: StateFlow<List<TrashedItem>> = _trashedList.asStateFlow()

    private val _albumGroups = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albumGroups: StateFlow<List<AlbumGroup>> = _albumGroups.asStateFlow()

    private val _selectedGroup = MutableStateFlow<AlbumGroup?>(null)
    val selectedGroup: StateFlow<AlbumGroup?> = _selectedGroup.asStateFlow()

    private val _activeDeckTitle = MutableStateFlow("All Photos & Videos")
    val activeDeckTitle: StateFlow<String> = _activeDeckTitle.asStateFlow()

    // Items staged for deletion in current session before final review
    private val _stagedForDeletion = MutableStateFlow<List<MediaItem>>(emptyList())
    val stagedForDeletion: StateFlow<List<MediaItem>> = _stagedForDeletion.asStateFlow()

    private val _showReviewDeletions = MutableStateFlow(false)
    val showReviewDeletions: StateFlow<Boolean> = _showReviewDeletions.asStateFlow()

    private val _stats = MutableStateFlow(CleanStats(0, 0, 0, 0, 0L))
    val stats: StateFlow<CleanStats> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _inspectItem = MutableStateFlow<MediaItem?>(null)
    val inspectItem: StateFlow<MediaItem?> = _inspectItem.asStateFlow()

    private val mediaCache = mutableMapOf<Long, MediaItem>()
    private val sessionActionStack = java.util.ArrayDeque<SwipeAction>()

    fun loadData(groupKey: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true

            val deck = repository.loadAllMedia(groupKey)
            deck.forEach { mediaCache[it.id] = it }
            _mediaDeck.value = deck

            val months = repository.loadMonthProgressList()
            _monthProgressList.value = months

            val onThisDay = repository.loadOnThisDayMedia()
            _onThisDayList.value = onThisDay

            val bookmarks = repository.loadBookmarkedMedia()
            _bookmarkedList.value = bookmarks

            val trashed = repository.loadTrashedItems()
            trashed.forEach { mediaCache[it.mediaItem.id] = it.mediaItem }
            _trashedList.value = trashed

            val groups = repository.loadAlbumGroups()
            _albumGroups.value = groups

            if (_selectedGroup.value == null && groups.isNotEmpty()) {
                _selectedGroup.value = groups.first()
            }

            _stats.value = dbHelper.getStats()
            _isLoading.value = false
        }
    }

    fun startMonthDeck(monthKey: String, monthDisplay: String) {
        _activeDeckTitle.value = monthDisplay
        loadData(monthKey)
    }

    fun startOnThisDayDeck() {
        _activeDeckTitle.value = "On This Day"
        viewModelScope.launch {
            _isLoading.value = true
            val items = repository.loadOnThisDayMedia()
            items.forEach { mediaCache[it.id] = it }
            _mediaDeck.value = items
            _isLoading.value = false
        }
    }

    fun selectGroup(group: AlbumGroup) {
        _selectedGroup.value = group
        _activeDeckTitle.value = group.displayName
        loadData(group.key)
    }

    fun swipeRight(item: MediaItem) {
        viewModelScope.launch {
            sessionActionStack.addFirst(SwipeAction(item, SwipeCleanDbHelper.STATUS_KEPT, System.currentTimeMillis()))
            dbHelper.markKept(item)
            _mediaDeck.value = _mediaDeck.value.filter { it.id != item.id }
            _canUndo.value = true
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()

            if (_mediaDeck.value.isEmpty() && _stagedForDeletion.value.isNotEmpty()) {
                _showReviewDeletions.value = true
            }
        }
    }

    fun swipeLeft(item: MediaItem) {
        sessionActionStack.addFirst(SwipeAction(item, SwipeCleanDbHelper.STATUS_TRASHED, System.currentTimeMillis()))
        _mediaDeck.value = _mediaDeck.value.filter { it.id != item.id }
        _stagedForDeletion.value = listOf(item) + _stagedForDeletion.value
        _canUndo.value = true

        if (_mediaDeck.value.isEmpty()) {
            _showReviewDeletions.value = true
        }
    }

    fun swipeUpBookmark(item: MediaItem) {
        viewModelScope.launch {
            sessionActionStack.addFirst(SwipeAction(item, SwipeCleanDbHelper.STATUS_BOOKMARKED, System.currentTimeMillis()))
            dbHelper.markBookmarked(item)
            _mediaDeck.value = _mediaDeck.value.filter { it.id != item.id }
            _bookmarkedList.value = listOf(item) + _bookmarkedList.value
            _canUndo.value = true
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
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
        viewModelScope.launch {
            val finalIds = finalDeletions.map { it.id }.toSet()
            val unselectedItems = _stagedForDeletion.value.filter { !finalIds.contains(it.id) }
            for (unselected in unselectedItems) {
                dbHelper.markKept(unselected)
            }

            for (del in finalDeletions) {
                dbHelper.markTrashed(del)
            }

            _trashedList.value = finalDeletions.map { TrashedItem(it, System.currentTimeMillis()) } + _trashedList.value
            _stagedForDeletion.value = emptyList()
            sessionActionStack.clear()
            _showReviewDeletions.value = false
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun undo() {
        viewModelScope.launch {
            if (sessionActionStack.isNotEmpty()) {
                val lastAction: SwipeAction = sessionActionStack.removeFirst()
                val item = lastAction.item

                when (lastAction.actionType) {
                    SwipeCleanDbHelper.STATUS_TRASHED -> {
                        _stagedForDeletion.value = _stagedForDeletion.value.filter { it.id != item.id }
                        _trashedList.value = _trashedList.value.filter { it.mediaItem.id != item.id }
                        dbHelper.restoreItem(item.id)
                    }
                    SwipeCleanDbHelper.STATUS_KEPT -> {
                        dbHelper.restoreItem(item.id)
                    }
                    SwipeCleanDbHelper.STATUS_BOOKMARKED -> {
                        dbHelper.removeBookmark(item.id)
                        _bookmarkedList.value = _bookmarkedList.value.filter { it.id != item.id }
                    }
                }

                _mediaDeck.value = listOf(item) + _mediaDeck.value
                _canUndo.value = sessionActionStack.isNotEmpty()
                _stats.value = dbHelper.getStats()
                refreshMonthProgress()
                return@launch
            }

            // Fallback to database undo if session stack is exhausted
            val popped = dbHelper.popUndo() ?: return@launch
            val (mediaId, action) = popped
            val item = mediaCache[mediaId]

            if (item != null) {
                _mediaDeck.value = listOf(item) + _mediaDeck.value
            }

            when (action) {
                SwipeCleanDbHelper.STATUS_TRASHED -> {
                    _trashedList.value = _trashedList.value.filter { it.mediaItem.id != mediaId }
                }
                SwipeCleanDbHelper.STATUS_BOOKMARKED -> {
                    _bookmarkedList.value = _bookmarkedList.value.filter { it.id != mediaId }
                }
            }

            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun keepFromBookmark(item: MediaItem) {
        viewModelScope.launch {
            dbHelper.removeBookmark(item.id)
            dbHelper.markKept(item)
            _bookmarkedList.value = _bookmarkedList.value.filter { it.id != item.id }
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun trashFromBookmark(item: MediaItem) {
        viewModelScope.launch {
            dbHelper.removeBookmark(item.id)
            dbHelper.markTrashed(item)
            _bookmarkedList.value = _bookmarkedList.value.filter { it.id != item.id }
            _trashedList.value = listOf(TrashedItem(item, System.currentTimeMillis())) + _trashedList.value
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun restoreFromBin(trashedItem: TrashedItem) {
        viewModelScope.launch {
            val id = trashedItem.mediaItem.id
            dbHelper.restoreItem(id)
            _trashedList.value = _trashedList.value.filter { it.mediaItem.id != id }
            _mediaDeck.value = listOf(trashedItem.mediaItem) + _mediaDeck.value
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun restoreAllFromBin() {
        viewModelScope.launch {
            val currentTrashed = _trashedList.value
            for (item in currentTrashed) {
                dbHelper.restoreItem(item.mediaItem.id)
            }
            _mediaDeck.value = currentTrashed.map { it.mediaItem } + _mediaDeck.value
            _trashedList.value = emptyList()
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun onPermanentDeletionComplete(deletedIds: Collection<Long>) {
        viewModelScope.launch {
            dbHelper.deletePermanentlyAll(deletedIds)
            _trashedList.value = _trashedList.value.filter { !deletedIds.contains(it.mediaItem.id) }
            _stats.value = dbHelper.getStats()
            refreshMonthProgress()
        }
    }

    fun resetKeptHistory() {
        viewModelScope.launch {
            dbHelper.resetKeptHistory()
            loadData(_selectedGroup.value?.key)
        }
    }

    private fun refreshMonthProgress() {
        viewModelScope.launch {
            _monthProgressList.value = repository.loadMonthProgressList()
        }
    }

    fun setInspectItem(item: MediaItem?) {
        _inspectItem.value = item
    }
}

package com.antigravity.swipeclean.util

import com.antigravity.swipeclean.data.model.MediaKey

data class UndoStackItem<T>(
    val key: MediaKey,
    val actionType: String,
    val isStagedOnly: Boolean,
    val payload: T
)

class SwipeUndoStack<T>(private val maxSize: Int = 200) {

    private val entries = ArrayDeque<UndoStackItem<T>>()

    val size: Int get() = entries.size
    fun isEmpty(): Boolean = entries.isEmpty()
    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    fun push(item: UndoStackItem<T>) {
        entries.addFirst(item)
        while (entries.size > maxSize) {
            entries.removeLast()
        }
    }

    fun popOrNull(): UndoStackItem<T>? = if (entries.isEmpty()) null else entries.removeFirst()

    fun removeStagedForKeys(keys: Set<MediaKey>) {
        if (keys.isEmpty()) return
        entries.removeAll { it.isStagedOnly && keys.contains(it.key) }
    }

    fun removeByActionType(actionType: String) {
        entries.removeAll { it.actionType == actionType }
    }

    fun removeByKey(key: MediaKey) {
        entries.removeAll { it.key == key }
    }
}

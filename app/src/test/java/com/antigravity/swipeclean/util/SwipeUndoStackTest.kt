package com.antigravity.swipeclean.util

import com.antigravity.swipeclean.data.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeUndoStackTest {

    private fun entry(
        id: Long,
        action: String,
        staged: Boolean,
        isVideo: Boolean = false
    ): UndoStackItem<String> = UndoStackItem(
        key = MediaKey(id, isVideo),
        actionType = action,
        isStagedOnly = staged,
        payload = "${if (isVideo) 'v' else 'i'}_$id"
    )

    @Test
    fun chronologicalPop_reversesInsertionOrderAcrossActions() {
        val stack = SwipeUndoStack<String>()
        val a = entry(1L, "KEPT", staged = false, isVideo = false)
        val b = entry(1L, "TRASHED", staged = true, isVideo = true) // same id, different type
        val c = entry(2L, "LATER", staged = false, isVideo = false)

        stack.push(a)
        stack.push(b)
        stack.push(c)

        assertEquals(3, stack.size)
        assertEquals(c.key, stack.popOrNull()?.key)
        assertEquals(b.key, stack.popOrNull()?.key)
        assertEquals(a.key, stack.popOrNull()?.key)
        assertNull(stack.popOrNull())
        assertTrue(stack.isEmpty())
    }

    @Test
    fun removeStagedForKeys_preservesCommittedHistoryAndDistinguishesImageVsVideo() {
        val stack = SwipeUndoStack<String>()
        val keptImg = entry(10L, "KEPT", staged = false, isVideo = false)
        val stagedVid = entry(10L, "TRASHED", staged = true, isVideo = true)

        stack.push(keptImg)
        stack.push(stagedVid)
        stack.removeStagedForKeys(setOf(stagedVid.key))

        assertEquals(1, stack.size)
        val remaining = stack.popOrNull()
        assertEquals(keptImg.key, remaining?.key)
        assertFalse(remaining?.isStagedOnly ?: true)
    }

    @Test
    fun removeByActionType_stripsOnlyKeptWhenHistoryReset() {
        val stack = SwipeUndoStack<String>()
        stack.push(entry(1L, "KEPT", staged = false))
        stack.push(entry(2L, "TRASHED", staged = true))
        stack.push(entry(3L, "KEPT", staged = false))

        stack.removeByActionType("KEPT")

        assertEquals(1, stack.size)
        assertEquals(2L, stack.popOrNull()?.key?.id)
    }

    @Test
    fun removeByKey_dropsOnlyMatchingMediaKey() {
        val stack = SwipeUndoStack<String>()
        val img = entry(7L, "TRASHED", staged = false, isVideo = false)
        val vid = entry(7L, "KEPT", staged = false, isVideo = true)
        stack.push(img)
        stack.push(vid)

        stack.removeByKey(img.key)

        assertEquals(1, stack.size)
        assertEquals(vid.key, stack.popOrNull()?.key)
    }

    @Test
    fun stackIsBoundedToMaxSize_evictsOldestFirst() {
        val stack = SwipeUndoStack<String>(maxSize = 3)
        for (i in 1L..5L) {
            stack.push(entry(i, "KEPT", staged = false))
        }

        assertEquals(3, stack.size)
        assertEquals(5L, stack.popOrNull()?.key?.id)
        assertEquals(4L, stack.popOrNull()?.key?.id)
        assertEquals(3L, stack.popOrNull()?.key?.id)
        assertNull(stack.popOrNull())
    }
}

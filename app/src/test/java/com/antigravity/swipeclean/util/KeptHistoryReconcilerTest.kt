package com.antigravity.swipeclean.util

import com.antigravity.swipeclean.data.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeptHistoryReconcilerTest {

    private fun candidate(
        id: Long,
        name: String,
        size: Long = 2048L,
        isVideo: Boolean = false
    ): MediaIdentity = MediaIdentity(
        key = MediaKey(id, isVideo),
        uriString = "content://media/test/$id",
        displayName = name,
        isVideo = isVideo,
        sizeBytes = size,
        fingerprint = MediaFingerprint.build(isVideo, size, name)
    )

    @Test
    fun upgradeBackfillsFingerprintsAndDiscoversSignaturesForPreV4Rows() {
        val item = candidate(id = 10L, name = "IMG_010.jpg", size = 5000L)
        val rows = listOf(
            ReviewedRow(
                key = MediaKey(10L, false),
                status = KeptHistoryReconciler.STATUS_KEPT,
                swipedAtMs = 1234L,
                sizeBytes = 5000L,
                fingerprint = ""
            )
        )

        val plan = KeptHistoryReconciler.plan(
            libraryItems = listOf(item),
            reviewedRows = rows,
            keptSignatures = emptyMap()
        )

        assertTrue(plan.hasDbMutations)
        assertEquals(item.fingerprint, plan.backfillFingerprints[item.key])
        assertEquals(1, plan.discoveredSignatures.size)
        assertEquals("IMG_010.jpg", plan.discoveredSignatures[0].displayName)
        assertTrue(plan.newKeptLinks.isEmpty())
        assertTrue(plan.effectiveReviewedKeys.contains(item.key))
    }

    @Test
    fun relinksRelocatedFileAndRemovesOrphanOldKeptKey() {
        val oldKey = MediaKey(10L, false)
        val newItem = candidate(id = 999L, name = "IMG_010.jpg", size = 5000L)
        val rows = listOf(
            ReviewedRow(
                key = oldKey,
                status = KeptHistoryReconciler.STATUS_KEPT,
                swipedAtMs = 1234L,
                sizeBytes = 5000L,
                fingerprint = newItem.fingerprint
            )
        )
        val sigs = mapOf(
            newItem.fingerprint to KeptMediaRecord(newItem.fingerprint, "IMG_010.jpg", false, 5000L, 1234L)
        )

        val plan = KeptHistoryReconciler.plan(
            libraryItems = listOf(newItem),
            reviewedRows = rows,
            keptSignatures = sigs
        )

        assertEquals(listOf(newItem.key), plan.newKeptLinks.map { it.identity.key })
        assertEquals(setOf(oldKey), plan.staleKeptKeysToRemove)
        assertTrue(plan.effectiveReviewedKeys.contains(newItem.key))
        assertFalse(plan.effectiveReviewedKeys.contains(oldKey))
    }

    @Test
    fun matchesRestoredBackupAfterUninstallWithoutOverwritingTrashedItems() {
        val restoredKept = candidate(id = 42L, name = "KeepMe.jpg", size = 8000L)
        val userTrashed = candidate(id = 43L, name = "KeepMe.jpg", size = 8000L)
        val unreviewed = candidate(id = 44L, name = "NewPhoto.jpg", size = 9000L)

        val sigs = mapOf(
            restoredKept.fingerprint to KeptMediaRecord(restoredKept.fingerprint, "KeepMe.jpg", false, 8000L, 777L)
        )
        val rows = listOf(
            ReviewedRow(
                key = userTrashed.key,
                status = "TRASHED",
                swipedAtMs = 999L,
                sizeBytes = 8000L,
                fingerprint = userTrashed.fingerprint
            )
        )

        val plan = KeptHistoryReconciler.plan(
            libraryItems = listOf(restoredKept, userTrashed, unreviewed),
            reviewedRows = rows,
            keptSignatures = sigs
        )

        assertEquals(listOf(restoredKept.key), plan.newKeptLinks.map { it.identity.key })
        assertTrue(plan.effectiveReviewedKeys.contains(restoredKept.key))
        assertTrue(plan.effectiveReviewedKeys.contains(userTrashed.key))
        assertFalse(plan.effectiveReviewedKeys.contains(unreviewed.key))
    }
}

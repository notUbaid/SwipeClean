package com.antigravity.swipeclean.util

import com.antigravity.swipeclean.data.model.MediaKey

data class MediaIdentity(
    val key: MediaKey,
    val uriString: String,
    val displayName: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val fingerprint: String
)

data class ReviewedRow(
    val key: MediaKey,
    val status: String,
    val swipedAtMs: Long,
    val sizeBytes: Long,
    val fingerprint: String
)

data class KeptLinkWrite(
    val identity: MediaIdentity,
    val keptAtMs: Long
)

data class ReconciliationPlan(
    val backfillFingerprints: Map<MediaKey, String>,
    val discoveredSignatures: List<KeptMediaRecord>,
    val staleKeptKeysToRemove: Set<MediaKey>,
    val newKeptLinks: List<KeptLinkWrite>,
    val effectiveReviewedKeys: Set<MediaKey>
) {
    val hasDbMutations: Boolean
        get() = backfillFingerprints.isNotEmpty() ||
            discoveredSignatures.isNotEmpty() ||
            staleKeptKeysToRemove.isNotEmpty() ||
            newKeptLinks.isNotEmpty()
}

object KeptHistoryReconciler {

    const val STATUS_KEPT = "KEPT"

    /**
     * Reconciles active MediaStore items against local reviewed rows and durable kept
     * content signatures:
     * 1. Backfills fingerprints onto rows upgraded from pre-v4 databases.
     * 2. Seeds missing entries in the durable kept-signatures store.
     * 3. Re-links moved/re-indexed/restored kept media to their current MediaStore IDs
     *    and prunes orphaned old kept IDs for those relocated files.
     */
    fun plan(
        libraryItems: List<MediaIdentity>,
        reviewedRows: List<ReviewedRow>,
        keptSignatures: Map<String, KeptMediaRecord>
    ): ReconciliationPlan {
        val libraryByKey = HashMap<MediaKey, MediaIdentity>(libraryItems.size)
        for (item in libraryItems) {
            libraryByKey[item.key] = item
        }

        val reviewedByKey = HashMap<MediaKey, ReviewedRow>(reviewedRows.size)
        for (row in reviewedRows) {
            reviewedByKey[row.key] = row
        }

        val backfillFingerprints = LinkedHashMap<MediaKey, String>()
        val discoveredSignaturesMap = LinkedHashMap<String, KeptMediaRecord>()
        val orphanKeptRowsByFingerprint = LinkedHashMap<String, MutableList<MediaKey>>()

        for (row in reviewedRows) {
            val item = libraryByKey[row.key]
            val resolvedFp = if (row.fingerprint.isNotBlank()) {
                row.fingerprint
            } else {
                val computed = item?.fingerprint.orEmpty()
                if (computed.isNotBlank()) {
                    backfillFingerprints[row.key] = computed
                }
                computed
            }

            if (row.status == STATUS_KEPT && resolvedFp.isNotBlank()) {
                if (!keptSignatures.containsKey(resolvedFp) && !discoveredSignaturesMap.containsKey(resolvedFp)) {
                    discoveredSignaturesMap[resolvedFp] = KeptMediaRecord(
                        fingerprint = resolvedFp,
                        displayName = item?.displayName ?: "",
                        isVideo = row.key.isVideo,
                        sizeBytes = maxOf(row.sizeBytes, item?.sizeBytes ?: 0L),
                        keptAtMs = row.swipedAtMs
                    )
                }
                if (item == null) {
                    orphanKeptRowsByFingerprint.getOrPut(resolvedFp) { mutableListOf() }.add(row.key)
                }
            }
        }

        val newKeptLinks = ArrayList<KeptLinkWrite>()
        val staleKeptKeysToRemove = LinkedHashSet<MediaKey>()
        val effectiveReviewedKeys = HashSet<MediaKey>(reviewedRows.size + 16)

        for (row in reviewedRows) {
            effectiveReviewedKeys.add(row.key)
        }

        for (item in libraryItems) {
            if (reviewedByKey.containsKey(item.key)) continue
            val fp = item.fingerprint
            if (fp.isBlank()) continue

            val signature = keptSignatures[fp] ?: discoveredSignaturesMap[fp]
            if (signature != null) {
                newKeptLinks.add(KeptLinkWrite(identity = item, keptAtMs = signature.keptAtMs))
                effectiveReviewedKeys.add(item.key)
                orphanKeptRowsByFingerprint[fp]?.let { staleKeptKeysToRemove.addAll(it) }
            }
        }

        effectiveReviewedKeys.removeAll(staleKeptKeysToRemove)

        return ReconciliationPlan(
            backfillFingerprints = backfillFingerprints,
            discoveredSignatures = discoveredSignaturesMap.values.toList(),
            staleKeptKeysToRemove = staleKeptKeysToRemove,
            newKeptLinks = newKeptLinks,
            effectiveReviewedKeys = effectiveReviewedKeys
        )
    }
}

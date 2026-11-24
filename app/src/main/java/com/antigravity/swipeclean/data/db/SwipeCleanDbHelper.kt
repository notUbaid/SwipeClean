package com.antigravity.swipeclean.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.antigravity.swipeclean.data.model.CleanStats
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.data.model.MediaKey
import com.antigravity.swipeclean.util.BinStatusCalculator
import com.antigravity.swipeclean.util.DeletionBatcher
import com.antigravity.swipeclean.util.KeptMediaRecord
import com.antigravity.swipeclean.util.ReconciliationPlan
import com.antigravity.swipeclean.util.ReviewedRow
import com.antigravity.swipeclean.util.StreakCalculator
import java.time.LocalDate

data class UndoEntry(
    val mediaId: Long,
    val isVideo: Boolean,
    val action: String
) {
    val mediaKey: MediaKey get() = MediaKey(mediaId, isVideo)
}

class SwipeCleanDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "swipeclean.db"
        const val DATABASE_VERSION = 4
        const val MAX_UNDO_ROWS = 200
        private const val SQL_PARAM_CHUNK = 400

        const val TABLE_REVIEWED = "reviewed_items"
        const val COL_MEDIA_ID = "media_id"
        const val COL_IS_VIDEO = "is_video"
        const val COL_URI = "uri_string"
        const val COL_STATUS = "status"
        const val COL_SWIPED_AT = "swiped_at"
        const val COL_SIZE_BYTES = "size_bytes"
        const val COL_FINGERPRINT = "fingerprint"

        const val TABLE_KEPT_SIGNATURES = "kept_signatures"
        const val COL_SIG_FP = "fingerprint"
        const val COL_SIG_NAME = "display_name"
        const val COL_SIG_IS_VIDEO = "is_video"
        const val COL_SIG_SIZE = "size_bytes"
        const val COL_SIG_KEPT_AT = "kept_at"

        const val TABLE_UNDO = "undo_stack"
        const val COL_UNDO_ID = "id"
        const val COL_UNDO_MEDIA_ID = "media_id"
        const val COL_UNDO_IS_VIDEO = "is_video"
        const val COL_UNDO_ACTION = "action"
        const val COL_UNDO_TIMESTAMP = "timestamp"

        const val TABLE_META = "app_metadata"
        const val COL_META_KEY = "key"
        const val COL_META_VAL = "val"

        const val STATUS_KEPT = "KEPT"
        const val STATUS_TRASHED = "TRASHED"
        const val STATUS_LATER = "LATER"

        private const val META_LAST_ACTIVE_DATE = "last_active_date"
        private const val META_STREAK_DAYS = "streak_days"
        private const val META_FREED_COUNT = "lifetime_freed_count"
        private const val META_FREED_BYTES = "lifetime_freed_bytes"
        private const val META_INITIAL_BACKUP_HYDRATED = "initial_backup_hydrated"
        private const val META_BACKUP_URI = "external_backup_uri"

        @Volatile
        private var instance: SwipeCleanDbHelper? = null

        fun getInstance(context: Context): SwipeCleanDbHelper {
            return instance ?: synchronized(this) {
                instance ?: SwipeCleanDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REVIEWED (
                $COL_MEDIA_ID INTEGER NOT NULL,
                $COL_IS_VIDEO INTEGER NOT NULL,
                $COL_URI TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_SWIPED_AT INTEGER NOT NULL,
                $COL_SIZE_BYTES INTEGER NOT NULL,
                $COL_FINGERPRINT TEXT NOT NULL DEFAULT '',
                PRIMARY KEY ($COL_MEDIA_ID, $COL_IS_VIDEO)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_reviewed_fingerprint ON $TABLE_REVIEWED($COL_FINGERPRINT)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_KEPT_SIGNATURES (
                $COL_SIG_FP TEXT PRIMARY KEY,
                $COL_SIG_NAME TEXT NOT NULL DEFAULT '',
                $COL_SIG_IS_VIDEO INTEGER NOT NULL,
                $COL_SIG_SIZE INTEGER NOT NULL,
                $COL_SIG_KEPT_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_UNDO (
                $COL_UNDO_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UNDO_MEDIA_ID INTEGER NOT NULL,
                $COL_UNDO_IS_VIDEO INTEGER NOT NULL DEFAULT 0,
                $COL_UNDO_ACTION TEXT NOT NULL,
                $COL_UNDO_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_META (
                $COL_META_KEY TEXT PRIMARY KEY,
                $COL_META_VAL TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_META (
                    $COL_META_KEY TEXT PRIMARY KEY,
                    $COL_META_VAL TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            db.beginTransaction()
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ${TABLE_REVIEWED}_v3 (
                        $COL_MEDIA_ID INTEGER NOT NULL,
                        $COL_IS_VIDEO INTEGER NOT NULL,
                        $COL_URI TEXT NOT NULL,
                        $COL_STATUS TEXT NOT NULL,
                        $COL_SWIPED_AT INTEGER NOT NULL,
                        $COL_SIZE_BYTES INTEGER NOT NULL,
                        PRIMARY KEY ($COL_MEDIA_ID, $COL_IS_VIDEO)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO ${TABLE_REVIEWED}_v3
                        ($COL_MEDIA_ID, $COL_IS_VIDEO, $COL_URI, $COL_STATUS, $COL_SWIPED_AT, $COL_SIZE_BYTES)
                    SELECT $COL_MEDIA_ID, $COL_IS_VIDEO, $COL_URI,
                        CASE WHEN $COL_STATUS = 'BOOKMARKED' THEN '$STATUS_LATER' ELSE $COL_STATUS END,
                        $COL_SWIPED_AT, $COL_SIZE_BYTES
                    FROM $TABLE_REVIEWED
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE $TABLE_REVIEWED")
                db.execSQL("ALTER TABLE ${TABLE_REVIEWED}_v3 RENAME TO $TABLE_REVIEWED")

                if (!columnExists(db, TABLE_UNDO, COL_UNDO_IS_VIDEO)) {
                    db.execSQL("ALTER TABLE $TABLE_UNDO ADD COLUMN $COL_UNDO_IS_VIDEO INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    "UPDATE $TABLE_UNDO SET $COL_UNDO_ACTION = '$STATUS_LATER' WHERE $COL_UNDO_ACTION = 'BOOKMARKED'"
                )
                db.execSQL(
                    "DELETE FROM $TABLE_UNDO WHERE $COL_UNDO_ID NOT IN (SELECT $COL_UNDO_ID FROM $TABLE_UNDO ORDER BY $COL_UNDO_ID DESC LIMIT $MAX_UNDO_ROWS)"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        if (oldVersion < 4) {
            db.beginTransaction()
            try {
                if (!columnExists(db, TABLE_REVIEWED, COL_FINGERPRINT)) {
                    db.execSQL(
                        "ALTER TABLE $TABLE_REVIEWED ADD COLUMN $COL_FINGERPRINT TEXT NOT NULL DEFAULT ''"
                    )
                }
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_reviewed_fingerprint ON $TABLE_REVIEWED($COL_FINGERPRINT)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS $TABLE_KEPT_SIGNATURES (
                        $COL_SIG_FP TEXT PRIMARY KEY,
                        $COL_SIG_NAME TEXT NOT NULL DEFAULT '',
                        $COL_SIG_IS_VIDEO INTEGER NOT NULL,
                        $COL_SIG_SIZE INTEGER NOT NULL,
                        $COL_SIG_KEPT_AT INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    @Synchronized
    fun markKept(item: MediaItem, recordUndo: Boolean = true) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertReviewed(db, item, STATUS_KEPT)
            if (recordUndo) {
                pushUndoRow(db, item, STATUS_KEPT)
            }
            updateStreakLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun markTrashed(item: MediaItem, recordUndo: Boolean = true) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertReviewed(db, item, STATUS_TRASHED)
            if (recordUndo) {
                pushUndoRow(db, item, STATUS_TRASHED)
            }
            updateStreakLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun markLater(item: MediaItem, recordUndo: Boolean = true) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertReviewed(db, item, STATUS_LATER)
            if (recordUndo) {
                pushUndoRow(db, item, STATUS_LATER)
            }
            updateStreakLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun commitReviewBatch(keptItems: List<MediaItem>, trashedItems: List<MediaItem>) {
        if (keptItems.isEmpty() && trashedItems.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (item in keptItems) {
                upsertReviewed(db, item, STATUS_KEPT, now)
            }
            for (item in trashedItems) {
                upsertReviewed(db, item, STATUS_TRASHED, now)
            }
            updateStreakLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun upsertReviewed(
        db: SQLiteDatabase,
        item: MediaItem,
        status: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val prevKeptFp = findKeptFingerprintLocked(db, item.mediaKey)
        val values = ContentValues().apply {
            put(COL_MEDIA_ID, item.id)
            put(COL_IS_VIDEO, if (item.isVideo) 1 else 0)
            put(COL_URI, item.uri.toString())
            put(COL_STATUS, status)
            put(COL_SWIPED_AT, timestampMs)
            put(COL_SIZE_BYTES, item.sizeBytes)
            put(COL_FINGERPRINT, item.fingerprint)
        }
        db.insertWithOnConflict(TABLE_REVIEWED, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (status == STATUS_KEPT) {
            if (item.fingerprint.isNotBlank()) {
                val sigValues = ContentValues().apply {
                    put(COL_SIG_FP, item.fingerprint)
                    put(COL_SIG_NAME, item.displayName)
                    put(COL_SIG_IS_VIDEO, if (item.isVideo) 1 else 0)
                    put(COL_SIG_SIZE, item.sizeBytes)
                    put(COL_SIG_KEPT_AT, timestampMs)
                }
                db.insertWithOnConflict(TABLE_KEPT_SIGNATURES, null, sigValues, SQLiteDatabase.CONFLICT_REPLACE)
            }
        } else if (!prevKeptFp.isNullOrBlank()) {
            pruneSignatureIfUnreferencedLocked(db, prevKeptFp)
        }
    }

    private fun pushUndoRow(db: SQLiteDatabase, item: MediaItem, status: String) {
        val undoValues = ContentValues().apply {
            put(COL_UNDO_MEDIA_ID, item.id)
            put(COL_UNDO_IS_VIDEO, if (item.isVideo) 1 else 0)
            put(COL_UNDO_ACTION, status)
            put(COL_UNDO_TIMESTAMP, System.currentTimeMillis())
        }
        db.insert(TABLE_UNDO, null, undoValues)
        db.execSQL(
            """
            DELETE FROM $TABLE_UNDO
            WHERE $COL_UNDO_ID NOT IN (
                SELECT $COL_UNDO_ID FROM $TABLE_UNDO ORDER BY $COL_UNDO_ID DESC LIMIT $MAX_UNDO_ROWS
            )
            """.trimIndent()
        )
    }

    @Synchronized
    fun hasUndoHistory(): Boolean {
        val db = readableDatabase
        db.rawQuery("SELECT 1 FROM $TABLE_UNDO LIMIT 1", null).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    @Synchronized
    fun popUndo(): UndoEntry? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val entry = db.query(
                TABLE_UNDO,
                arrayOf(COL_UNDO_ID, COL_UNDO_MEDIA_ID, COL_UNDO_IS_VIDEO, COL_UNDO_ACTION),
                null,
                null,
                null,
                null,
                "$COL_UNDO_ID DESC",
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                val undoId = cursor.getLong(0)
                val mediaId = cursor.getLong(1)
                val isVideo = cursor.getInt(2) == 1
                val action = cursor.getString(3)
                Triple(undoId, UndoEntry(mediaId, isVideo, action), isVideo)
            }

            val (undoId, undoEntry, isVideo) = entry
            val keptFp = findKeptFingerprintLocked(db, undoEntry.mediaKey)
            val isVideoArg = if (isVideo) "1" else "0"
            db.delete(TABLE_UNDO, "$COL_UNDO_ID = ?", arrayOf(undoId.toString()))
            db.delete(
                TABLE_REVIEWED,
                "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?",
                arrayOf(undoEntry.mediaId.toString(), isVideoArg)
            )
            if (!keptFp.isNullOrBlank()) {
                pruneSignatureIfUnreferencedLocked(db, keptFp)
            }

            db.setTransactionSuccessful()
            return undoEntry
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun restoreItem(key: MediaKey) {
        val db = writableDatabase
        val args = arrayOf(key.id.toString(), if (key.isVideo) "1" else "0")
        db.beginTransaction()
        try {
            val keptFp = findKeptFingerprintLocked(db, key)
            db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?", args)
            db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ? AND $COL_UNDO_IS_VIDEO = ?", args)
            if (!keptFp.isNullOrBlank()) {
                pruneSignatureIfUnreferencedLocked(db, keptFp)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun restoreAllKeys(keys: Collection<MediaKey>) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val fpsToCheck = LinkedHashSet<String>()
            for (key in keys) {
                findKeptFingerprintLocked(db, key)?.let { if (it.isNotBlank()) fpsToCheck.add(it) }
                val args = arrayOf(key.id.toString(), if (key.isVideo) "1" else "0")
                db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?", args)
                db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ? AND $COL_UNDO_IS_VIDEO = ?", args)
            }
            for (fp in fpsToCheck) {
                pruneSignatureIfUnreferencedLocked(db, fp)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun findKeptFingerprintLocked(db: SQLiteDatabase, key: MediaKey): String? {
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_FINGERPRINT, COL_STATUS),
            "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?",
            arrayOf(key.id.toString(), if (key.isVideo) "1" else "0"),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val fp = cursor.getString(0)
            val status = cursor.getString(1)
            return if (status == STATUS_KEPT) fp else null
        }
    }

    private fun pruneSignatureIfUnreferencedLocked(db: SQLiteDatabase, fingerprint: String) {
        if (fingerprint.isBlank()) return
        db.rawQuery(
            "SELECT 1 FROM $TABLE_REVIEWED WHERE $COL_STATUS = ? AND $COL_FINGERPRINT = ? LIMIT 1",
            arrayOf(STATUS_KEPT, fingerprint)
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                db.delete(TABLE_KEPT_SIGNATURES, "$COL_SIG_FP = ?", arrayOf(fingerprint))
            }
        }
    }

    @Synchronized
    fun recordPermanentDeletion(deletedItems: Collection<MediaItem>) {
        if (deletedItems.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val prevCount = getMetaLongLocked(db, META_FREED_COUNT)
            val prevBytes = getMetaLongLocked(db, META_FREED_BYTES)
            val summary = DeletionBatcher.summarizeFreed(deletedItems)

            setMetaValueLocked(db, META_FREED_COUNT, (prevCount + summary.count).toString())
            setMetaValueLocked(db, META_FREED_BYTES, (prevBytes + summary.totalBytes).toString())

            for (chunk in deletedItems.chunked(SQL_PARAM_CHUNK)) {
                for (item in chunk) {
                    val args = arrayOf(item.id.toString(), if (item.isVideo) "1" else "0")
                    db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?", args)
                    db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ? AND $COL_UNDO_IS_VIDEO = ?", args)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getAllReviewedKeys(): Set<MediaKey> {
        val db = readableDatabase
        val result = HashSet<MediaKey>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID, COL_IS_VIDEO),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(MediaKey(cursor.getLong(0), cursor.getInt(1) == 1))
            }
        }
        return result
    }

    @Synchronized
    fun getAllLaterKeys(): List<MediaKey> {
        val db = readableDatabase
        val result = mutableListOf<MediaKey>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID, COL_IS_VIDEO),
            "$COL_STATUS = ?",
            arrayOf(STATUS_LATER),
            null,
            null,
            "$COL_SWIPED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(MediaKey(cursor.getLong(0), cursor.getInt(1) == 1))
            }
        }
        return result
    }

    @Synchronized
    fun getAllTrashedMap(): Map<MediaKey, Long> {
        val db = readableDatabase
        val result = LinkedHashMap<MediaKey, Long>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID, COL_IS_VIDEO, COL_SWIPED_AT),
            "$COL_STATUS = ?",
            arrayOf(STATUS_TRASHED),
            null,
            null,
            "$COL_SWIPED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = MediaKey(cursor.getLong(0), cursor.getInt(1) == 1)
                result[key] = cursor.getLong(2)
            }
        }
        return result
    }

    @Synchronized
    fun getStats(nowMs: Long = System.currentTimeMillis()): CleanStats {
        val db = readableDatabase
        var keptCount = 0
        var laterCount = 0
        var binCount = 0
        var binTotalBytes = 0L

        db.rawQuery(
            """
            SELECT $COL_STATUS, COUNT(*), SUM($COL_SIZE_BYTES)
            FROM $TABLE_REVIEWED
            GROUP BY $COL_STATUS
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val status = cursor.getString(0)
                val count = cursor.getInt(1)
                val sumSize = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                when (status) {
                    STATUS_KEPT -> keptCount = count
                    STATUS_TRASHED -> {
                        binCount = count
                        binTotalBytes = sumSize
                    }
                    STATUS_LATER -> laterCount = count
                }
            }
        }

        val threshold = nowMs - (BinStatusCalculator.RETENTION_DAYS.toLong() * 24L * 60L * 60L * 1000L)
        var expiredCount = 0
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_REVIEWED WHERE $COL_STATUS = ? AND $COL_SWIPED_AT <= ?",
            arrayOf(STATUS_TRASHED, threshold.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                expiredCount = cursor.getInt(0)
            }
        }

        val freedCount = getMetaLongLocked(db, META_FREED_COUNT).toInt()
        val freedBytes = getMetaLongLocked(db, META_FREED_BYTES)
        val lastDateStr = getMetaStringLocked(db, META_LAST_ACTIVE_DATE)
        val storedStreak = getMetaStringLocked(db, META_STREAK_DAYS)?.toIntOrNull() ?: 0
        val visibleStreak = StreakCalculator.visibleStreak(lastDateStr, storedStreak, LocalDate.now())

        return CleanStats(
            totalKeptCount = keptCount,
            binItemCount = binCount,
            binTotalSizeBytes = binTotalBytes,
            expiredBinCount = expiredCount,
            totalLaterCount = laterCount,
            lifetimeDeletedCount = freedCount,
            lifetimeFreedBytes = freedBytes,
            streakDays = visibleStreak
        )
    }

    private fun updateStreakLocked(db: SQLiteDatabase) {
        val lastDateStr = getMetaStringLocked(db, META_LAST_ACTIVE_DATE)
        val storedStreak = getMetaStringLocked(db, META_STREAK_DAYS)?.toIntOrNull() ?: 0
        val (newDateStr, newStreak) = StreakCalculator.recordActivity(lastDateStr, storedStreak, LocalDate.now())
        setMetaValueLocked(db, META_LAST_ACTIVE_DATE, newDateStr)
        setMetaValueLocked(db, META_STREAK_DAYS, newStreak.toString())
    }

    private fun getMetaStringLocked(db: SQLiteDatabase, key: String): String? {
        db.query(
            TABLE_META,
            arrayOf(COL_META_VAL),
            "$COL_META_KEY = ?",
            arrayOf(key),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun getMetaLongLocked(db: SQLiteDatabase, key: String): Long {
        return getMetaStringLocked(db, key)?.toLongOrNull() ?: 0L
    }

    private fun setMetaValueLocked(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put(COL_META_KEY, key)
            put(COL_META_VAL, value)
        }
        db.insertWithOnConflict(TABLE_META, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getAllReviewedRows(): List<ReviewedRow> {
        val db = readableDatabase
        val result = ArrayList<ReviewedRow>(256)
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID, COL_IS_VIDEO, COL_STATUS, COL_SWIPED_AT, COL_SIZE_BYTES, COL_FINGERPRINT),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ReviewedRow(
                        key = MediaKey(cursor.getLong(0), cursor.getInt(1) == 1),
                        status = cursor.getString(2),
                        swipedAtMs = cursor.getLong(3),
                        sizeBytes = cursor.getLong(4),
                        fingerprint = cursor.getString(5) ?: ""
                    )
                )
            }
        }
        return result
    }

    @Synchronized
    fun getAllKeptSignatures(): Map<String, KeptMediaRecord> {
        val db = readableDatabase
        val result = LinkedHashMap<String, KeptMediaRecord>()
        db.query(
            TABLE_KEPT_SIGNATURES,
            arrayOf(COL_SIG_FP, COL_SIG_NAME, COL_SIG_IS_VIDEO, COL_SIG_SIZE, COL_SIG_KEPT_AT),
            null,
            null,
            null,
            null,
            "$COL_SIG_KEPT_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fp = cursor.getString(0) ?: continue
                if (fp.isBlank()) continue
                result[fp] = KeptMediaRecord(
                    fingerprint = fp,
                    displayName = cursor.getString(1) ?: "",
                    isVideo = cursor.getInt(2) == 1,
                    sizeBytes = cursor.getLong(3),
                    keptAtMs = cursor.getLong(4)
                )
            }
        }
        return result
    }

    @Synchronized
    fun seedKeptSignatures(records: Collection<KeptMediaRecord>): Int {
        if (records.isEmpty()) return 0
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            for (record in records) {
                if (record.fingerprint.isBlank()) continue
                val values = ContentValues().apply {
                    put(COL_SIG_FP, record.fingerprint)
                    put(COL_SIG_NAME, record.displayName)
                    put(COL_SIG_IS_VIDEO, if (record.isVideo) 1 else 0)
                    put(COL_SIG_SIZE, record.sizeBytes)
                    put(COL_SIG_KEPT_AT, record.keptAtMs)
                }
                val rowId = db.insertWithOnConflict(
                    TABLE_KEPT_SIGNATURES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rowId != -1L) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    @Synchronized
    fun applyReconciliation(plan: ReconciliationPlan) {
        if (!plan.hasDbMutations) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((key, fp) in plan.backfillFingerprints) {
                val values = ContentValues().apply { put(COL_FINGERPRINT, fp) }
                db.update(
                    TABLE_REVIEWED,
                    values,
                    "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?",
                    arrayOf(key.id.toString(), if (key.isVideo) "1" else "0")
                )
            }

            for (record in plan.discoveredSignatures) {
                val values = ContentValues().apply {
                    put(COL_SIG_FP, record.fingerprint)
                    put(COL_SIG_NAME, record.displayName)
                    put(COL_SIG_IS_VIDEO, if (record.isVideo) 1 else 0)
                    put(COL_SIG_SIZE, record.sizeBytes)
                    put(COL_SIG_KEPT_AT, record.keptAtMs)
                }
                db.insertWithOnConflict(
                    TABLE_KEPT_SIGNATURES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }

            for (staleKey in plan.staleKeptKeysToRemove) {
                db.delete(
                    TABLE_REVIEWED,
                    "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ? AND $COL_STATUS = ?",
                    arrayOf(staleKey.id.toString(), if (staleKey.isVideo) "1" else "0", STATUS_KEPT)
                )
            }

            for (link in plan.newKeptLinks) {
                val identity = link.identity
                val values = ContentValues().apply {
                    put(COL_MEDIA_ID, identity.key.id)
                    put(COL_IS_VIDEO, if (identity.isVideo) 1 else 0)
                    put(COL_URI, identity.uriString)
                    put(COL_STATUS, STATUS_KEPT)
                    put(COL_SWIPED_AT, link.keptAtMs)
                    put(COL_SIZE_BYTES, identity.sizeBytes)
                    put(COL_FINGERPRINT, identity.fingerprint)
                }
                db.insertWithOnConflict(
                    TABLE_REVIEWED,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun isInitialBackupHydrated(): Boolean =
        getMetaStringLocked(readableDatabase, META_INITIAL_BACKUP_HYDRATED) == "1"

    @Synchronized
    fun markInitialBackupHydrated() {
        setMetaValueLocked(writableDatabase, META_INITIAL_BACKUP_HYDRATED, "1")
    }

    @Synchronized
    fun getBackupUriString(): String? =
        getMetaStringLocked(readableDatabase, META_BACKUP_URI)?.takeIf { it.isNotBlank() }

    @Synchronized
    fun setBackupUriString(uriString: String?) {
        val db = writableDatabase
        if (uriString.isNullOrBlank()) {
            db.delete(TABLE_META, "$COL_META_KEY = ?", arrayOf(META_BACKUP_URI))
        } else {
            setMetaValueLocked(db, META_BACKUP_URI, uriString)
        }
    }

    @Synchronized
    fun resetKeptHistory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_REVIEWED, "$COL_STATUS = ?", arrayOf(STATUS_KEPT))
            db.delete(TABLE_UNDO, "$COL_UNDO_ACTION = ?", arrayOf(STATUS_KEPT))
            db.delete(TABLE_KEPT_SIGNATURES, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameCol = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameCol >= 0 && cursor.getString(nameCol) == column) {
                    return true
                }
            }
        }
        return false
    }
}

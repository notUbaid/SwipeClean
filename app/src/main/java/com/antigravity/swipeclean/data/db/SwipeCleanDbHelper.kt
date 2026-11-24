package com.antigravity.swipeclean.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.antigravity.swipeclean.data.model.CleanStats
import com.antigravity.swipeclean.data.model.MediaItem
import com.antigravity.swipeclean.data.model.MediaKey
import com.antigravity.swipeclean.util.BinStatusCalculator
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
        const val DATABASE_VERSION = 3
        const val MAX_UNDO_ROWS = 200
        private const val SQL_PARAM_CHUNK = 400

        const val TABLE_REVIEWED = "reviewed_items"
        const val COL_MEDIA_ID = "media_id"
        const val COL_IS_VIDEO = "is_video"
        const val COL_URI = "uri_string"
        const val COL_STATUS = "status"
        const val COL_SWIPED_AT = "swiped_at"
        const val COL_SIZE_BYTES = "size_bytes"

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

        @Volatile
        private var instance: SwipeCleanDbHelper? = null

        fun getInstance(context: Context): SwipeCleanDbHelper {
            return instance ?: synchronized(this) {
                instance ?: SwipeCleanDbHelper(context.applicationContext).also { instance = it }
            }
        }
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
                PRIMARY KEY ($COL_MEDIA_ID, $COL_IS_VIDEO)
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

                db.execSQL("ALTER TABLE $TABLE_UNDO ADD COLUMN $COL_UNDO_IS_VIDEO INTEGER NOT NULL DEFAULT 0")
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
        val values = ContentValues().apply {
            put(COL_MEDIA_ID, item.id)
            put(COL_IS_VIDEO, if (item.isVideo) 1 else 0)
            put(COL_URI, item.uri.toString())
            put(COL_STATUS, status)
            put(COL_SWIPED_AT, timestampMs)
            put(COL_SIZE_BYTES, item.sizeBytes)
        }
        db.insertWithOnConflict(TABLE_REVIEWED, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
            val isVideoArg = if (isVideo) "1" else "0"
            db.delete(TABLE_UNDO, "$COL_UNDO_ID = ?", arrayOf(undoId.toString()))
            db.delete(
                TABLE_REVIEWED,
                "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?",
                arrayOf(undoEntry.mediaId.toString(), isVideoArg)
            )

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
            db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?", args)
            db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ? AND $COL_UNDO_IS_VIDEO = ?", args)
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
            for (key in keys) {
                val args = arrayOf(key.id.toString(), if (key.isVideo) "1" else "0")
                db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ? AND $COL_IS_VIDEO = ?", args)
                db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ? AND $COL_UNDO_IS_VIDEO = ?", args)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
            val addedBytes = deletedItems.sumOf { maxOf(0L, it.sizeBytes) }

            setMetaValueLocked(db, META_FREED_COUNT, (prevCount + deletedItems.size).toString())
            setMetaValueLocked(db, META_FREED_BYTES, (prevBytes + addedBytes).toString())

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
    fun resetKeptHistory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_REVIEWED, "$COL_STATUS = ?", arrayOf(STATUS_KEPT))
            db.delete(TABLE_UNDO, "$COL_UNDO_ACTION = ?", arrayOf(STATUS_KEPT))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

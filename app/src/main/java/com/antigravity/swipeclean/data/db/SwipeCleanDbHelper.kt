package com.antigravity.swipeclean.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.antigravity.swipeclean.data.model.CleanStats
import com.antigravity.swipeclean.data.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SwipeCleanDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "swipeclean.db"
        const val DATABASE_VERSION = 2

        const val TABLE_REVIEWED = "reviewed_items"
        const val COL_MEDIA_ID = "media_id"
        const val COL_URI = "uri_string"
        const val COL_STATUS = "status" // "KEPT", "TRASHED", "BOOKMARKED"
        const val COL_SWIPED_AT = "swiped_at"
        const val COL_SIZE_BYTES = "size_bytes"
        const val COL_IS_VIDEO = "is_video"

        const val TABLE_UNDO = "undo_stack"
        const val COL_UNDO_ID = "id"
        const val COL_UNDO_MEDIA_ID = "media_id"
        const val COL_UNDO_ACTION = "action"
        const val COL_UNDO_TIMESTAMP = "timestamp"

        const val TABLE_META = "app_metadata"
        const val COL_META_KEY = "key"
        const val COL_META_VAL = "val"

        const val STATUS_KEPT = "KEPT"
        const val STATUS_TRASHED = "TRASHED"
        const val STATUS_BOOKMARKED = "BOOKMARKED"

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
                $COL_MEDIA_ID INTEGER PRIMARY KEY,
                $COL_URI TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_SWIPED_AT INTEGER NOT NULL,
                $COL_SIZE_BYTES INTEGER NOT NULL,
                $COL_IS_VIDEO INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_UNDO (
                $COL_UNDO_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UNDO_MEDIA_ID INTEGER NOT NULL,
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
    }

    @Synchronized
    fun markKept(item: MediaItem) {
        recordAction(item, STATUS_KEPT)
        updateStreak()
    }

    @Synchronized
    fun markTrashed(item: MediaItem) {
        recordAction(item, STATUS_TRASHED)
        updateStreak()
    }

    @Synchronized
    fun markBookmarked(item: MediaItem) {
        recordAction(item, STATUS_BOOKMARKED)
        updateStreak()
    }

    private fun recordAction(item: MediaItem, status: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COL_MEDIA_ID, item.id)
                put(COL_URI, item.uri.toString())
                put(COL_STATUS, status)
                put(COL_SWIPED_AT, System.currentTimeMillis())
                put(COL_SIZE_BYTES, item.sizeBytes)
                put(COL_IS_VIDEO, if (item.isVideo) 1 else 0)
            }
            db.insertWithOnConflict(TABLE_REVIEWED, null, values, SQLiteDatabase.CONFLICT_REPLACE)

            val undoValues = ContentValues().apply {
                put(COL_UNDO_MEDIA_ID, item.id)
                put(COL_UNDO_ACTION, status)
                put(COL_UNDO_TIMESTAMP, System.currentTimeMillis())
            }
            db.insert(TABLE_UNDO, null, undoValues)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun popUndo(): Pair<Long, String>? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.query(
                TABLE_UNDO,
                arrayOf(COL_UNDO_ID, COL_UNDO_MEDIA_ID, COL_UNDO_ACTION),
                null,
                null,
                null,
                null,
                "$COL_UNDO_ID DESC",
                "1"
            )

            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }

            val undoId = cursor.getLong(0)
            val mediaId = cursor.getLong(1)
            val action = cursor.getString(2)
            cursor.close()

            db.delete(TABLE_UNDO, "$COL_UNDO_ID = ?", arrayOf(undoId.toString()))
            db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ?", arrayOf(mediaId.toString()))

            db.setTransactionSuccessful()
            return Pair(mediaId, action)
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun restoreItem(mediaId: Long) {
        val db = writableDatabase
        db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ?", arrayOf(mediaId.toString()))
        db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ?", arrayOf(mediaId.toString()))
    }

    @Synchronized
    fun removeBookmark(mediaId: Long) {
        val db = writableDatabase
        db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ? AND $COL_STATUS = ?", arrayOf(mediaId.toString(), STATUS_BOOKMARKED))
    }

    @Synchronized
    fun deletePermanently(mediaId: Long) {
        val db = writableDatabase
        db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID = ?", arrayOf(mediaId.toString()))
        db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID = ?", arrayOf(mediaId.toString()))
    }

    @Synchronized
    fun deletePermanentlyAll(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val idStrings = mediaIds.map { it.toString() }.toTypedArray()
            val placeholders = mediaIds.joinToString(",") { "?" }
            db.delete(TABLE_REVIEWED, "$COL_MEDIA_ID IN ($placeholders)", idStrings)
            db.delete(TABLE_UNDO, "$COL_UNDO_MEDIA_ID IN ($placeholders)", idStrings)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getAllKeptIds(): Set<Long> {
        val db = readableDatabase
        val result = mutableSetOf<Long>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID),
            "$COL_STATUS = ?",
            arrayOf(STATUS_KEPT),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0))
            }
        }
        return result
    }

    @Synchronized
    fun getAllBookmarkedIds(): Set<Long> {
        val db = readableDatabase
        val result = mutableSetOf<Long>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID),
            "$COL_STATUS = ?",
            arrayOf(STATUS_BOOKMARKED),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0))
            }
        }
        return result
    }

    @Synchronized
    fun getAllReviewedIds(): Set<Long> {
        val db = readableDatabase
        val result = mutableSetOf<Long>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0))
            }
        }
        return result
    }

    @Synchronized
    fun getAllTrashedMap(): Map<Long, Long> {
        val db = readableDatabase
        val result = mutableMapOf<Long, Long>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID, COL_SWIPED_AT),
            "$COL_STATUS = ?",
            arrayOf(STATUS_TRASHED),
            null,
            null,
            "$COL_SWIPED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = cursor.getLong(1)
            }
        }
        return result
    }

    @Synchronized
    fun getExpiredTrashedIds(expirationDays: Int = 7): List<Long> {
        val db = readableDatabase
        val threshold = System.currentTimeMillis() - (expirationDays.toLong() * 24 * 60 * 60 * 1000)
        val result = mutableListOf<Long>()
        db.query(
            TABLE_REVIEWED,
            arrayOf(COL_MEDIA_ID),
            "$COL_STATUS = ? AND $COL_SWIPED_AT <= ?",
            arrayOf(STATUS_TRASHED, threshold.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0))
            }
        }
        return result
    }

    @Synchronized
    fun getStats(): CleanStats {
        val db = readableDatabase
        var keptCount = 0
        var trashedCount = 0
        var bookmarkedCount = 0
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
                        trashedCount = count
                        binCount = count
                        binTotalBytes = sumSize
                    }
                    STATUS_BOOKMARKED -> bookmarkedCount = count
                }
            }
        }

        val streak = getStreakDays()

        return CleanStats(
            totalKeptCount = keptCount,
            totalTrashedCount = trashedCount,
            totalBookmarkedCount = bookmarkedCount,
            binItemCount = binCount,
            binTotalSizeBytes = binTotalBytes,
            streakDays = streak
        )
    }

    private fun updateStreak() {
        val db = writableDatabase
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cursor = db.query(TABLE_META, arrayOf(COL_META_VAL), "$COL_META_KEY = ?", arrayOf("last_active_date"), null, null, null)
        val lastDate = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()

        var currentStreak = getStreakDays()
        if (lastDate == null) {
            currentStreak = 1
        } else if (lastDate != todayStr) {
            val lastMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(lastDate)?.time ?: 0L
            val todayMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(todayStr)?.time ?: 0L
            val diffDays = ((todayMillis - lastMillis) / (1000 * 60 * 60 * 24)).toInt()
            currentStreak = if (diffDays == 1) currentStreak + 1 else 1
        }

        val v1 = ContentValues().apply {
            put(COL_META_KEY, "last_active_date")
            put(COL_META_VAL, todayStr)
        }
        db.insertWithOnConflict(TABLE_META, null, v1, SQLiteDatabase.CONFLICT_REPLACE)

        val v2 = ContentValues().apply {
            put(COL_META_KEY, "streak_days")
            put(COL_META_VAL, currentStreak.toString())
        }
        db.insertWithOnConflict(TABLE_META, null, v2, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun getStreakDays(): Int {
        val db = readableDatabase
        val cursor = db.query(TABLE_META, arrayOf(COL_META_VAL), "$COL_META_KEY = ?", arrayOf("streak_days"), null, null, null)
        val streak = if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() ?: 1 else 1
        cursor.close()
        return streak
    }

    @Synchronized
    fun resetKeptHistory() {
        val db = writableDatabase
        db.delete(TABLE_REVIEWED, "$COL_STATUS = ?", arrayOf(STATUS_KEPT))
    }
}

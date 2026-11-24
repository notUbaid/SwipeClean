package com.antigravity.swipeclean.data.backup

import android.app.backup.BackupManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.antigravity.swipeclean.util.KeptBackupPayload
import com.antigravity.swipeclean.util.KeptHistoryCodec
import com.antigravity.swipeclean.util.KeptMediaRecord
import java.io.File

class KeptBackupStorage(context: Context) {

    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val snapshotFile = File(appContext.filesDir, SNAPSHOT_FILE_NAME)

    fun readInternalSnapshot(): KeptBackupPayload? {
        if (!snapshotFile.exists()) return null
        val raw = runCatching { snapshotFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return KeptHistoryCodec.decode(raw)
    }

    fun writeInternalSnapshot(records: List<KeptMediaRecord>) {
        val payload = KeptBackupPayload(
            version = KeptBackupPayload.CURRENT_VERSION,
            updatedAtMs = System.currentTimeMillis(),
            items = records
        )
        val encoded = KeptHistoryCodec.encode(payload)
        runCatching {
            val tempFile = File(appContext.filesDir, "$SNAPSHOT_FILE_NAME.tmp")
            tempFile.writeText(encoded, Charsets.UTF_8)
            if (!tempFile.renameTo(snapshotFile)) {
                snapshotFile.writeText(encoded, Charsets.UTF_8)
                tempFile.delete()
            }
            BackupManager.dataChanged(appContext.packageName)
        }
    }

    fun takePersistablePermissions(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun releasePersistablePermissions(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    fun writeToUri(uri: Uri, records: List<KeptMediaRecord>): Boolean {
        val payload = KeptBackupPayload(
            version = KeptBackupPayload.CURRENT_VERSION,
            updatedAtMs = System.currentTimeMillis(),
            items = records
        )
        val bytes = KeptHistoryCodec.encode(payload).toByteArray(Charsets.UTF_8)
        return runCatching {
            val stream = contentResolver.openOutputStream(uri, "wt")
                ?: contentResolver.openOutputStream(uri)
                ?: return false
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
            true
        }.getOrDefault(false)
    }

    fun readFromUri(uri: Uri): KeptBackupPayload? {
        return runCatching {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return null
            KeptHistoryCodec.decode(raw)
        }.getOrNull()
    }

    fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val SNAPSHOT_FILE_NAME = "kept_history.json"
        const val SUGGESTED_BACKUP_FILE_NAME = "swipeclean-kept-backup.json"
    }
}

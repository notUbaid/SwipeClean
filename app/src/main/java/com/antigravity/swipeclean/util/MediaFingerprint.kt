package com.antigravity.swipeclean.util

import java.security.MessageDigest
import java.util.Locale

data class KeptMediaRecord(
    val fingerprint: String,
    val displayName: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val keptAtMs: Long
)

object MediaFingerprint {

    private const val HEX_CHARS = "0123456789abcdef"

    /**
     * Generates a portable SHA-256 content key from media type, exact byte size, and normalized
     * display name so kept decisions survive MediaStore re-indexing, folder moves, OS upgrades,
     * and app reinstalls/restores.
     */
    fun build(
        isVideo: Boolean,
        sizeBytes: Long,
        displayName: String?
    ): String {
        if (sizeBytes <= 0L) return ""
        val cleanName = displayName?.trim()?.lowercase(Locale.US).orEmpty()
        if (cleanName.isBlank()) return ""

        val payload = "${if (isVideo) "v" else "i"}|$sizeBytes|$cleanName"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val byteVal = digest[i].toInt() and 0xFF
                append(HEX_CHARS[byteVal ushr 4])
                append(HEX_CHARS[byteVal and 0x0F])
            }
        }
    }
}

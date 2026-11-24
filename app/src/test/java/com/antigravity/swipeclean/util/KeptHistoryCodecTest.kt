package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeptHistoryCodecTest {

    @Test
    fun roundTripsEscapedFilenamesAndDeduplicatesKeepingNewestTimestamp() {
        val payload = KeptBackupPayload(
            version = 1,
            updatedAtMs = 1_764_000_000_000L,
            items = listOf(
                KeptMediaRecord("fp1", "Vacation \"2026\"\\cover\n1.jpg", false, 4_200_123L, 100L),
                KeptMediaRecord("fp1", "Vacation \"2026\"\\cover\n1.jpg", false, 4_200_123L, 500L),
                KeptMediaRecord("fp2", "VID_2026.mp4", true, 98_000_000L, 900L)
            )
        )

        val encoded = KeptHistoryCodec.encode(payload)
        val decoded = KeptHistoryCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.version)
        assertEquals(1_764_000_000_000L, decoded.updatedAtMs)
        assertEquals(2, decoded.items.size)
        assertEquals("Vacation \"2026\"\\cover\n1.jpg", decoded.items[0].displayName)
        assertEquals(500L, decoded.items[0].keptAtMs)
        assertEquals(true, decoded.items[1].isVideo)
        assertEquals(98_000_000L, decoded.items[1].sizeBytes)
    }

    @Test
    fun returnsNullOnCorruptOrEmptyInput() {
        assertNull(KeptHistoryCodec.decode(""))
        assertNull(KeptHistoryCodec.decode("{bad json"))
        assertNull(KeptHistoryCodec.decode("[]"))
    }
}

package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaFingerprintTest {

    @Test
    fun producesSameFingerprintRegardlessOfCaseOrWhitespace() {
        val a = MediaFingerprint.build(
            isVideo = false,
            sizeBytes = 4_123_456L,
            displayName = "IMG_20261124_120000.JPG"
        )
        val b = MediaFingerprint.build(
            isVideo = false,
            sizeBytes = 4_123_456L,
            displayName = " img_20261124_120000.jpg "
        )
        assertEquals(32, a.length)
        assertEquals(a, b)
    }

    @Test
    fun distinguishesPhotoFromVideoAndDifferentSizesOrNames() {
        val photo = MediaFingerprint.build(isVideo = false, sizeBytes = 1000L, displayName = "clip.mp4")
        val video = MediaFingerprint.build(isVideo = true, sizeBytes = 1000L, displayName = "clip.mp4")
        val differentSize = MediaFingerprint.build(isVideo = true, sizeBytes = 1001L, displayName = "clip.mp4")
        val differentName = MediaFingerprint.build(isVideo = true, sizeBytes = 1000L, displayName = "clip2.mp4")

        assertNotEquals(photo, video)
        assertNotEquals(video, differentSize)
        assertNotEquals(video, differentName)
    }

    @Test
    fun returnsEmptyForZeroSizeOrBlankName() {
        assertEquals("", MediaFingerprint.build(false, 0L, "IMG_01.jpg"))
        assertEquals("", MediaFingerprint.build(false, 1024L, ""))
        assertEquals("", MediaFingerprint.build(true, 1024L, null))
    }
}

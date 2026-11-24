package com.antigravity.swipeclean.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    @Test
    fun identicalLumaGradients_haveZeroHammingDistance() {
        val luma = IntArray(72) { i -> (i % 9) * 20 }
        val hashA = PerceptualHash.computeDHashFromLuma(luma)
        val hashB = PerceptualHash.computeDHashFromLuma(luma.copyOf())
        assertEquals(0, PerceptualHash.hammingDistance(hashA, hashB))
    }

    @Test
    fun reversedGradients_haveMaximumHammingDistance() {
        val ascending = IntArray(72) { i -> (i % 9) * 20 }
        val descending = IntArray(72) { i -> (8 - (i % 9)) * 20 }
        val hashA = PerceptualHash.computeDHashFromLuma(ascending)
        val hashB = PerceptualHash.computeDHashFromLuma(descending)
        assertEquals(64, PerceptualHash.hammingDistance(hashA, hashB))
    }

    @Test
    fun minorExposureShift_staysWithinSimilarityThreshold() {
        val lumaA = IntArray(72) { i -> (i % 9) * 25 }
        val lumaB = IntArray(72) { i -> (i % 9) * 25 + 10 }
        val hashA = PerceptualHash.computeDHashFromLuma(lumaA)
        val hashB = PerceptualHash.computeDHashFromLuma(lumaB)
        assertTrue(
            PerceptualHash.hammingDistance(hashA, hashB) <= PerceptualHash.DEFAULT_SIMILARITY_THRESHOLD
        )
    }
}

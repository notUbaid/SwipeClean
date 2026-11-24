package com.antigravity.swipeclean.util

import android.graphics.Bitmap

object PerceptualHash {

    const val DEFAULT_SIMILARITY_THRESHOLD = 12

    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(9 * 8)
        scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val luma = IntArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
        return computeDHashFromLuma(luma)
    }

    fun computeDHashFromLuma(grayscale9x8: IntArray): Long {
        require(grayscale9x8.size == 72) { "Expected 9x8 grayscale array of 72 elements" }
        var hash = 0L
        var bit = 0
        for (row in 0 until 8) {
            val rowStart = row * 9
            for (col in 0 until 8) {
                if (grayscale9x8[rowStart + col] < grayscale9x8[rowStart + col + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

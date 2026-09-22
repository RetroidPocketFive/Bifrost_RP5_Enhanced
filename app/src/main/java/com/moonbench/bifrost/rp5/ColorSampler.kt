package com.moonbench.bifrost.rp5

enum class SamplingMethod {
    AVERAGE,
    CENTER_WEIGHTED,
    DOMINANT
}

data class SampledColors(val left: Int, val right: Int)

class ColorSampler(
    private val method: SamplingMethod = SamplingMethod.CENTER_WEIGHTED,
) {
    fun sample(
        pixels: IntArray,
        width: Int,
        height: Int,
        leftRegion: NormalizedRegion,
        rightRegion: NormalizedRegion,
    ): SampledColors {
        require(width > 0 && height > 0)
        require(pixels.size >= width * height)
        return SampledColors(
            sampleRegion(pixels, width, height, leftRegion),
            sampleRegion(pixels, width, height, rightRegion)
        )
    }

    private fun sampleRegion(
        pixels: IntArray,
        width: Int,
        height: Int,
        region: NormalizedRegion,
    ): Int {
        val b = region.bounds()
        val x0 = (b.left * width).toInt().coerceIn(0, width - 1)
        val x1 = (b.right * width).toInt().coerceIn(x0 + 1, width)
        val y0 = (b.top * height).toInt().coerceIn(0, height - 1)
        val y1 = (b.bottom * height).toInt().coerceIn(y0 + 1, height)

        return when (method) {
            SamplingMethod.AVERAGE -> average(pixels, width, x0, x1, y0, y1)
            SamplingMethod.CENTER_WEIGHTED -> weighted(pixels, width, x0, x1, y0, y1)
            SamplingMethod.DOMINANT -> dominant(pixels, width, x0, x1, y0, y1)
        }
    }

    private fun average(p: IntArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = p[y * w + x]
            r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; n++
        }
        return ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun weighted(p: IntArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
        val cx = (x0 + x1 - 1) / 2.0; val cy = (y0 + y1 - 1) / 2.0
        val sx = maxOf(1.0, (x1 - x0) / 2.0); val sy = maxOf(1.0, (y1 - y0) / 2.0)
        for (y in y0 until y1) for (x in x0 until x1) {
            val dx = (x - cx) / sx; val dy = (y - cy) / sy
            val weight = 1.0 / (1.0 + dx * dx + dy * dy)
            val c = p[y * w + x]
            r += (c shr 16 and 0xFF) * weight; g += (c shr 8 and 0xFF) * weight
            b += (c and 0xFF) * weight; total += weight
        }
        return (r / total).toInt().coerceIn(0,255) shl 16 or
            ((g / total).toInt().coerceIn(0,255) shl 8) or (b / total).toInt().coerceIn(0,255)
    }

    private fun dominant(p: IntArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        val bins = HashMap<Int, Int>()
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = p[y * w + x]
            val q = ((c shr 16 and 0xF0) shl 12) or ((c shr 8 and 0xF0) shl 4) or (c and 0xF0)
            bins[q] = (bins[q] ?: 0) + 1
        }
        return bins.maxByOrNull { it.value }?.key ?: 0
    }
}

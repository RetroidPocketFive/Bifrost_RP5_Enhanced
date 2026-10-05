package com.moonbench.bifrost.rp5

import kotlin.math.roundToInt

object ThumbstickColourProcessor {
    fun process(left: Int, right: Int, mode: ThumbstickColourMode): SampledColors = when (mode) {
        ThumbstickColourMode.AVERAGE -> SampledColors(left, right)
        ThumbstickColourMode.FIFTY_FIFTY -> {
            val mixed = mixHalf(left, right); SampledColors(mixed, mixed)
        }
        ThumbstickColourMode.CLOSE_MATCH -> SampledColors(left, right)
        ThumbstickColourMode.MOST_DOMINANT -> SampledColors(left, right)
        ThumbstickColourMode.DOMINANT_PRIME ->
            SampledColors(toDominantPrime(left), toDominantPrime(right))
    }

    private fun toDominantPrime(color: Int): Int {
        val r = color shr 16 and 0xFF; val g = color shr 8 and 0xFF; val b = color and 0xFF
        return when {
            r >= g && r >= b -> r shl 16
            g >= r && g >= b -> g shl 8
            else -> b
        }
    }

    private fun mixHalf(a: Int, b: Int): Int {
        val r = (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF)) / 2.0
        val g = (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF)) / 2.0
        val b2 = ((a and 0xFF) + (b and 0xFF)) / 2.0
        return (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b2.roundToInt()
    }
}
package com.moonbench.bifrost.rp5

import kotlin.math.sqrt

/** Deterministic helpers used by Phase 8 colour-validation tests. */
object FineTuneValidation {
    data class ColourMetrics(
        val red: Int,
        val green: Int,
        val blue: Int,
        val luminance: Double,
        val dominantPrimary: Primary,
    )

    enum class Primary { RED, GREEN, BLUE }

    fun metrics(color: Int): ColourMetrics {
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        val primary = when {
            r >= g && r >= b -> Primary.RED
            g >= r && g >= b -> Primary.GREEN
            else -> Primary.BLUE
        }
        return ColourMetrics(r, g, b, 0.2126 * r + 0.7152 * g + 0.0722 * b, primary)
    }

    fun rgbDistance(a: Int, b: Int): Double {
        val ar = a shr 16 and 0xFF; val ag = a shr 8 and 0xFF; val ab = a and 0xFF
        val br = b shr 16 and 0xFF; val bg = b shr 8 and 0xFF; val bb = b and 0xFF
        return sqrt(
            ((ar - br) * (ar - br) +
                (ag - bg) * (ag - bg) +
                (ab - bb) * (ab - bb)).toDouble()
        )
    }
}
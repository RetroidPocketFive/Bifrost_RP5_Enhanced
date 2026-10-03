package com.moonbench.bifrost.tools

/**
 * Suppresses tiny colour changes and requires a colour to remain stable for
 * a configurable number of frames before it is emitted to the LED pipeline.
 *
 * This keeps screen sampling responsive while preventing rapid hardware
 * updates caused by capture noise or subtle pixel changes.
 */
class ScreenColorStabilityFilter(
    private val deadband: Int = 4,
    private val requiredStableFrames: Int = 2,
) {
    private var emitted: ScreenColors? = null
    private var candidate: ScreenColors? = null
    private var candidateFrames: Int = 0

    fun offer(colors: ScreenColors): ScreenColors? {
        val previous = emitted
        if (previous == null) {
            emitted = colors
            candidate = null
            candidateFrames = 0
            return colors
        }

        if (withinDeadband(previous, colors)) {
            candidate = null
            candidateFrames = 0
            return null
        }

        val currentCandidate = candidate
        if (currentCandidate != null && withinDeadband(currentCandidate, colors)) {
            candidateFrames++
        } else {
            candidate = colors
            candidateFrames = 1
        }

        if (candidateFrames >= requiredStableFrames.coerceAtLeast(1)) {
            emitted = colors
            candidate = null
            candidateFrames = 0
            return colors
        }

        return null
    }

    fun reset() {
        emitted = null
        candidate = null
        candidateFrames = 0
    }

    private fun withinDeadband(a: ScreenColors, b: ScreenColors): Boolean =
        delta(a.leftColor, b.leftColor) <= deadband &&
            delta(a.rightColor, b.rightColor) <= deadband

    private fun delta(a: Int, b: Int): Int {
        val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
        return maxOf(dr, dg, db)
    }
}

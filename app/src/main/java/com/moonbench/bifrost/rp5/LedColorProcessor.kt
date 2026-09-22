package com.moonbench.bifrost.rp5

import kotlin.math.pow

object LedColorProcessor {
    fun apply(frame: LedFrame, brightness: Float = 1f, gamma: Float = 2.2f): LedFrame {
        require(brightness in 0f..1f)
        require(gamma > 0f)
        return LedFrame(
            transform(frame.left, brightness, gamma),
            transform(frame.right, brightness, gamma),
            frame.timestampNanos
        )
    }

    fun blend(from: LedFrame, to: LedFrame, amount: Float): LedFrame {
        val t = amount.coerceIn(0f, 1f)
        return LedFrame(
            lerp(from.left, to.left, t),
            lerp(from.right, to.right, t),
            to.timestampNanos
        )
    }

    private fun transform(color: Int, brightness: Float, gamma: Float): Int {
        fun channel(shift: Int): Int {
            val input = ((color shr shift) and 0xFF) / 255f
            val corrected = input.pow(gamma) * brightness
            return (corrected.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun c(shift: Int) = (((a shr shift) and 0xFF) +
            ((((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t)).roundToInt()
        return (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}

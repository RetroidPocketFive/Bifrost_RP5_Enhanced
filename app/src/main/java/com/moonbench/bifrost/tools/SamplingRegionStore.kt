package com.moonbench.bifrost.tools

import android.content.Context
import android.content.SharedPreferences

/**
 * A rectangular sampling region expressed as fractions (0..1) of the screen:
 * left/top is the upper-left corner, right/bottom the lower-right corner.
 *
 * Used by [ScreenAnalyzer] (and the sampling editor) so each stick can sample
 * an arbitrary, user-positioned area of the screen instead of the fixed
 * left-half / right-half split.
 */
data class SamplingRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(MIN_SIDE)
    val height: Float get() = (bottom - top).coerceAtLeast(MIN_SIDE)

    /** Clamp to valid in-screen fractions and enforce a minimum size. */
    fun clamped(): SamplingRegion {
        var l = left.coerceIn(0f, 1f - MIN_SIDE)
        var t = top.coerceIn(0f, 1f - MIN_SIDE)
        var r = right.coerceIn(MIN_SIDE, 1f)
        var b = bottom.coerceIn(MIN_SIDE, 1f)
        if (r - l < MIN_SIDE) r = (l + MIN_SIDE).coerceAtMost(1f)
        if (b - t < MIN_SIDE) b = (t + MIN_SIDE).coerceAtMost(1f)
        return SamplingRegion(l, t, r, b)
    }

    fun encode(): String = "$left,$top,$right,$bottom"

    companion object {
        const val MIN_SIDE = 0.05f

        val LEFT_HALF = SamplingRegion(0f, 0f, 0.5f, 1f)
        val RIGHT_HALF = SamplingRegion(0.5f, 0f, 1f, 1f)

        // 4:3 centre crop â€” useful for older console content with pillarboxing.
        val CENTER_4_3_LEFT: SamplingRegion
            get() = SamplingRegion(0.125f, 0f, 0.5f, 1f)
        val CENTER_4_3_RIGHT: SamplingRegion
            get() = SamplingRegion(0.5f, 0f, 0.875f, 1f)

        fun decode(value: String?): SamplingRegion? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(",")
            if (parts.size != 4) return null
            return runCatching {
                SamplingRegion(
                    parts[0].toFloat(), parts[1].toFloat(),
                    parts[2].toFloat(), parts[3].toFloat()
                ).clamped()
            }.getOrNull()
        }
    }
}

/**
 * Persists the per-stick sampling regions and whether custom regions are
 * enabled. ScreenAnalyzer reads these lazily (and re-reads periodically) so the
 * editor's changes apply live without restarting the LED service.
 */
object SamplingRegionStore {

    private const val PREFS_NAME = "bifrost_sampling_regions"
    private const val KEY_ENABLED = "regions_enabled"
    private const val KEY_LEFT = "left_region"
    private const val KEY_RIGHT = "right_region"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getLeft(context: Context): SamplingRegion =
        SamplingRegion.decode(prefs(context).getString(KEY_LEFT, null))
            ?: SamplingRegion.LEFT_HALF

    fun getRight(context: Context): SamplingRegion =
        SamplingRegion.decode(prefs(context).getString(KEY_RIGHT, null))
            ?: SamplingRegion.RIGHT_HALF

    fun setRegions(context: Context, left: SamplingRegion, right: SamplingRegion) {
        prefs(context).edit()
            .putString(KEY_LEFT, left.clamped().encode())
            .putString(KEY_RIGHT, right.clamped().encode())
            .apply()
    }
}
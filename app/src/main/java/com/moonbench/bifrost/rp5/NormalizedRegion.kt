package com.moonbench.bifrost.rp5

data class NormalizedRegion(
    val centerX: Float,
    val centerY: Float,
    val size: Float,
) {
    init {
        require(centerX in 0f..1f)
        require(centerY in 0f..1f)
        require(size > 0f && size <= 1f)
    }

    fun bounds(): Bounds {
        val half = size / 2f
        return Bounds(
            (centerX - half).coerceIn(0f, 1f),
            (centerY - half).coerceIn(0f, 1f),
            (centerX + half).coerceIn(0f, 1f),
            (centerY + half).coerceIn(0f, 1f)
        )
    }

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
}

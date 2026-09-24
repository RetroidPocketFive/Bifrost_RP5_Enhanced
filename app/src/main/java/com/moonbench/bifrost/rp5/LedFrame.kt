package com.moonbench.bifrost.rp5

data class LedFrame(
    val left: Int,
    val right: Int,
    val timestampNanos: Long = System.nanoTime(),
) {
    init {
        require(left in 0..0xFFFFFF)
        require(right in 0..0xFFFFFF)
    }

    fun isSameColorAs(other: LedFrame): Boolean = left == other.left && right == other.right
}

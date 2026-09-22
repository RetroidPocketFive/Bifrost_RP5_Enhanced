package com.moonbench.bifrost.rp5

/**
 * In-memory LED driver used by unit tests and future diagnostics tooling.
 */
class MockLedDriver : LedDriver {
    private val frames = mutableListOf<LedFrame>()
    var isCleared: Boolean = false
        private set

    val writes: List<LedFrame>
        get() = frames.toList()

    override fun write(frame: LedFrame) {
        frames += frame
        isCleared = false
    }

    override fun clear() {
        isCleared = true
    }
}

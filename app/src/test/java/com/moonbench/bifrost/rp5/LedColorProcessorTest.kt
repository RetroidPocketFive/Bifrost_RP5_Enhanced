package com.moonbench.bifrost.rp5

import org.junit.Assert.assertEquals
import org.junit.Test

class LedColorProcessorTest {
    @Test fun brightnessZeroTurnsFrameOff() {
        val result = LedColorProcessor.apply(LedFrame(0xFFFFFF, 0xFF0000), brightness = 0f)
        assertEquals(0, result.left)
        assertEquals(0, result.right)
    }

    @Test fun blendKeepsEndpoints() {
        val a = LedFrame(0x000000, 0xFFFFFF)
        assertEquals(a.left, LedColorProcessor.blend(a, a, 0f).left)
        assertEquals(a.right, LedColorProcessor.blend(a, a, 1f).right)
    }
}

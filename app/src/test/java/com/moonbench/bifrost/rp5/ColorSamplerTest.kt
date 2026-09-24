package com.moonbench.bifrost.rp5

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSamplerTest {
    @Test fun samplesIndependentLeftAndRightRegions() {
        val pixels = IntArray(4) { if (it % 2 == 0) 0xFF0000 else 0x0000FF }
        val result = ColorSampler(SamplingMethod.AVERAGE).sample(
            pixels, 4, 1,
            NormalizedRegion(0.125f, 0.5f, 0.2f),
            NormalizedRegion(0.875f, 0.5f, 0.2f)
        )
        assertEquals(0xFF0000, result.left)
        assertEquals(0x0000FF, result.right)
    }

    @Test fun regionBoundsAreNormalized() {
        val bounds = NormalizedRegion(0.1f, 0.2f, 0.5f).bounds()
        assertEquals(0f, bounds.left, 0.0001f)
        assertEquals(0f, bounds.top, 0.0001f)
        assertEquals(0.35f, bounds.right, 0.0001f)
        assertEquals(0.45f, bounds.bottom, 0.0001f)
    }
}

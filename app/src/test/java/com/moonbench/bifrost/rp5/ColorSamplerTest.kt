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

    @Test fun bucketSamplingBalancesUnevenDetail() {
        val pixels = IntArray(9) { if (it == 0) 0xFF0000 else 0x0000FF }
        val result = ColorSampler(SamplingMethod.BUCKET).sample(
            pixels, 3, 3,
            NormalizedRegion(0.5f, 0.5f, 1f),
            NormalizedRegion(0.5f, 0.5f, 1f)
        )
        assertEquals(0x1C00E2, result.left)
        assertEquals(0x1C00E2, result.right)
    }

    @Test fun edgeRejectedSamplingIgnoresOuterPixels() {
        val pixels = IntArray(5 * 5) { 0x00FF00 }
        pixels[0] = 0xFF0000
        pixels[24] = 0x0000FF
        val result = ColorSampler(SamplingMethod.EDGE_REJECTED_WEIGHTED).sample(
            pixels, 5, 5,
            NormalizedRegion(0.5f, 0.5f, 1f),
            NormalizedRegion(0.5f, 0.5f, 1f)
        )
        assertEquals(0x00FF00, result.left)
        assertEquals(0x00FF00, result.right)
    }

    @Test fun regionBoundsAreNormalized() {
        val bounds = NormalizedRegion(0.1f, 0.2f, 0.5f).bounds()
        assertEquals(0f, bounds.left, 0.0001f)
        assertEquals(0f, bounds.top, 0.0001f)
        assertEquals(0.35f, bounds.right, 0.0001f)
        assertEquals(0.45f, bounds.bottom, 0.0001f)
    }
}

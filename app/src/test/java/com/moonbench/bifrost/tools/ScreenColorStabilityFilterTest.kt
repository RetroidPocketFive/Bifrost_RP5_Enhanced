package com.moonbench.bifrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenColorStabilityFilterTest {
    @Test fun firstSampleEmitsImmediately() {
        val filter = ScreenColorStabilityFilter(deadband = 4, requiredStableFrames = 2)
        assertEquals(ScreenColors(0xFF0000, 0x0000FF), filter.offer(ScreenColors(0xFF0000, 0x0000FF)))
    }

    @Test fun tinyChangesInsideDeadbandAreSuppressed() {
        val filter = ScreenColorStabilityFilter(deadband = 4, requiredStableFrames = 2)
        filter.offer(ScreenColors(100, 100, ))
        assertNull(filter.offer(ScreenColors(103, 104)))
    }

    @Test fun changedColourMustBeStableBeforeEmission() {
        val filter = ScreenColorStabilityFilter(deadband = 2, requiredStableFrames = 2)
        filter.offer(ScreenColors(0x101010, 0x202020))
        assertNull(filter.offer(ScreenColors(0x202020, 0x303030)))
        assertEquals(ScreenColors(0x202020, 0x303030), filter.offer(ScreenColors(0x202020, 0x303030)))
    }

    @Test fun resetAllowsNextSampleToEmitImmediately() {
        val filter = ScreenColorStabilityFilter(deadband = 4, requiredStableFrames = 2)
        filter.offer(ScreenColors(0x101010, 0x202020))
        filter.reset()
        assertEquals(ScreenColors(0x909090, 0x808080), filter.offer(ScreenColors(0x909090, 0x808080)))
    }
}

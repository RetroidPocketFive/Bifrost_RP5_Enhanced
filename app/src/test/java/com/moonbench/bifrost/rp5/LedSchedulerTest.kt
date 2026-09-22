package com.moonbench.bifrost.rp5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class LedSchedulerTest {
    @Test
    fun duplicateFramesAreSuppressed() {
        val driver = MockLedDriver()
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            val scheduler = LedScheduler(driver, executor, refreshHz = 30, gamma = 1f)
            val frame = LedFrame(0x102030, 0x405060)

            scheduler.submit(frame)
            scheduler.tickForTest()
            scheduler.submit(frame)
            scheduler.tickForTest()

            assertEquals(1, driver.writes.size)
            assertEquals(frame.left, driver.writes.single().left)
            assertEquals(frame.right, driver.writes.single().right)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun clearStopsAndClearsOutput() {
        val driver = MockLedDriver()
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            val scheduler = LedScheduler(driver, executor, gamma = 1f)
            scheduler.submit(LedFrame(0xFFFFFF, 0xFFFFFF))
            scheduler.tickForTest()

            scheduler.stop()

            assertTrue(driver.isCleared)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun brightnessZeroProducesOffFrame() {
        val driver = MockLedDriver()
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            val scheduler = LedScheduler(driver, executor, brightness = 0f)
            scheduler.submit(LedFrame(0xFFFFFF, 0xFFFFFF))
            scheduler.tickForTest()

            assertEquals(0, driver.writes.single().left)
            assertEquals(0, driver.writes.single().right)
        } finally {
            executor.shutdownNow()
        }
    }
}

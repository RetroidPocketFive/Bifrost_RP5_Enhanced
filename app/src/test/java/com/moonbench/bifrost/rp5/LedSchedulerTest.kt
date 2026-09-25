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
    @Test
    fun mergesPartialLeftAndRightFrames() {
        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        try {
            val driver = MockLedDriver()
            val scheduler = LedScheduler(driver, executor, refreshHz = 60, gamma = 1f)
            scheduler.submit(LedFrame(left = 0x112233, right = 0, leftTop = true, leftBottom = true, rightTop = false, rightBottom = false))
            scheduler.submit(LedFrame(left = 0, right = 0xAABBCC, leftTop = false, leftBottom = false, rightTop = true, rightBottom = true))
            scheduler.tickForTest()

            val written = driver.writes.last()
            assertEquals(0x112233, written.left)
            assertEquals(0xAABBCC, written.right)
            assertTrue(written.leftTop && written.leftBottom)
            assertTrue(written.rightTop && written.rightBottom)
        } finally {
            executor.shutdownNow()
        }
    }

}

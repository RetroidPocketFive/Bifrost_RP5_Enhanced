package com.moonbench.bifrost.rp5

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LedScheduler(
    private val driver: LedDriver,
    private val executor: ScheduledExecutorService,
    refreshHz: Int = 30,
    private val brightness: Float = 1f,
    private val gamma: Float = 2.2f,
    private val smoothing: Float = 0f,
) {
    private val pending = AtomicReference<LedFrame?>(null)
    private var lastWritten: LedFrame? = null
    private var task: ScheduledFuture<*>? = null
    private var current: LedFrame? = null

    private val periodMs = (1000.0 / refreshHz.coerceIn(1, 120)).toLong().coerceAtLeast(1L)

    init {
        require(brightness in 0f..1f)
        require(gamma > 0f)
        require(smoothing in 0f..1f)
    }

    fun start() {
        if (task != null) return
        task = executor.scheduleAtFixedRate({ tick() }, 0, periodMs, TimeUnit.MILLISECONDS)
    }

    fun submit(frame: LedFrame) {
        pending.getAndUpdate { previous ->
            mergeFrames(previous, frame)
        }
    }

    /**
     * Some existing Bifrost animations emit the left and right sticks as
     * separate partial frames. The scheduler must merge those updates instead
     * of letting the second write erase the first one.
     */
    private fun mergeFrames(previous: LedFrame?, incoming: LedFrame): LedFrame {
        if (previous == null) return incoming
        val leftChanged = incoming.leftTop || incoming.leftBottom
        val rightChanged = incoming.rightTop || incoming.rightBottom

        return LedFrame(
            left = if (leftChanged) incoming.left else previous.left,
            right = if (rightChanged) incoming.right else previous.right,
            timestampNanos = incoming.timestampNanos,
            leftTop = previous.leftTop || incoming.leftTop,
            leftBottom = previous.leftBottom || incoming.leftBottom,
            rightTop = previous.rightTop || incoming.rightTop,
            rightBottom = previous.rightBottom || incoming.rightBottom
        )
    }

    fun stop(clear: Boolean = true) {
        task?.cancel(false)
        task = null
        pending.set(null)
        if (clear) {
            driver.clear()
            lastWritten = null
            current = null
        }
    }

    fun tickForTest() = tick()

    private fun tick() {
        val target = pending.get() ?: return
        val next = if (current == null || smoothing <= 0f) {
            target
        } else {
            LedColorProcessor.blend(current!!, target, 1f - smoothing)
        }
        current = next
        val processed = LedColorProcessor.apply(next, brightness, gamma)
        if (lastWritten?.isSameColorAs(processed) == true) {
            return
        }
        driver.write(processed)
        lastWritten = processed
    }
}

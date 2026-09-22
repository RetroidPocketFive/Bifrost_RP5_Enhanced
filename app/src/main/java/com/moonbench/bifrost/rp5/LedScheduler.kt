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

    private val periodMs = (1000L / refreshHz.coerceIn(1, 120))

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
        pending.set(frame)
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
        if (processed.isSameColorAs(lastWritten ?: returnWriteSentinel(processed))) {
            return
        }
        driver.write(processed)
        lastWritten = processed
    }

    private fun returnWriteSentinel(frame: LedFrame): LedFrame {
        return LedFrame(-1 and 0xFFFFFF, -1 and 0xFFFFFF, frame.timestampNanos)
    }
}

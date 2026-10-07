package com.moonbench.bifrost.rp5

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.moonbench.bifrost.services.BifrostAccessibilityService
import com.moonbench.bifrost.tools.LedController
import java.util.concurrent.Executor

class ThumbstickLivePreview(
    private val context: Context,
    private val onFrame: (Bitmap?) -> Unit,
    private val onColors: (Int, Int) -> Unit,
    private val displayId: Int = Display.DEFAULT_DISPLAY,
    private val brightness: () -> Int = { 255 },
    private val beforeCapture: (() -> Unit)? = null,
    private val afterCapture: (() -> Unit)? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val led = LedController(context)
    private var running = false
    private var singleFrame = false
    private var bitmap: Bitmap? = null
    private var left = NormalizedRegion(.25f, .78f, .18f)
    private var right = NormalizedRegion(.75f, .78f, .18f)
    private var mode = ThumbstickColourMode.AVERAGE

    companion object {
        private const val MAX_PREVIEW_WIDTH = 640
        private const val CAPTURE_RENDER_DELAY_MS = 50L
    }

    private val capture = object : AccessibilityService.TakeScreenshotCallback {
        override fun onSuccess(s: AccessibilityService.ScreenshotResult) {
            try {
                val hw = Bitmap.wrapHardwareBuffer(s.hardwareBuffer, s.colorSpace)
                val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                hw?.recycle()
                if (sw != null) {
                    val frame = if (sw.width > MAX_PREVIEW_WIDTH) {
                        val scaledHeight = (sw.height * MAX_PREVIEW_WIDTH.toFloat() / sw.width)
                            .toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(sw, MAX_PREVIEW_WIDTH, scaledHeight, true).also {
                            sw.recycle()
                        }
                    } else {
                        sw
                    }
                    bitmap = frame
                    onFrame(frame)
                    sample(frame)
                }
                if (singleFrame) {
                    running = false
                    singleFrame = false
                }
            } finally {
                s.hardwareBuffer.close()
                afterCapture?.invoke()
                if (running) schedule()
            }
        }

        override fun onFailure(errorCode: Int) {
            afterCapture?.invoke()
            if (running) schedule(400L)
        }
    }

    fun start(single: Boolean = false) {
        singleFrame = single
        if (!running) {
            running = true
            request()
        }
    }

    fun captureOnce() {
        start(single = true)
    }

    fun stop() {
        running = false
        singleFrame = false
        handler.removeCallbacksAndMessages(null)
        bitmap = null
        afterCapture?.invoke()
    }

    fun preview(l: NormalizedRegion, r: NormalizedRegion, m: ThumbstickColourMode = mode) {
        left = l
        right = r
        mode = m
        bitmap?.let(::sample)
    }

    private fun sample(frame: Bitmap) {
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        val method = when (mode) {
            ThumbstickColourMode.MOST_DOMINANT,
            ThumbstickColourMode.DOMINANT_PRIME -> SamplingMethod.DOMINANT
            ThumbstickColourMode.CLOSE_MATCH -> SamplingMethod.CENTER_WEIGHTED
            else -> SamplingMethod.AVERAGE
        }
        val colors = ColorSampler(method).sample(pixels, frame.width, frame.height, left, right)
        val processed = ThumbstickColourProcessor.process(colors.left, colors.right, mode)
        onColors(processed.left, processed.right)
        led.setLedColorDual(
            Color.red(processed.left),
            Color.green(processed.left),
            Color.blue(processed.left),
            Color.red(processed.right),
            Color.green(processed.right),
            Color.blue(processed.right),
            brightness(),
        )
    }

    private fun request() {
        if (!running) return
        val service = BifrostAccessibilityService.instance
        if (service == null || !BifrostAccessibilityService.isEnabled(service)) {
            schedule(400L)
            return
        }

        beforeCapture?.invoke()
        handler.postDelayed({
            if (!running) {
                afterCapture?.invoke()
                return@postDelayed
            }
            runCatching {
                service.takeScreenshot(
                    displayId,
                    Executor { command -> handler.post(command) },
                    capture,
                )
            }.onFailure {
                afterCapture?.invoke()
                schedule(400L)
            }
        }, CAPTURE_RENDER_DELAY_MS)
    }

    private fun schedule(delay: Long = 160L) {
        if (running) handler.postDelayed(::request, delay)
    }
}

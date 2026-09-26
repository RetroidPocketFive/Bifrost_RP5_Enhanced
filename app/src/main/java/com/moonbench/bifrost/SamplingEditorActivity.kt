package com.moonbench.bifrost

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.BifrostAccessibilityService
import com.moonbench.bifrost.tools.SamplingRegion
import com.moonbench.bifrost.tools.SamplingRegionStore
import com.moonbench.bifrost.ui.SamplingCanvasView
import java.io.InputStream
import java.util.concurrent.Executor

/**
 * Lets the user position and resize the per-stick screen sampling regions used
 * by Ambient / Ambi Aurora. A reference screenshot is taken via the
 * accessibility service (no MediaProjection, so no conflict with the running
 * LED service) and shown as the canvas background so the user can see what each
 * rectangle covers. Regions are saved as screen fractions and applied live by
 * ScreenAnalyzer on its next capture frame.
 *
 * The screenshot is DELAYED (a 5-second countdown): the editor itself would be
 * captured otherwise. The user taps Capture, switches to their game within the
 * countdown, and the screenshot captures the game instead of this editor.
 *
 * Custom regions take priority over Single Color mode for Ambient / Ambi
 * Aurora. Enabling/disabling regions requires toggling the effect off/on.
 */
class SamplingEditorActivity : AppCompatActivity() {

    private lateinit var canvas: SamplingCanvasView
    private lateinit var leftSwatch: View
    private lateinit var rightSwatch: View
    private lateinit var placeholder: TextView
    private lateinit var captureButton: MaterialButton
    private var referenceBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureCountdownActive = false

    // Image picker for importing a reference screenshot from the user's files.
    // This is the reliable path when the accessibility screenshot capture
    // isn't available or fails â€” the user can grab a screenshot of their game
    // with any tool and import it here.
    private val importImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importImageFromUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sampling_editor)

        val toolbar = findViewById<MaterialToolbar>(R.id.samplingToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        canvas = findViewById(R.id.samplingCanvas)
        leftSwatch = findViewById(R.id.leftSwatch)
        rightSwatch = findViewById(R.id.rightSwatch)
        placeholder = findViewById(R.id.samplingPlaceholder)
        captureButton = findViewById(R.id.btnCapture)

        // Match the canvas content rect to the real screen aspect so rectangles
        // represent the same screen area whether or not a screenshot is present.
        val metrics = getDisplayMetrics()
        canvas.screenAspectRatio = if (metrics.heightPixels > 0)
            metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat() else 16f / 9f

        canvas.setRegions(
            SamplingRegionStore.getLeft(this),
            SamplingRegionStore.getRight(this)
        )

        canvas.listener = object : SamplingCanvasView.OnRegionsChangedListener {
            override fun onRegionsChanged(left: SamplingRegion, right: SamplingRegion) {
                updateSwatches(left, right)
            }
        }

        captureButton.setOnClickListener { startDelayedCapture() }
        findViewById<MaterialButton>(R.id.btnImport).setOnClickListener {
            importImageLauncher.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.btnReset).setOnClickListener {
            canvas.setRegions(SamplingRegion.RP5_LEFT_DEFAULT, SamplingRegion.RP5_RIGHT_DEFAULT, notify = true)
        }
        findViewById<MaterialButton>(R.id.btnDisable).setOnClickListener { disableAndFinish() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveAndFinish() }

        updateSwatches(canvas.leftRegion, canvas.rightRegion)
        updatePlaceholder()
    }

    private fun updatePlaceholder() {
        placeholder.visibility = if (referenceBitmap == null && !captureCountdownActive) View.VISIBLE else View.GONE
    }

    private fun updateSwatches(left: SamplingRegion, right: SamplingRegion) {
        val bmp = referenceBitmap
        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
            leftSwatch.setBackgroundColor(averageColor(bmp, left))
            rightSwatch.setBackgroundColor(averageColor(bmp, right))
        }
    }

    private fun averageColor(bmp: Bitmap, region: SamplingRegion): Int {
        val x0 = (region.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val x1 = (region.right * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = (region.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val y1 = (region.bottom * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
        var r = 0; var g = 0; var b = 0; var n = 0
        val stepX = maxOf(1, (x1 - x0) / 24)
        val stepY = maxOf(1, (y1 - y0) / 24)
        for (y in y0 until y1 step stepY) {
            for (x in x0 until x1 step stepX) {
                val c = bmp.getPixel(x, y)
                r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
            }
        }
        if (n == 0) return Color.BLACK
        return Color.rgb(r / n, g / n, b / n)
    }

    private fun startDelayedCapture() {
        val service = BifrostAccessibilityService.instance
        if (service == null || !BifrostAccessibilityService.isEnabled(service)) {
            Toast.makeText(this, "Enable the Bifrost accessibility service first, then tap Capture again.", Toast.LENGTH_LONG).show()
            return
        }
        if (captureCountdownActive) return
        captureCountdownActive = true
        captureButton.isEnabled = false
        captureButton.text = "Switch to gameâ€¦"
        updatePlaceholder()

        Toast.makeText(this, "Capturing in 5s â€” switch to your game now.", Toast.LENGTH_LONG).show()
        // No further toasts: a countdown toast could overlay the screenshot.
        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            captureCountdownActive = false
            captureButton.isEnabled = true
            captureButton.text = "Capture"
            takeReferenceScreenshot(service)
        }, 5000L)
    }

    /**
     * Import an image the user picked (a screenshot of their game, or any
     * reference picture) and use it as the editor canvas background. The image
     * is downscaled to a bounded width and decoded as a software ARGB_8888
     * bitmap so swatch colour sampling is safe and fast. This is reference-only;
     * runtime sampling still uses the normal Ambient capture path.
     */
    private fun importImageFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input: InputStream ->
                val metrics = getDisplayMetrics()
                val targetW = 540
                val targetH = (targetW * metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat())
                    .toInt().coerceAtLeast(120)

                val bounds = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    runOnUiThread {
                        Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)

                contentResolver.openInputStream(uri)?.use { dec ->
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = android.graphics.BitmapFactory.decodeStream(dec, null, opts) ?: return
                    val scaled = if (decoded.width != targetW || decoded.height != targetH) {
                        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { decoded.recycle() }
                    } else {
                        decoded
                    }
                    referenceBitmap?.recycle()
                    referenceBitmap = scaled
                    runOnUiThread {
                        canvas.backgroundBitmap = referenceBitmap
                        updatePlaceholder()
                        updateSwatches(canvas.leftRegion, canvas.rightRegion)
                        Toast.makeText(this, "Image imported â€” position your rectangles over it.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Throwable) {
            runOnUiThread {
                Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
        var sample = 1
        var w = srcW
        var h = srcH
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun takeReferenceScreenshot(service: AccessibilityService) {
        try {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                Executor { cmd -> runOnUiThread(cmd) },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (isFinishing || isDestroyed) {
                            screenshot.hardwareBuffer.close()
                            return
                        }
                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        screenshot.hardwareBuffer.close()
                        if (hw == null) {
                            runOnUiThread {
                                Toast.makeText(this@SamplingEditorActivity, "Screenshot unavailable.", Toast.LENGTH_LONG).show()
                            }
                            return
                        }
                        // Accessibility screenshots are hardware-backed on Android 13.
                        // A hardware bitmap cannot be drawn onto a software Canvas, so copy
                        // it into a normal ARGB_8888 bitmap first. This also makes getPixel()
                        // safe for the swatch sampling below.
                        val softwareSource = try {
                            hw.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            hw.recycle()
                        }
                        if (softwareSource == null) {
                            runOnUiThread {
                                Toast.makeText(this@SamplingEditorActivity, "Could not convert screenshot.", Toast.LENGTH_LONG).show()
                            }
                            return
                        }

                        val metrics = getDisplayMetrics()
                        val targetW = 360
                        val targetH = (targetW * metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat())
                            .toInt().coerceAtLeast(120)
                        val software = if (softwareSource.width != targetW || softwareSource.height != targetH) {
                            Bitmap.createScaledBitmap(softwareSource, targetW, targetH, true).also {
                                softwareSource.recycle()
                            }
                        } else {
                            softwareSource
                        }
                        referenceBitmap?.recycle()
                        referenceBitmap = software
                        runOnUiThread {
                            canvas.backgroundBitmap = referenceBitmap
                            updatePlaceholder()
                            updateSwatches(canvas.leftRegion, canvas.rightRegion)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (isFinishing || isDestroyed) return
                        runOnUiThread {
                            captureButton.isEnabled = true
                            updatePlaceholder()
                            Toast.makeText(this@SamplingEditorActivity, "Screenshot failed (error $errorCode). You can still position rectangles.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "Accessibility permission unavailable.", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Screenshot unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun saveAndFinish() {
        SamplingRegionStore.setRegions(this, canvas.leftRegion, canvas.rightRegion)
        SamplingRegionStore.setEnabled(this, true)
        Toast.makeText(this, "Sampling areas saved. If Ambient is running, toggle it off/on to apply the new capture grid.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun disableAndFinish() {
        SamplingRegionStore.setEnabled(this, false)
        Toast.makeText(this, "Custom sampling areas off â€” using left/right split. Toggle the effect off/on to apply.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
package com.moonbench.bifrost

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.BifrostAccessibilityService
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.SamplingRegion
import com.moonbench.bifrost.tools.SamplingRegionStore
import com.moonbench.bifrost.rp5.LedColorCalibration
import com.moonbench.bifrost.ui.SamplingCanvasView
import java.io.InputStream
import java.util.concurrent.Executor

class SamplingEditorActivity : AppCompatActivity() {

    private enum class Page { CHOOSER, EDITOR, COLOR_TEST }
    private var page = Page.CHOOSER
    private var activeStick = 0
    private var colorIndex = 0

    private lateinit var chooserPanel: View
    private lateinit var editorPanel: View
    private lateinit var colorTestPanel: View
    private lateinit var canvas: SamplingCanvasView
    private lateinit var editorTitle: TextView
    private lateinit var editorSubtitle: TextView
    private lateinit var placeholder: TextView
    private lateinit var leftReferenceButton: MaterialButton
    private lateinit var rightReferenceButton: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var testColorName: TextView
    private lateinit var matchStickName: TextView
    private lateinit var targetColorSwatch: View
    private lateinit var rawColorSwatch: View
    private lateinit var redSeek: SeekBar
    private lateinit var greenSeek: SeekBar
    private lateinit var blueSeek: SeekBar
    private lateinit var redValue: TextView
    private lateinit var greenValue: TextView
    private lateinit var blueValue: TextView
    private var matchStick = LedColorCalibration.Stick.LEFT
    private var matchAdjusting = false

    private var referenceBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureCountdownActive = false

    private data class TestColor(val name: String, val color: Int)
    private val testColors = listOf(
        TestColor("RED", Color.rgb(255, 0, 0)),
        TestColor("GREEN", Color.GREEN),
        TestColor("BLUE", Color.BLUE),
        TestColor("WHITE", Color.WHITE),
    )

    private val importImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) importImageFromUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sampling_editor)

        val toolbar = findViewById<MaterialToolbar>(R.id.samplingToolbar)
        toolbar.setNavigationOnClickListener {
            when (page) {
                Page.CHOOSER -> finish()
                Page.EDITOR -> if (activeStick == 0) showChooser() else showEditor(0)
                Page.COLOR_TEST -> showEditor(1)
            }
        }

        chooserPanel = findViewById(R.id.chooserPanel)
        editorPanel = findViewById(R.id.editorPanel)
        colorTestPanel = findViewById(R.id.colorTestPanel)
        canvas = findViewById(R.id.samplingCanvas)
        editorTitle = findViewById(R.id.editorTitle)
        editorSubtitle = findViewById(R.id.editorSubtitle)
        placeholder = findViewById(R.id.samplingPlaceholder)
        leftReferenceButton = findViewById(R.id.leftReferenceButton)
        rightReferenceButton = findViewById(R.id.rightReferenceButton)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)
        testColorName = findViewById(R.id.testColorName)
        matchStickName = findViewById(R.id.matchStickName)
        targetColorSwatch = findViewById(R.id.targetColorSwatch)
        rawColorSwatch = findViewById(R.id.rawColorSwatch)
        redSeek = findViewById(R.id.redSeek)
        greenSeek = findViewById(R.id.greenSeek)
        blueSeek = findViewById(R.id.blueSeek)
        redValue = findViewById(R.id.redValue)
        greenValue = findViewById(R.id.greenValue)
        blueValue = findViewById(R.id.blueValue)

        val metrics = getDisplayMetrics()
        canvas.screenAspectRatio =
            if (metrics.heightPixels > 0) metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
            else 16f / 9f

        canvas.setRegions(
            SamplingRegionStore.getLeft(this),
            SamplingRegionStore.getRight(this)
        )

        canvas.listener = object : SamplingCanvasView.OnRegionsChangedListener {
            override fun onRegionsChanged(left: SamplingRegion, right: SamplingRegion) {
                updateReferenceButtons(left, right)
            }
        }

        findViewById<MaterialButton>(R.id.btnChooseLive).setOnClickListener {
            showEditor(0)
            startDelayedCapture()
        }
        findViewById<MaterialButton>(R.id.btnChooseStill).setOnClickListener {
            showEditor(0)
            importImageLauncher.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.btnRecapture).setOnClickListener {
            startDelayedCapture()
        }
        findViewById<MaterialButton>(R.id.btnReset).setOnClickListener {
            canvas.setRegions(
                SamplingRegion.RP5_LEFT_DEFAULT,
                SamplingRegion.RP5_RIGHT_DEFAULT,
                notify = true
            )
        }
        findViewById<MaterialButton>(R.id.btnDisable).setOnClickListener {
            SamplingRegionStore.setEnabled(this, false)
            Toast.makeText(
                this,
                "Custom sampling areas disabled. Ambient will use its normal sampling split.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        leftReferenceButton.setOnClickListener {
            activeStick = 0
            showEditor(0)
            runLedTest(canvasColor(canvas.leftRegion), Color.BLACK)
        }
        rightReferenceButton.setOnClickListener {
            activeStick = 1
            showEditor(1)
            runLedTest(Color.BLACK, canvasColor(canvas.rightRegion))
        }

        btnBack.setOnClickListener {
            if (activeStick == 0) showChooser() else showEditor(0)
        }
        btnNext.setOnClickListener {
            if (activeStick == 0) showEditor(1) else showColorTest()
        }

        findViewById<MaterialButton>(R.id.btnMatchLeft).setOnClickListener {
            matchStick = LedColorCalibration.Stick.LEFT
            loadMatchCommand()
        }
        findViewById<MaterialButton>(R.id.btnMatchRight).setOnClickListener {
            matchStick = LedColorCalibration.Stick.RIGHT
            loadMatchCommand()
        }
        findViewById<MaterialButton>(R.id.btnTestAdjusted).setOnClickListener {
            testAdjustedCommand()
        }
        findViewById<MaterialButton>(R.id.btnSavePrimary).setOnClickListener {
            saveCurrentPrimary()
        }
        findViewById<MaterialButton>(R.id.btnResetLedCalibration).setOnClickListener {
            LedColorCalibration.reset(this, matchStick)
            loadMatchCommand()
            Toast.makeText(this, matchStickLabel() + " LED calibration reset.", Toast.LENGTH_SHORT).show()
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!matchAdjusting) updateMatchValues()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        redSeek.setOnSeekBarChangeListener(seekListener)
        greenSeek.setOnSeekBarChangeListener(seekListener)
        blueSeek.setOnSeekBarChangeListener(seekListener)
        findViewById<MaterialButton>(R.id.btnColorBack).setOnClickListener {
            if (colorIndex == 0) showEditor(1)
            else {
                colorIndex--
                updateColorTest()
            }
        }
        findViewById<MaterialButton>(R.id.btnColorNext).setOnClickListener {
            if (colorIndex < testColors.lastIndex) {
                saveCurrentPrimary()
                colorIndex++
                updateColorTest()
            } else {
                Toast.makeText(
                    this,
                    "White is a verification colour. Save & Apply when the stick looks correct.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnFinishCalibration).setOnClickListener {
            if (colorIndex <= 2) saveCurrentPrimary()
            Toast.makeText(
                this,
                "LED colour calibration saved for normal Ambient / Ambi Aurora output.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        updateReferenceButtons(canvas.leftRegion, canvas.rightRegion)
        showChooser()
    }

    private fun showChooser() {
        page = Page.CHOOSER
        chooserPanel.visibility = View.VISIBLE
        editorPanel.visibility = View.GONE
        colorTestPanel.visibility = View.GONE
    }

    private fun showEditor(stick: Int) {
        page = Page.EDITOR
        activeStick = stick.coerceIn(0, 1)
        chooserPanel.visibility = View.GONE
        editorPanel.visibility = View.VISIBLE
        colorTestPanel.visibility = View.GONE

        canvas.focus = if (activeStick == 0) SamplingCanvasView.Focus.LEFT else SamplingCanvasView.Focus.RIGHT
        editorTitle.text = if (activeStick == 0) "LEFT STICK" else "RIGHT STICK"
        editorSubtitle.text =
            "Zoomed view. Drag the box to move it; drag a corner to resize. Tap the color reference to test the physical LED."
        btnBack.text = if (activeStick == 0) "BACK" else "LEFT STICK"
        btnNext.text = if (activeStick == 0) "RIGHT STICK →" else "COLOR MATCH →"
        updateReferenceButtons(canvas.leftRegion, canvas.rightRegion)
        updatePlaceholder()
    }

    private fun showColorTest() {
        page = Page.COLOR_TEST
        colorIndex = 0
        chooserPanel.visibility = View.GONE
        editorPanel.visibility = View.GONE
        colorTestPanel.visibility = View.VISIBLE
        updateColorTest()
    }

    private fun updateColorTest() {
        val test = testColors[colorIndex]
        targetColorSwatch.setBackgroundColor(test.color)
        testColorName.text = test.name + "  (" + (colorIndex + 1) + "/" + testColors.size + ")"
        loadMatchCommand()
    }

    private fun matchStickLabel(): String =
        if (matchStick == LedColorCalibration.Stick.LEFT) "LEFT" else "RIGHT"

    private fun loadMatchCommand() {
        matchStickName.text = "SELECTED: " + matchStickLabel()
        val primary = when (colorIndex.coerceAtMost(2)) {
            0 -> LedColorCalibration.Primary.RED
            1 -> LedColorCalibration.Primary.GREEN
            else -> LedColorCalibration.Primary.BLUE
        }
        val command = if (colorIndex <= 2) {
            LedColorCalibration.getPrimaryCommand(this, matchStick, primary)
        } else {
            LedColorCalibration.CommandColor(
                Color.red(testColors[colorIndex].color),
                Color.green(testColors[colorIndex].color),
                Color.blue(testColors[colorIndex].color)
            )
        }
        matchAdjusting = true
        redSeek.progress = command.red
        greenSeek.progress = command.green
        blueSeek.progress = command.blue
        matchAdjusting = false
        updateMatchValues()
    }

    private fun updateMatchValues() {
        val command = Color.rgb(redSeek.progress, greenSeek.progress, blueSeek.progress)
        redValue.text = "R " + redSeek.progress
        greenValue.text = "G " + greenSeek.progress
        blueValue.text = "B " + blueSeek.progress
        rawColorSwatch.setBackgroundColor(command)
    }

    private fun testAdjustedCommand() {
        val command = Color.rgb(redSeek.progress, greenSeek.progress, blueSeek.progress)
        val left = if (matchStick == LedColorCalibration.Stick.LEFT) command else Color.BLACK
        val right = if (matchStick == LedColorCalibration.Stick.RIGHT) command else Color.BLACK
        runLedTest(left, right, true)
        Toast.makeText(
            this,
            "Raw " + matchStickLabel() + " output sent. Adjust RGB until it visually matches the target.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveCurrentPrimary() {
        if (colorIndex > 2) {
            Toast.makeText(this, "WHITE is verification only; it is not a matrix primary.", Toast.LENGTH_LONG).show()
            return
        }
        val primary = LedColorCalibration.Primary.values()[colorIndex]
        LedColorCalibration.setPrimaryCommand(
            this,
            matchStick,
            primary,
            LedColorCalibration.CommandColor(redSeek.progress, greenSeek.progress, blueSeek.progress)
        )
        Toast.makeText(
            this,
            matchStickLabel() + " " + primary.name + " correction saved.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updatePlaceholder() {
        placeholder.visibility =
            if (referenceBitmap == null && !captureCountdownActive) View.VISIBLE else View.GONE
    }

    private fun updateReferenceButtons(left: SamplingRegion, right: SamplingRegion) {
        val leftColor = if (referenceBitmap != null) canvasColor(left) else Color.rgb(40, 130, 255)
        val rightColor = if (referenceBitmap != null) canvasColor(right) else Color.rgb(255, 130, 30)
        leftReferenceButton.text = "LEFT  " + hex(leftColor)
        rightReferenceButton.text = "RIGHT  " + hex(rightColor)
        leftReferenceButton.setTextColor(contrastText(leftColor))
        rightReferenceButton.setTextColor(contrastText(rightColor))
        leftReferenceButton.setBackgroundTintList(ColorStateList.valueOf(leftColor))
        rightReferenceButton.setBackgroundTintList(ColorStateList.valueOf(rightColor))
    }

    private fun canvasColor(region: SamplingRegion): Int {
        val bmp = referenceBitmap ?: return Color.BLACK
        return averageColor(bmp, region)
    }

    private fun averageColor(bmp: Bitmap, region: SamplingRegion): Int {
        val x0 = (region.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val x1 = (region.right * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = (region.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val y1 = (region.bottom * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        val stepX = maxOf(1, (x1 - x0) / 24)
        val stepY = maxOf(1, (y1 - y0) / 24)
        for (y in y0 until y1 step stepY) {
            for (x in x0 until x1 step stepX) {
                val c = bmp.getPixel(x, y)
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                n++
            }
        }
        return if (n == 0L) Color.BLACK else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun hex(color: Int): String =
        "#%02X%02X%02X".format(Color.red(color), Color.green(color), Color.blue(color))

    private fun contrastText(color: Int): Int {
        val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return if (luminance > 150) Color.BLACK else Color.WHITE
    }

    private fun startDelayedCapture() {
        val service = BifrostAccessibilityService.instance
        if (service == null || !BifrostAccessibilityService.isEnabled(service)) {
            Toast.makeText(
                this,
                "Enable the Bifrost accessibility service first, then try LIVE GAME SCREEN again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (captureCountdownActive) return

        captureCountdownActive = true
        updatePlaceholder()
        Toast.makeText(this, "Capturing in 5s — switch to your game now.", Toast.LENGTH_LONG).show()

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            captureCountdownActive = false
            updatePlaceholder()
            takeReferenceScreenshot(service)
        }, 5000L)
    }

    private fun importImageFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use {
                val metrics = getDisplayMetrics()
                val targetW = 540
                val targetH = (targetW * metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()).toInt().coerceAtLeast(120)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show()
                    return
                }

                val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
                contentResolver.openInputStream(uri)?.use { dec ->
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = android.graphics.BitmapFactory.decodeStream(dec, null, opts) ?: return@use
                    val scaled = if (decoded.width != targetW || decoded.height != targetH) {
                        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { decoded.recycle() }
                    } else decoded

                    referenceBitmap?.recycle()
                    referenceBitmap = scaled
                    canvas.backgroundBitmap = scaled
                    updatePlaceholder()
                    updateReferenceButtons(canvas.leftRegion, canvas.rightRegion)
                    Toast.makeText(this, "Still image loaded. Position the highlighted stick area.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Import failed: " + e.message, Toast.LENGTH_LONG).show()
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
                Executor { command -> runOnUiThread(command) },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (isFinishing || isDestroyed) {
                            screenshot.hardwareBuffer.close()
                            return
                        }

                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        screenshot.hardwareBuffer.close()
                        if (hw == null) {
                            Toast.makeText(this@SamplingEditorActivity, "Screenshot unavailable.", Toast.LENGTH_LONG).show()
                            return
                        }

                        val softwareSource = try {
                            hw.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            hw.recycle()
                        }
                        if (softwareSource == null) {
                            Toast.makeText(this@SamplingEditorActivity, "Could not convert screenshot.", Toast.LENGTH_LONG).show()
                            return
                        }

                        val metrics = getDisplayMetrics()
                        val targetW = 540
                        val targetH = (targetW * metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()).toInt().coerceAtLeast(120)
                        val software = if (softwareSource.width != targetW || softwareSource.height != targetH) {
                            Bitmap.createScaledBitmap(softwareSource, targetW, targetH, true).also { softwareSource.recycle() }
                        } else softwareSource

                        referenceBitmap?.recycle()
                        referenceBitmap = software
                        canvas.backgroundBitmap = software
                        updatePlaceholder()
                        updateReferenceButtons(canvas.leftRegion, canvas.rightRegion)
                        Toast.makeText(this@SamplingEditorActivity, "Game screen captured.", Toast.LENGTH_SHORT).show()
                    }

                    override fun onFailure(errorCode: Int) {
                        captureCountdownActive = false
                        updatePlaceholder()
                        Toast.makeText(
                            this@SamplingEditorActivity,
                            "Screenshot failed (error " + errorCode + "). You can still position the areas.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        } catch (e: Throwable) {
            captureCountdownActive = false
            updatePlaceholder()
            Toast.makeText(this, "Screenshot unavailable: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun runLedTest(left: Int, right: Int, bypassCalibration: Boolean = false) {
        val intent = Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, left)
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, right)
            putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, 1500L)
            putExtra(LEDService.EXTRA_RP5_TEST_BYPASS_CALIBRATION, bypassCalibration)
        }
        runCatching { startService(intent) }.onFailure {
            Toast.makeText(this, "LED test could not start: " + it.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAndFinish() {
        SamplingRegionStore.setRegions(this, canvas.leftRegion, canvas.rightRegion)
        SamplingRegionStore.setEnabled(this, true)
        Toast.makeText(
            this,
            "Sampling areas saved and enabled for Ambient / Ambi Aurora.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        referenceBitmap?.recycle()
        referenceBitmap = null
        super.onDestroy()
    }
}

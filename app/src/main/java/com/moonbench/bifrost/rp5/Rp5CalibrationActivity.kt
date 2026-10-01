package com.moonbench.bifrost.rp5

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.moonbench.bifrost.R
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class Rp5CalibrationActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var ledController: LedController
    private lateinit var targetSwatch: View
    private lateinit var rawSwatch: View
    private lateinit var targetValue: TextView
    private lateinit var rawValue: TextView
    private lateinit var sourceStatus: TextView
    private lateinit var stickStatus: TextView
    private lateinit var primaryStatus: TextView
    private lateinit var redSeek: SeekBar
    private lateinit var greenSeek: SeekBar
    private lateinit var blueSeek: SeekBar

    private var calibration = LedColorCalibration()
    private var activeStick = LedColorCalibration.Stick.LEFT
    private var activePrimary = LedColorCalibration.Primary.RED
    private var targetColor = Color.RED
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var lastBitmap: Bitmap? = null
    private val captureHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_CAPTURE = 4201
        private const val REQUEST_STILL = 4202
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
        calibration = LedColorCalibration.load(prefs)
        ledController = LedController(prefs)
        buildUi()
        updatePrimaryTarget()
        loadCurrentPrimary()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CAPTURE -> {
                if (resultCode == RESULT_OK && data != null) {
                    startLiveCapture(data)
                } else {
                    sourceStatus.text = "Screen capture permission required"
                    Toast.makeText(this, "Screen capture permission required", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_STILL -> {
                if (resultCode != RESULT_OK || data == null) return
                runCatching {
                    contentResolver.openInputStream(data)?.use { BitmapFactory.decodeStream(it) }
                }.onSuccess { bitmap ->
                    if (bitmap == null) {
                        Toast.makeText(this, "Unable to open image", Toast.LENGTH_SHORT).show()
                    } else {
                        lastBitmap?.recycle()
                        lastBitmap = bitmap
                        sourceStatus.text = "Still image loaded. Position the highlighted stick area."
                        updateTargetFromBitmap(bitmap)
                    }
                }.onFailure {
                    Toast.makeText(this, "Unable to open image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        stopCapture()
        ledController.setBrightness(0)
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(getColor(R.color.bifrost_bg))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(button("BACK").apply { setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(76), dp(40)))
        top.addView(text("RP5 LED CALIBRATION", 18f, true), LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(10) })
        root.addView(top)

        root.addView(text(
            "Match the physical LED to the screen colour. Adjust raw RGB, test it, then save the primary. Green can be corrected independently from blue.",
            10f, false
        ).apply { setPadding(0, dp(8), 0, dp(10)) })

        root.addView(card().apply {
            val c = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            c.addView(text("SOURCE", 12f, true))
            c.addView(text("Choose how to get the game screen used for calibration.", 9f, false))
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("LIVE GAME SCREEN").apply { setOnClickListener { requestLiveCapture() } },
                LinearLayout.LayoutParams(0, dp(40), 1f))
            row.addView(button("STILL IMAGE").apply { setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }, REQUEST_STILL) } },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
            c.addView(row.apply { setPadding(0, dp(8), 0, 0) })
            sourceStatus = text("LIVE waits 5 seconds, then captures the game. STILL lets you choose a screenshot.", 8f, false)
            c.addView(sourceStatus.apply { setPadding(0, dp(6), 0, 0) })
            addView(c)
        })

        val stickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stickRow.addView(button("LEFT STICK").apply {
            setOnClickListener { activeStick = LedColorCalibration.Stick.LEFT; loadCurrentPrimary() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        stickRow.addView(button("RIGHT STICK").apply {
            setOnClickListener { activeStick = LedColorCalibration.Stick.RIGHT; loadCurrentPrimary() }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        root.addView(stickRow.apply { setPadding(0, dp(10), 0, dp(3)) })
        stickStatus = text("", 9f, true)
        root.addView(stickStatus)

        root.addView(card().apply {
            val c = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            c.addView(text("SCREEN TARGET", 11f, true))
            targetSwatch = View(context)
            c.addView(targetSwatch, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(6) })
            targetValue = text("", 9f, true)
            c.addView(targetValue)
            addView(c)
        })

        val primaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("RED","GREEN","BLUE","WHITE").forEachIndexed { i, name ->
            primaryRow.addView(button(name).apply {
                setOnClickListener {
                    activePrimary = LedColorCalibration.Primary.values()[i]
                    updatePrimaryTarget()
                    loadCurrentPrimary()
                }
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) marginStart = dp(4) })
        }
        root.addView(primaryRow.apply { setPadding(0, dp(10), 0, dp(4)) })
        primaryStatus = text("", 9f, false)
        root.addView(primaryStatus)

        root.addView(card().apply {
            val c = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            c.addView(text("RAW LED COMMAND", 11f, true))
            rawSwatch = View(context)
            c.addView(rawSwatch, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })
            redSeek = addSeek(c, "RED")
            greenSeek = addSeek(c, "GREEN")
            blueSeek = addSeek(c, "BLUE")
            rawValue = text("", 9f, true)
            c.addView(rawValue)
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("TEST RAW LED").apply { setOnClickListener { testRaw() } }, LinearLayout.LayoutParams(0, dp(40), 1f))
            row.addView(button("SAVE PRIMARY").apply { setOnClickListener { savePrimary() } }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
            c.addView(row.apply { setPadding(0, dp(8), 0, 0) })
            addView(c)
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("NEXT PRIMARY").apply { setOnClickListener { nextPrimary() } }, LinearLayout.LayoutParams(0, dp(40), 1f))
        actions.addView(button("RESET LED CALIBRATION").apply { setOnClickListener { resetCalibration() } }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        root.addView(actions.apply { setPadding(0, dp(8), 0, 0) })

        root.addView(button("SAVE & APPLY").apply {
            setOnClickListener {
                LedColorCalibration.save(prefs, calibration)
                Toast.makeText(this@Rp5CalibrationActivity, "LED colour calibration saved for normal Ambient / Ambi Aurora output.", Toast.LENGTH_LONG).show()
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })

        root.addView(button("DISABLE").apply {
            setOnClickListener {
                LedColorCalibration.reset(prefs)
                calibration = LedColorCalibration()
                loadCurrentPrimary()
                Toast.makeText(this@Rp5CalibrationActivity, "LED calibration reset.", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })

        root.addView(text("WHITE is a verification colour. Save & Apply when the stick looks correct. WHITE is not a matrix primary.", 8f, false)
            .apply { setPadding(0, dp(8), 0, dp(14)) })
    }

    private fun addSeek(parent: LinearLayout, label: String): SeekBar {
        parent.addView(text(label, 8f, true).apply { setPadding(0, dp(6), 0, 0) })
        return SeekBar(this).also {
            it.max = 255
            parent.addView(it, LinearLayout.LayoutParams(-1, dp(34)))
            it.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) updateRawUi() }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
    }

    private fun updatePrimaryTarget() {
        targetColor = when (activePrimary) {
            LedColorCalibration.Primary.RED -> Color.RED
            LedColorCalibration.Primary.GREEN -> Color.GREEN
            LedColorCalibration.Primary.BLUE -> Color.BLUE
            LedColorCalibration.Primary.WHITE -> Color.WHITE
        }
        targetSwatch.setBackgroundColor(targetColor)
        targetValue.text = String.format("#%06X", targetColor and 0xFFFFFF)
        primaryStatus.text = if (activePrimary == LedColorCalibration.Primary.WHITE)
            "WHITE is verification only; it is not a matrix primary."
        else
            "Selected " + activePrimary.name + " • " + activeStick.name + " • adjust the raw command until the physical LED matches."
    }

    private fun loadCurrentPrimary() {
        stickStatus.text = "SELECTED: " + activeStick.name
        val stick = if (activeStick == LedColorCalibration.Stick.LEFT) calibration.left else calibration.right
        val command = when (activePrimary) {
            LedColorCalibration.Primary.RED -> stick.red
            LedColorCalibration.Primary.GREEN -> stick.green
            LedColorCalibration.Primary.BLUE -> stick.blue
            LedColorCalibration.Primary.WHITE -> LedColorCalibration.CommandColor(255, 255, 255)
        }
        redSeek.progress = command.red
        greenSeek.progress = command.green
        blueSeek.progress = command.blue
        updateRawUi()
    }

    private fun updateRawUi() {
        val c = Color.rgb(redSeek.progress, greenSeek.progress, blueSeek.progress)
        rawSwatch.setBackgroundColor(c)
        rawValue.text = String.format("RAW #%06X", c and 0xFFFFFF)
    }

    private fun testRaw() {
        val c = Color.rgb(redSeek.progress, greenSeek.progress, blueSeek.progress)
        val left = activeStick == LedColorCalibration.Stick.LEFT
        ledController.setLedColor(Color.red(c), Color.green(c), Color.blue(c), leftTop = left, leftBottom = left, rightTop = !left, rightBottom = !left)
    }

    private fun savePrimary() {
        if (activePrimary == LedColorCalibration.Primary.WHITE) {
            testRaw()
            Toast.makeText(this, "WHITE is verification only; it is not a matrix primary.", Toast.LENGTH_SHORT).show()
            return
        }
        calibration = calibration.withPrimary(
            activeStick,
            activePrimary,
            LedColorCalibration.CommandColor(redSeek.progress, greenSeek.progress, blueSeek.progress)
        )
        LedColorCalibration.save(prefs, calibration)
        Toast.makeText(this, "Saved " + activePrimary.name + " for " + activeStick.name + ".", Toast.LENGTH_SHORT).show()
    }

    private fun nextPrimary() {
        activePrimary = when (activePrimary) {
            LedColorCalibration.Primary.RED -> LedColorCalibration.Primary.GREEN
            LedColorCalibration.Primary.GREEN -> LedColorCalibration.Primary.BLUE
            LedColorCalibration.Primary.BLUE -> LedColorCalibration.Primary.WHITE
            LedColorCalibration.Primary.WHITE -> LedColorCalibration.Primary.RED
        }
        updatePrimaryTarget()
        loadCurrentPrimary()
    }

    private fun resetCalibration() {
        LedColorCalibration.reset(prefs)
        calibration = LedColorCalibration()
        loadCurrentPrimary()
        Toast.makeText(this, "LED calibration reset.", Toast.LENGTH_SHORT).show()
    }

    private fun requestLiveCapture() {
        sourceStatus.text = "Screen capture permission required"
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    private fun startLiveCapture(data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = manager.getMediaProjection(RESULT_OK, data)
        val m = resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        imageReader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            image.use {
                val plane = it.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val paddedWidth = w + (rowStride - pixelStride * w) / pixelStride
                val bitmap = Bitmap.createBitmap(paddedWidth, h, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = if (paddedWidth == w) bitmap else Bitmap.createBitmap(bitmap, 0, 0, w, h)
                if (cropped !== bitmap) bitmap.recycle()
                lastBitmap?.recycle()
                lastBitmap = cropped
                updateTargetFromBitmap(cropped)
            }
        }, captureHandler)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BifrostRP5Calibration", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, captureHandler
        )
        sourceStatus.text = "LIVE GAME SCREEN • sampling the default left/right regions"
    }

    private fun updateTargetFromBitmap(bitmap: Bitmap) {
        targetColor = sampleRegion(bitmap, if (activeStick == LedColorCalibration.Stick.LEFT) .25f else .75f, .78f, .18f)
        targetSwatch.setBackgroundColor(targetColor)
        targetValue.text = String.format("#%06X", targetColor and 0xFFFFFF)
    }

    private fun sampleRegion(bitmap: Bitmap, cx: Float, cy: Float, radius: Float): Int {
        val hw = (bitmap.width * radius / 2f).roundToInt().coerceAtLeast(1)
        val hh = (bitmap.height * radius / 2f).roundToInt().coerceAtLeast(1)
        val x0 = (bitmap.width * cx).roundToInt().coerceIn(0, bitmap.width - 1)
        val y0 = (bitmap.height * cy).roundToInt().coerceIn(0, bitmap.height - 1)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in (y0 - hh).coerceAtLeast(0)..(y0 + hh).coerceAtMost(bitmap.height - 1) step 8) {
            for (x in (x0 - hw).coerceAtLeast(0)..(x0 + hw).coerceAtMost(bitmap.width - 1) step 8) {
                val c = bitmap.getPixel(x, y)
                r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
            }
        }
        if (n == 0L) return Color.BLACK
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        imageReader?.close()
        imageReader = null
        lastBitmap?.recycle()
        lastBitmap = null
    }

    private fun card() = MaterialCardView(this).apply {
        setCardBackgroundColor(getColor(R.color.bifrost_card))
        radius = dp(14).toFloat()
        strokeWidth = dp(1)
        setStrokeColorResource(R.color.bifrost_accent)
    }

    private fun button(label: String) = MaterialButton(this).apply {
        text = label
        textSize = 9f
        setTextColor(getColor(R.color.bifrost_text))
        setBackgroundColor(getColor(R.color.bifrost_surface))
        insetTop = 0; insetBottom = 0
        cornerRadius = dp(10); strokeWidth = dp(1); strokeColor = getColor(R.color.bifrost_accent)
    }

    private fun text(value: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(if (bold) R.color.bifrost_text else R.color.bifrost_text_secondary))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

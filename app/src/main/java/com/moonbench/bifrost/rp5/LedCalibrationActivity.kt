package com.moonbench.bifrost.rp5

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController

class LedCalibrationActivity : Activity() {
    private lateinit var view: LedCalibrationView
    private var preview: ThumbstickLivePreview? = null
    private val led by lazy { LedController(this) }
    private var displayId = android.view.Display.DEFAULT_DISPLAY
    private var brightness = 255
    private var testIndex = 0
    private var brightnessIndex = 4
    private var running = false
    private var mode = MODE_SCREEN
    private lateinit var status: TextView
    private lateinit var displaySpinner: Spinner

    companion object {
        const val EXTRA_MODE = "rp5_calibration_mode"
        const val MODE_SCREEN = "screen"
        const val MODE_LED_COLOR = "led_color"
        const val MODE_LED_BRIGHTNESS = "led_brightness"
        private const val PICK_IMAGE = 6107
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SCREEN
        send(LEDService.ACTION_CALIBRATION_ENTER)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(buildUi())
    }

    private fun buildUi(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 12, 24))
            setPadding(16, 12, 16, 12)

            status = TextView(this@LedCalibrationActivity).apply {
                text = titleForMode()
                textSize = 22f
                setTextColor(Color.rgb(242, 244, 255))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(4, 4, 4, 10)
            }
            addView(status)

            if (mode == MODE_SCREEN) addView(buildScreenControls())

            view = LedCalibrationView(this@LedCalibrationActivity)
            view.onRegionChanged = { l, r -> preview?.preview(l, r, ThumbstickColourMode.AVERAGE) }

            if (mode == MODE_SCREEN) {
                addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(buildButtons(
                    "LIVE" to { startLive() },
                    "STILL IMAGE" to { pickStill() },
                    "RESET" to { resetView() },
                    "CLOSE" to { finish() }
                ))
            } else if (mode == MODE_LED_COLOR) {
                addView(description("Controlled red, green, blue and white LED output for physical colour matching."))
                addView(centerPanel("LED COLOUR MATCH"), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(buildButtons("TEST COLOR" to { colourMatchTest() }, "CLOSE" to { finish() }))
            } else {
                addView(description("Test LED white output at OFF, 25%, 50%, 75% and 100%."))
                addView(centerPanel("LED BRIGHTNESS"), LinearLayout.LayoutParams(-1, 0, 1f))
                addView(buildButtons("TEST BRIGHTNESS" to { brightnessTest() }, "CLOSE" to { finish() }))
            }
        }
    }

    private fun buildScreenControls(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = "CAPTURE DISPLAY:"
            textSize = 11f
            setTextColor(Color.rgb(137, 146, 173))
            setPadding(6, 4, 4, 4)
        })
        displaySpinner = Spinner(this)
        row.addView(displaySpinner, LinearLayout.LayoutParams(0, -2, 1f))
        populateDisplays()
        return row
    }

    private fun description(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(137, 146, 173))
        setPadding(6, 8, 6, 18)
    }

    private fun centerPanel(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 28f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(242, 244, 255))
        setBackgroundColor(Color.rgb(16, 22, 41))
    }

    private fun buildButtons(vararg buttons: Pair<String, () -> Unit>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { pair ->
                addView(Button(this@LedCalibrationActivity).apply {
                    text = pair.first
                    textSize = 11f
                    setOnClickListener { pair.second() }
                    layoutParams = LinearLayout.LayoutParams(0, 52, 1f)
                })
            }
        }

    private fun startLive() {
        stopPreview()
        running = true
        preview = ThumbstickLivePreview(
            this,
            { bmp -> runOnUiThread { view.setFrame(bmp); status.text = "SCREEN COLOR MATCH • LIVE" } },
            { l, r -> runOnUiThread { view.setColors(l, r) } },
            displayId,
            { brightness }
        )
        preview?.start()
        val l = view.left
        val r = view.right
        preview?.preview(l, r, ThumbstickColourMode.AVERAGE)
    }

    private fun pickStill() {
        stopPreview()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val bmp = try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (_: Throwable) { null }
        if (bmp == null) {
            Toast.makeText(this, "Unable to open image", Toast.LENGTH_SHORT).show()
            return
        }
        view.setFrame(bmp)
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val sampled = ColorSampler(SamplingMethod.AVERAGE).sample(pixels, bmp.width, bmp.height, view.left, view.right)
        view.setColors(sampled.left, sampled.right)
        status.text = "SCREEN COLOR MATCH • STILL IMAGE"
    }

    private fun resetView() {
        view.setRegions(NormalizedRegion(.25f, .78f, .18f), NormalizedRegion(.75f, .78f, .18f))
        view.setFrame(null)
        stopPreview()
        status.text = "SCREEN COLOR MATCH • RESET"
    }

    private fun colourMatchTest() {
        testIndex = (testIndex + 1) % 4
        val c = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)[testIndex]
        led.setLedColorDual(Color.red(c), Color.green(c), Color.blue(c), Color.red(c), Color.green(c), Color.blue(c), brightness)
        status.text = "LED COLOR MATCH • " + name(c)
    }

    private fun brightnessTest() {
        val levels = intArrayOf(0, 64, 128, 192, 255)
        brightnessIndex = (brightnessIndex + 1) % levels.size
        brightness = levels[brightnessIndex]
        led.setLedColorDual(255, 255, 255, 255, 255, 255, brightness)
        status.text = "LED BRIGHTNESS • " + (brightness * 100 / 255) + "%"
    }

    private fun populateDisplays() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        displaySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displays.map { "Capture Display " + it.displayId })
        displaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, selected: android.view.View?, position: Int, id: Long) {
                displayId = displays.getOrNull(position)?.displayId ?: android.view.Display.DEFAULT_DISPLAY
                if (running) startLive()
            }
        }
    }

    private fun titleForMode() = when (mode) {
        MODE_LED_COLOR -> "LED COLOR MATCH"
        MODE_LED_BRIGHTNESS -> "LED BRIGHTNESS"
        else -> "SCREEN COLOR MATCH"
    }

    private fun stopPreview() {
        running = false
        preview?.stop()
        preview = null
    }

    private fun send(action: String) {
        runCatching { startService(Intent(this, LEDService::class.java).setAction(action)) }
    }

    private fun name(c: Int) = when (c) {
        Color.RED -> "RED"
        Color.GREEN -> "GREEN"
        Color.BLUE -> "BLUE"
        else -> "WHITE"
    }

    override fun onPause() {
        stopPreview()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onPause()
    }

    override fun onDestroy() {
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onDestroy()
    }
}
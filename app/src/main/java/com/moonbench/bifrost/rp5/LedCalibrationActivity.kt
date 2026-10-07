package com.moonbench.bifrost.rp5

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController

/**
 * Calibration-only test page.
 *
 * Deliberately separated into three independent checks:
 * 1. Screen Colour Check
 * 2. LED Thumb Stick Check
 * 3. LED Brightness Check
 *
 * This activity does not create or modify viewing profiles and does not touch
 * Fine Tune/profile bindings.
 */
class LedCalibrationActivity : AppCompatActivity() {
    private lateinit var screenPreview: ImageView
    private var preview: ThumbstickLivePreview? = null
    private val led by lazy { LedController(this) }
    private var captureMode = 0 // 0 = live, 1 = still
    private var brightness = 255
    private var serviceWasRunning = false

    private val leftBlue = Color.rgb(40, 120, 255)
    private val rightOrange = Color.rgb(255, 140, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceWasRunning = LEDService.isRunning
        send(LEDService.ACTION_CALIBRATION_ENTER)
        setContentView(buildPage())
    }

    private fun buildPage(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 24)
            setBackgroundColor(Color.rgb(8, 12, 24))
        }

        root.addView(screenTitle("CALIBRATION"))
        root.addView(description("Check screen colour capture, thumb-stick LED operation and LED brightness."))

        root.addView(sectionHeading("SCREEN COLOUR CHECK"))
        root.addView(description("Check how Bifrost captures the game screen. Choose a live screen or a single still image."))

        root.addView(uiCard("LIVE GAME SCREEN", "Continuously capture the current game screen.") {
            captureMode = 0
            startLiveScreenCheck()
        })
        root.addView(space(12))
        root.addView(uiCard("STILL IMAGE", "Capture one fixed screen image for colour checking.") {
            captureMode = 1
            startStillScreenCheck()
        })

        screenPreview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(21, 29, 51))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Captured game screen preview"
        }
        root.addView(screenPreview, LinearLayout.LayoutParams(-1, 230).apply {
            topMargin = 16
            bottomMargin = 24
        })

        root.addView(sectionHeading("LED THUMB STICK CHECK"))
        root.addView(description("Check the left and right thumb-stick LEDs independently, then together."))

        val leftRight = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(uiCard("LEFT LED", "Test the left thumb-stick LED in blue.", { testLeft() }), weightParams())
            addView(uiCard("RIGHT LED", "Test the right thumb-stick LED in orange.", { testRight() }), weightParams())
        }
        root.addView(leftRight)
        root.addView(space(12))
        root.addView(uiCard("BOTH LEDs", "Test both thumb-stick LED channels together.", { testBoth() }))
        root.addView(space(12))
        root.addView(uiCard("LED OFF", "Turn both thumb-stick LEDs off.", { allOff() }))

        root.addView(sectionHeading("LED BRIGHTNESS CHECK"))
        root.addView(description("Check LED output at five fixed brightness levels."))

        val brightnessRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val values = listOf(0, 25, 50, 75, 100)
            values.forEachIndexed { index, value ->
                addView(
                    actionButton(if (value == 0) "OFF" else "$value%") { setBrightness(value) },
                    LinearLayout.LayoutParams(0, 52, 1f).apply {
                        if (index < values.lastIndex) marginEnd = 8
                    }
                )
            }
        }
        root.addView(brightnessRow)

        root.addView(space(32))
        root.addView(actionButton("EXIT / RESTORE NORMAL LED CONTROL") {
            finish()
        }, LinearLayout.LayoutParams(-1, 56))

        return ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 12, 24))
            addView(root)
        }
    }

    private fun screenTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(Color.rgb(242, 244, 255))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, 8)
    }

    private fun sectionHeading(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.rgb(242, 244, 255))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 24, 0, 8)
    }

    private fun description(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(137, 146, 173))
        setPadding(0, 0, 0, 12)
    }

    private fun uiCard(title: String, subtitle: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 14, 20, 14)
            background = roundedBackground(Color.rgb(16, 22, 41), Color.rgb(104, 101, 242), 20f)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.rgb(242, 244, 255))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.rgb(137, 146, 173))
            setPadding(0, 4, 0, 0)
        })
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(-1, 76)
        }
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(242, 244, 255))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isAllCaps = false
        background = roundedBackground(Color.rgb(16, 22, 41), Color.rgb(104, 101, 242), 14f)
        minHeight = 52
        setPadding(20, 0, 20, 0)
        setOnClickListener { action() }
    }

    private fun roundedBackground(fill: Int, stroke: Int, radius: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            setStroke(1, stroke)
            cornerRadius = radius
        }

    private fun weightParams() = LinearLayout.LayoutParams(0, 76, 1f).apply {
        marginEnd = 12
    }

    private fun space(height: Int): Space =
        Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }

    private fun startLiveScreenCheck() {
        stopScreenCheck()
        captureMode = 0
        preview = makePreview()
        preview?.preview(
            NormalizedRegion(.25f, .50f, .30f),
            NormalizedRegion(.75f, .50f, .30f),
            ThumbstickColourMode.FIFTY_FIFTY
        )
        preview?.start()
    }

    private fun startStillScreenCheck() {
        stopScreenCheck()
        captureMode = 1
        preview = makePreview()
        preview?.preview(
            NormalizedRegion(.25f, .50f, .30f),
            NormalizedRegion(.75f, .50f, .30f),
            ThumbstickColourMode.FIFTY_FIFTY
        )
        preview?.captureOnce()
    }

    private fun makePreview(): ThumbstickLivePreview {
        return ThumbstickLivePreview(
            this,
            { frame -> screenPreview.setImageBitmap(frame) },
            { _, _ -> },
            brightness = { brightness },
            beforeCapture = {
                window.decorView.alpha = 0f
                supportActionBar?.hide()
            },
            afterCapture = {
                window.decorView.alpha = 1f
                supportActionBar?.show()
            },
        )
    }

    private fun stopScreenCheck() {
        preview?.stop()
        preview = null
        screenPreview.alpha = 1f
    }

    private fun testLeft() {
        led.setLedColorDualUncorrected(
            leftBlue.red(), leftBlue.green(), leftBlue.blue(),
            0, 0, 0,
            true, true, false, false
        )
    }

    private fun testRight() {
        led.setLedColorDualUncorrected(
            0, 0, 0,
            rightOrange.red(), rightOrange.green(), rightOrange.blue(),
            false, false, true, true
        )
    }

    private fun testBoth() {
        led.setLedColorDualUncorrected(
            leftBlue.red(), leftBlue.green(), leftBlue.blue(),
            rightOrange.red(), rightOrange.green(), rightOrange.blue(),
            true, true, true, true
        )
    }

    private fun allOff() {
        led.setLedColorDualUncorrected(0, 0, 0, 0, 0, 0, false, false, false, false)
        led.clear()
    }

    private fun setBrightness(percent: Int) {
        brightness = (percent * 255 / 100).coerceIn(0, 255)
        if (percent == 0) {
            led.clear()
            return
        }
        val l = Color.rgb(
            leftBlue.red() * percent / 100,
            leftBlue.green() * percent / 100,
            leftBlue.blue() * percent / 100
        )
        val r = Color.rgb(
            rightOrange.red() * percent / 100,
            rightOrange.green() * percent / 100,
            rightOrange.blue() * percent / 100
        )
        led.setLedColorDualUncorrected(
            l.red(), l.green(), l.blue(),
            r.red(), r.green(), r.blue()
        )
    }

    override fun onPause() {
        stopScreenCheck()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onPause()
    }

    override fun onDestroy() {
        stopScreenCheck()
        allOff()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onDestroy()
    }

    private fun send(action: String) {
        if (!serviceWasRunning) return
        runCatching {
            startService(Intent(this, LEDService::class.java).setAction(action))
        }
    }

    private fun Int.red() = Color.red(this)
    private fun Int.green() = Color.green(this)
    private fun Int.blue() = Color.blue(this)
}

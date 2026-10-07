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
            setPadding(20, 12, 20, 24)
            setBackgroundColor(Color.BLACK)
        }

        root.addView(heading("Screen Colour Check"))
        root.addView(description("Check that Bifrost can capture the game screen correctly. Choose a live game screen or a single still image."))

        val captureButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(actionButton("LIVE GAME SCREEN") {
                captureMode = 0
                startLiveScreenCheck()
            }, LinearLayout.LayoutParams(0, 58.dp(), 1f).apply { rightMargin = 8.dp() })
            addView(actionButton("STILL IMAGE") {
                captureMode = 1
                startStillScreenCheck()
            }, LinearLayout.LayoutParams(0, 58.dp(), 1f))
        }
        root.addView(captureButtons)

        screenPreview = ImageView(this).apply {
            setBackgroundColor(0xFF151515.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Captured game screen preview"
        }
        root.addView(screenPreview, LinearLayout.LayoutParams(-1, 230.dp()).apply {
            topMargin = 10.dp()
            bottomMargin = 18.dp()
        })

        root.addView(heading("LED Thumb Stick Check"))
        root.addView(description("Check the two thumb-stick LEDs independently. BLUE tests the left stick and ORANGE tests the right stick. BOTH checks both sides together."))

        val ledRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("LEFT — BLUE") { testLeft() }, weightParams())
            addView(actionButton("RIGHT — ORANGE") { testRight() }, weightParams())
        }
        val ledRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("BOTH — LEFT / RIGHT") { testBoth() }, weightParams())
            addView(actionButton("LED OFF") { allOff() }, weightParams())
        }
        root.addView(ledRow1)
        root.addView(ledRow2)

        root.addView(heading("LED Brightness Check"))
        root.addView(description("Check LED output at five fixed brightness levels. These controls affect both thumb sticks."))

        val brightnessRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val values = listOf(0, 25, 50, 75, 100)
            values.forEach { value ->
                addView(actionButton(if (value == 0) "OFF" else "LED $value%") {
                    setBrightness(value)
                }, LinearLayout.LayoutParams(0, 62.dp(), 1f).apply {
                    marginEnd = 4.dp()
                })
            }
        }
        root.addView(brightnessRow)

        root.addView(Space(this), LinearLayout.LayoutParams(1, 18.dp()))
        root.addView(actionButton("EXIT / RESTORE NORMAL LED CONTROL") {
            finish()
        }, LinearLayout.LayoutParams(-1, 58.dp()))

        return ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        }
    }

    private fun heading(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 30f
        setTextColor(Color.WHITE)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(8.dp(), 18.dp(), 8.dp(), 8.dp)
    }

    private fun description(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFFBDBDBD.toInt())
        setPadding(8.dp(), 0, 8.dp(), 12.dp)
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 13f
        setOnClickListener { action() }
        isAllCaps = false
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, 58.dp(), 1f).apply {
        marginEnd = 8.dp()
    }

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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Int.red() = Color.red(this)
    private fun Int.green() = Color.green(this)
    private fun Int.blue() = Color.blue(this)
}

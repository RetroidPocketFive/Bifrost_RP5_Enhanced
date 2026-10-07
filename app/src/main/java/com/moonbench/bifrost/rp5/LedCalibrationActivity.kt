package com.moonbench.bifrost.rp5

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController

class LedCalibrationActivity : AppCompatActivity() {
    private lateinit var screenPreview: ImageView
    private lateinit var status: TextView
    private var preview: ThumbstickLivePreview? = null
    private val led by lazy { LedController(this) }
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
        status = description("READY — select a test below.")
        status.setTextColor(Color.rgb(242, 244, 255))
        status.setPadding(0, 0, 0, 8)
        root.addView(status)

        root.addView(sectionHeading("SCREEN COLOUR CHECK"))
        root.addView(description("Choose a live screen capture or a single still image for colour checking."))

        root.addView(testButton("LIVE GAME SCREEN", "Continuously capture the current game screen.") {
            status.text = "LIVE SCREEN — starting capture…"
            startLiveScreenCheck()
        })
        root.addView(space(12))
        root.addView(testButton("STILL IMAGE", "Capture one fixed screen image for colour checking.") {
            status.text = "STILL IMAGE — capturing…"
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
        root.addView(description("Test the left and right thumb-stick LEDs independently, together, or off."))

        val leftRight = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(testButton("LEFT LED", "Blue left channel.") {
                status.text = "LEFT LED — blue"
                testLeft()
            }, LinearLayout.LayoutParams(0, 64, 1f).apply { marginEnd = 12 })
            addView(testButton("RIGHT LED", "Orange right channel.") {
                status.text = "RIGHT LED — orange"
                testRight()
            }, LinearLayout.LayoutParams(0, 64, 1f))
        }
        root.addView(leftRight)
        root.addView(space(12))
        root.addView(testButton("BOTH LEDs", "Blue left + orange right.") {
            status.text = "BOTH LEDs — active"
            testBoth()
        })
        root.addView(space(12))
        root.addView(testButton("LED OFF", "Turn both thumb-stick LEDs off.") {
            status.text = "LEDs — off"
            allOff()
        })

        root.addView(sectionHeading("LED BRIGHTNESS CHECK"))
        root.addView(description("Check LED output at five fixed brightness levels."))

        val brightnessRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val values = listOf(0, 25, 50, 75, 100)
            values.forEachIndexed { index, value ->
                addView(
                    actionButton(if (value == 0) "OFF" else "$value%") {
                        status.text = "BRIGHTNESS — " + if (value == 0) "OFF" else "$value%"
                        setBrightness(value)
                    },
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
            isFillViewport = true
            isClickable = false
            setBackgroundColor(Color.rgb(8, 12, 24))
            addView(root, ScrollView.LayoutParams(-1, -2))
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

    private fun testButton(title: String, subtitle: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = title + "\n" + subtitle
            textSize = 14f
            setTextColor(Color.rgb(242, 244, 255))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = false
            isEnabled = true
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            insetTop = 0
            insetBottom = 0
            setPadding(20, 0, 20, 0)
            cornerRadius = 20
            strokeWidth = 1
            strokeColor = ColorStateList.valueOf(Color.rgb(104, 101, 242))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(16, 22, 41))
            minHeight = 64
            setOnClickListener { action() }
        }

    private fun actionButton(text: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(242, 244, 255))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = false
            isEnabled = true
            isClickable = true
            isFocusable = true
            cornerRadius = 14
            strokeWidth = 1
            strokeColor = ColorStateList.valueOf(Color.rgb(104, 101, 242))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(16, 22, 41))
            minHeight = 52
            setOnClickListener { action() }
        }

    private fun space(height: Int): Space =
        Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }

    private fun startLiveScreenCheck() {
        stopScreenCheck()
        preview = makePreview()
        runCatching {
            preview?.preview(
                NormalizedRegion(.25f, .50f, .30f),
                NormalizedRegion(.75f, .50f, .30f),
                ThumbstickColourMode.FIFTY_FIFTY
            )
            preview?.start()
            status.text = "LIVE SCREEN — running"
        }.onFailure {
            status.text = "LIVE SCREEN — failed: " + (it.message ?: "unknown error")
            stopScreenCheck()
        }
    }

    private fun startStillScreenCheck() {
        stopScreenCheck()
        preview = makePreview()
        runCatching {
            preview?.preview(
                NormalizedRegion(.25f, .50f, .30f),
                NormalizedRegion(.75f, .50f, .30f),
                ThumbstickColourMode.FIFTY_FIFTY
            )
            preview?.captureOnce()
            status.text = "STILL IMAGE — captured"
        }.onFailure {
            status.text = "STILL IMAGE — failed: " + (it.message ?: "unknown error")
            stopScreenCheck()
        }
    }

    private fun makePreview(): ThumbstickLivePreview {
        return ThumbstickLivePreview(
            this,
            { frame -> runOnUiThread { screenPreview.setImageBitmap(frame) } },
            { _, _ -> },
            brightness = { brightness },
            beforeCapture = { },
            afterCapture = { },
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
            0, 0, 0, true, true, false, false
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
        val l = Color.rgb(leftBlue.red() * percent / 100, leftBlue.green() * percent / 100, leftBlue.blue() * percent / 100)
        val r = Color.rgb(rightOrange.red() * percent / 100, rightOrange.green() * percent / 100, rightOrange.blue() * percent / 100)
        led.setLedColorDualUncorrected(l.red(), l.green(), l.blue(), r.red(), r.green(), r.blue())
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
        runCatching { startService(Intent(this, LEDService::class.java).setAction(action)) }
    }

    private fun Int.red() = Color.red(this)
    private fun Int.green() = Color.green(this)
    private fun Int.blue() = Color.blue(this)
}

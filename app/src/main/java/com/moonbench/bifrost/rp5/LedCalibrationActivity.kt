package com.moonbench.bifrost.rp5

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController

/**
 * Calibration hub: screen source selection, existing LED colour-match calibration,
 * and the dedicated brightness test. It does not edit viewing profiles.
 */
class LedCalibrationActivity : AppCompatActivity() {
    private val led by lazy { LedController(this) }
    private var serviceWasRunning = false
    private var brightness = 255
    private var preview: ThumbstickLivePreview? = null
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var previewImage: ImageView

    private val bg = Color.rgb(8, 12, 24)
    private val panel = Color.rgb(16, 22, 41)
    private val secondary = Color.rgb(21, 29, 51)
    private val accent = Color.rgb(104, 101, 242)
    private val bright = Color.rgb(119, 117, 255)
    private val text = Color.rgb(242, 244, 255)
    private val muted = Color.rgb(137, 146, 173)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceWasRunning = LEDService.isRunning
        send(LEDService.ACTION_CALIBRATION_ENTER)
        showHub()
    }

    private fun showHub() {
        stopPreview()
        content = pageRoot()
        content.addView(header("Calibration", "Adjust your device for the best visual performance."))
        content.addView(sectionCard("SCREEN COLOUR CHECK",
            "Choose the game-screen source used by the original calibration.",
            listOf("LIVE IMAGE", "STILL IMAGE")) { choice ->
            if (choice == "LIVE IMAGE") startScreenCapture(false) else startScreenCapture(true)
        })
        content.addView(sectionCard("LED THUMB-STICK COLOUR MATCH",
            "Uses the original left/right thumb-stick colour sampling and LED test.",
            listOf("OPEN CALIBRATION")) {
            stopPreview()
            startActivity(Intent(this, Rp5CalibrationActivity::class.java))
        })
        content.addView(sectionCard("LED BRIGHTNESS TEST",
            "Check the LED brightness levels and uniformity.",
            listOf("START")) {
            showBrightness()
        })
        content.addView(footerButton("EXIT / RESTORE NORMAL LED CONTROL") { finish() })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            addView(content)
        })
    }

    private fun showBrightness() {
        stopPreview()
        content = pageRoot()
        content.addView(header("Calibration", "LED brightness test"))
        content.addView(sectionCard("LED BRIGHTNESS TEST",
            "Select a level and check the output of both thumb-stick LEDs.",
            emptyList()) {})
        val levels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(0, 25, 50, 75, 100).forEachIndexed { i, value ->
            levels.addView(button(if (value == 0) "OFF" else "LED $value%", true) {
                status.text = if (value == 0) "BRIGHTNESS — OFF" else "BRIGHTNESS — $value%"
                setBrightness(value)
            }, LinearLayout.LayoutParams(0, 48, 1f).apply { if (i != 4) marginEnd = 6 })
        }
        content.addView(levels)
        content.addView(space(16))
        content.addView(button("START", false) {
            status.text = "BRIGHTNESS TEST READY — choose OFF / 25% / 50% / 75% / 100%."
        })
        content.addView(space(24))
        content.addView(footerButton("BACK TO CALIBRATION") { showHub() })
        content.addView(footerButton("EXIT / RESTORE NORMAL LED CONTROL") { finish() })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            addView(content)
        })
        status.text = "Choose a brightness level to test both LEDs."
    }

    private fun startScreenCapture(still: Boolean) {
        stopPreview()
        content = pageRoot()
        content.addView(header("Calibration", if (still) "Still image" else "Live image"))
        status = label(
            if (still) "Capturing one still screen image…" else "Live screen capture is starting…",
            text, 14f
        )
        content.addView(status)
        previewImage = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Captured screen image"
        }
        content.addView(previewImage, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            height = 0
            weight = 1f
        })
        content.addView(button("BACK TO CALIBRATION") { stopPreview(); showHub() })
        setContentView(content)
        preview = ThumbstickLivePreview(
            this,
            { frame -> runOnUiThread { if (frame != null) previewImage.setImageBitmap(frame) } },
            { left, right ->
                runOnUiThread {
                    status.text = (if (still) "STILL IMAGE" else "LIVE IMAGE") +
                        " — LEFT #%06X   RIGHT #%06X".format(left and 0xffffff, right and 0xffffff)
                }
            },
            brightness = { brightness },
            beforeCapture = { },
            afterCapture = { },
        ).also {
            it.preview(NormalizedRegion(.25f, .78f, .18f), NormalizedRegion(.75f, .78f, .18f), ThumbstickColourMode.FIFTY_FIFTY)
            it.start(single = still)
        }
    }

    private fun pageRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 16, 20, 24)
        setBackgroundColor(bg)
    }

    private fun header(title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)
            background = rounded(panel, accent, 18)
        }
        val mark = TextView(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(text)
            gravity = Gravity.CENTER
            background = rounded(secondary, accent, 12)
        }
        row.addView(mark, LinearLayout.LayoutParams(44, 44))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 12, 0)
            addView(label("•  B I F R O S T", text, 12f, true))
        }
        row.addView(brand)
        val divider = android.view.View(this).apply { setBackgroundColor(accent) }
        row.addView(divider, LinearLayout.LayoutParams(1, 36))
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 0, 0)
            addView(label(title, text, 18f, true))
            addView(label(subtitle, muted, 11f))
        }
        row.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun sectionCard(title: String, description: String, actions: List<String>, onAction: (String) -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            background = rounded(panel, Color.rgb(69, 67, 168), 18)
        }
        card.addView(label(title, text, 14f, true))
        card.addView(space(4))
        card.addView(label(description, muted, 12f))
        if (actions.isNotEmpty()) {
            card.addView(space(10))
            if (actions.size == 2) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.forEachIndexed { i, action ->
                    row.addView(button(action, true) { onAction(action) },
                        LinearLayout.LayoutParams(0, 48, 1f).apply { if (i == 0) marginEnd = 8 })
                }
                card.addView(row)
            } else {
                actions.forEach { action ->
                    card.addView(button(action, true) { onAction(action) })
                }
            }
        }
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
        return card
    }

    private fun footerButton(text: String, action: () -> Unit) =
        button(text, false, action).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 48).apply { topMargin = 12 }
        }

    private fun button(text: String, filled: Boolean = false, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            textSize = 11f
            isAllCaps = false
            isEnabled = true
            isClickable = true
            isFocusable = true
            setTextColor(if (filled) Color.WHITE else textColor())
            cornerRadius = 24
            insetTop = 0
            insetBottom = 0
            strokeWidth = if (filled) 0 else 1
            strokeColor = ColorStateList.valueOf(accent)
            backgroundTintList = ColorStateList.valueOf(if (filled) bright else panel)
            minHeight = 48
            setOnClickListener { action() }
        }

    private fun label(value: String, color: Int, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun space(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            setStroke(1, stroke)
            cornerRadius = radius.toFloat()
        }

    private fun textColor() = text

    private fun setBrightness(percent: Int) {
        brightness = percent * 255 / 100
        if (percent == 0) {
            led.clear()
            return
        }
        val left = Color.rgb(40 * percent / 100, 120 * percent / 100, 255 * percent / 100)
        val right = Color.rgb(255 * percent / 100, 140 * percent / 100, 30 * percent / 100)
        led.setLedColorDualUncorrected(
            Color.red(left), Color.green(left), Color.blue(left),
            Color.red(right), Color.green(right), Color.blue(right)
        )
    }

    private fun stopPreview() {
        preview?.stop()
        preview = null
    }

    override fun onPause() {
        stopPreview()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onPause()
    }

    override fun onDestroy() {
        stopPreview()
        led.clear()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onDestroy()
    }

    private fun send(action: String) {
        if (!serviceWasRunning) return
        runCatching { startService(Intent(this, LEDService::class.java).setAction(action)) }
    }
}

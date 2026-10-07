package com.moonbench.bifrost.rp5

import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.tools.LedController

class LedCalibrationActivity : Activity() {
    private lateinit var view: LedCalibrationView
    private var preview: ThumbstickLivePreview? = null
    private var displayId = android.view.Display.DEFAULT_DISPLAY
    private var selectedCaptureMode = 0
    private var serviceWasRunning = false
    private val led by lazy { LedController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceWasRunning = LEDService.isRunning
        send(LEDService.ACTION_CALIBRATION_ENTER)
        setContentView(build())
        startColourCheckLive()
    }

    private fun build(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF000000.toInt())

        addView(TextView(this@LedCalibrationActivity).apply {
            text = "CALIBRATION — COLOUR CHECK / LED TEST"
            setTextColor(-1)
            textSize = 18f
            setPadding(16, 16, 16, 6)
        })

        addView(TextView(this@LedCalibrationActivity).apply {
            text = "COLOUR CHECK — choose Live Image or Still Image. Move BLUE to test the left thumb-stick and ORANGE to test the right thumb-stick."
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 11f
            setPadding(16, 0, 16, 8)
        })

        val modeRow = LinearLayout(this@LedCalibrationActivity).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(button("LIVE IMAGE") { selectedCaptureMode = 0; startColourCheckLive() })
        modeRow.addView(button("STILL IMAGE") { selectedCaptureMode = 1; captureStill() })
        addView(modeRow)

        val dm = getSystemService(DisplayManager::class.java)
        val displays = dm.displays
        val spinner = Spinner(this@LedCalibrationActivity)
        spinner.adapter = ArrayAdapter(this@LedCalibrationActivity, android.R.layout.simple_spinner_dropdown_item, displays.map { "Capture Display " + it.displayId })
        spinner.setSelection(displays.indexOfFirst { it.displayId == displayId }.coerceAtLeast(0))
        spinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, selected: View?, position: Int, id: Long) {
                displayId = displays.getOrNull(position)?.displayId ?: android.view.Display.DEFAULT_DISPLAY
                if (selectedCaptureMode == 0) startColourCheckLive()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        })
        addView(spinner)

        addView(TextView(this@LedCalibrationActivity).apply {
            text = "LED COLOUR MATCH"
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(16, 8, 16, 4)
        })

        view = LedCalibrationView(this@LedCalibrationActivity)
        addView(view, LinearLayout.LayoutParams(-1, 0, 1f))

        addView(TextView(this@LedCalibrationActivity).apply {
            text = "LED BRIGHTNESS TEST"
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
        })

        val brightnessRow = LinearLayout(this@LedCalibrationActivity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(0, 25, 50, 75, 100).forEach { value ->
            brightnessRow.addView(button(if (value == 0) "OFF" else "LED " + value + "%") {
                val level = value * 255 / 100
                if (value == 0) led.setLedColorDual(0, 0, 0, 0, 0, 0, 0)
                else led.setLedColorDual(level, level, level, level, level, level, level)
            })
        }
        addView(brightnessRow)
        addView(button("EXIT / RESTORE NORMAL LED CONTROL") { finish() })
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 10f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun startColourCheckLive() {
        stopColourCheck()
        selectedCaptureMode = 0
        preview = ThumbstickLivePreview(
            this,
            { frame -> view.setFrame(frame) },
            { left, right -> view.setColors(left, right) },
            displayId,
            { 255 },
            beforeCapture = { window.decorView.alpha = 0f },
            afterCapture = { window.decorView.alpha = 1f },
        )
        preview?.preview(view.left, view.right, ThumbstickColourMode.AVERAGE)
        preview?.start()
    }

    private fun captureStill() {
        stopColourCheck()
        selectedCaptureMode = 1
        preview = ThumbstickLivePreview(
            this,
            { frame -> view.setFrame(frame) },
            { left, right -> view.setColors(left, right) },
            displayId,
            { 255 },
            beforeCapture = { window.decorView.alpha = 0f },
            afterCapture = { window.decorView.alpha = 1f },
        )
        preview?.preview(view.left, view.right, ThumbstickColourMode.AVERAGE)
        preview?.captureOnce()
    }

    private fun stopColourCheck() {
        preview?.stop()
        preview = null
        window.decorView.alpha = 1f
    }

    override fun onPause() {
        stopColourCheck()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onPause()
    }

    override fun onDestroy() {
        stopColourCheck()
        send(LEDService.ACTION_CALIBRATION_EXIT)
        super.onDestroy()
    }

    private fun send(action: String) {
        if (!serviceWasRunning) return
        runCatching { startService(Intent(this, LEDService::class.java).setAction(action)) }
    }
}

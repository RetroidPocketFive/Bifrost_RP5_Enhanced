package com.moonbench.bifrost.rp5

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class Rp5CalibrationMenuActivity : AppCompatActivity() {
    private fun button(text: String, description: String, action: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 12)
            addView(MaterialButton(this@Rp5CalibrationMenuActivity).apply {
                this.text = text
                minHeight = 52
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, 56))
            addView(TextView(this@Rp5CalibrationMenuActivity).apply {
                this.text = description
                setTextColor(Color.rgb(137,146,173))
                textSize = 13f
                setPadding(8, 2, 8, 0)
            })
        }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(8,12,24))
        }
        root.addView(TextView(this).apply {
            text = "RP5 CALIBRATION"
            setTextColor(Color.rgb(242,244,255))
            textSize = 28f
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, 56))
        root.addView(TextView(this).apply {
            text = "Choose the calibration function to run. Each option opens its own dedicated screen."
            setTextColor(Color.rgb(137,146,173))
            textSize = 13f
            setPadding(0, 0, 0, 18)
        })
        root.addView(button("SCREEN COLOR MATCH", "Match the screen capture colours to the left and right LED positions.") {
            startActivity(Intent(this, Rp5CalibrationActivity::class.java))
        })
        root.addView(button("CAPTURE-DISPLAY SELECTION", "Choose which detected display is used as the calibration capture target.") {
            startActivity(Intent(this, Rp5CaptureDisplaySelectionActivity::class.java))
        })
        root.addView(button("LED COLOR MATCH", "Set independent left/right LED test colours and verify the physical LEDs.") {
            startActivity(Intent(this, Rp5LedColorMatchActivity::class.java))
        })
        root.addView(button("LED BRIGHTNESS", "Test LED output intensity independently from screen colour matching.") {
            startActivity(Intent(this, Rp5LedBrightnessActivity::class.java))
        })
        root.addView(MaterialButton(this).apply {
            text = "CLOSE"
            minHeight = 52
            setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams(-1, 56))
        setContentView(root)
    }
}

package com.moonbench.bifrost.rp5

import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class Rp5CaptureDisplaySelectionActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bifrost_rp5_calibration", Context.MODE_PRIVATE) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(8,12,24))
        }
        root.addView(TextView(this).apply {
            text = "CAPTURE-DISPLAY SELECTION"
            setTextColor(Color.rgb(242,244,255)); textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Select the display to use for RP5 calibration capture."
            setTextColor(Color.rgb(137,146,173)); textSize = 13f
            setPadding(0, 8, 0, 16)
        })
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selected = prefs.getInt("capture_display_id", displays.firstOrNull()?.displayId ?: 0)
        for (display in displays) {
            group.addView(RadioButton(this).apply {
                text = display.name + "  (ID " + display.displayId + ")"
                setTextColor(Color.rgb(242,244,255))
                textSize = 16f
                isChecked = display.displayId == selected
                setOnClickListener { prefs.edit().putInt("capture_display_id", display.displayId).apply() }
                setPadding(0, 10, 0, 10)
            })
        }
        root.addView(group, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(MaterialButton(this).apply {
            text = "DONE"; minHeight = 52
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, 56))
        setContentView(root)
    }
}

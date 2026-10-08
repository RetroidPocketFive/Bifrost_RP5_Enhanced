package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.LEDService

class Rp5LedBrightnessActivity : AppCompatActivity() {
    private var level=100
    private lateinit var value: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(24,24,24,24)
            setBackgroundColor(Color.rgb(8,12,24))
        }
        root.addView(TextView(this).apply {
            text="LED BRIGHTNESS"; setTextColor(Color.rgb(242,244,255)); textSize=24f
        })
        root.addView(TextView(this).apply {
            text="Test white LED output at a controlled brightness level."
            setTextColor(Color.rgb(137,146,173)); textSize=13f; setPadding(0,8,0,16)
        })
        value=TextView(this).apply { setTextColor(Color.rgb(242,244,255)); textSize=18f }
        root.addView(value)
        root.addView(SeekBar(this).apply {
            max=100; progress=level
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean) { level=p.coerceAtLeast(1); update() }
                override fun onStartTrackingTouch(s:SeekBar?) {}
                override fun onStopTrackingTouch(s:SeekBar?) {}
            })
        })
        root.addView(MaterialButton(this).apply { text="TEST BRIGHTNESS"; setOnClickListener { test() } })
        root.addView(MaterialButton(this).apply { text="CLOSE"; setOnClickListener { finish() } })
        setContentView(root)
        update()
    }

    private fun update() { value.text="BRIGHTNESS: " + level + "%" }

    private fun test() {
        val c=(255f*level/100f).toInt().coerceIn(1,255)
        startService(Intent(this,LEDService::class.java).apply {
            action=LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR,Color.rgb(c,c,c))
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR,Color.rgb(c,c,c))
            putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS,2000L)
        })
        value.text="TESTING: " + level + "%"
    }
}

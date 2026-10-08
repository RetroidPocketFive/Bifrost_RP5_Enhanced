package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.LEDService

class Rp5LedColorMatchActivity : AppCompatActivity() {
    private var left = Color.RED
    private var right = Color.BLUE
    private lateinit var status: TextView
    private val colours = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
    private val names = arrayOf("Red","Green","Blue","White","Yellow","Cyan","Magenta")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,24,24,24)
            setBackgroundColor(Color.rgb(8,12,24))
        }
        root.addView(TextView(this).apply {
            text="LED COLOR MATCH"; setTextColor(Color.rgb(242,244,255)); textSize=24f
        })
        root.addView(TextView(this).apply {
            text="Select independent left/right LED test colours, then run the LEDs."
            setTextColor(Color.rgb(137,146,173)); textSize=13f; setPadding(0,8,0,16)
        })
        root.addView(MaterialButton(this).apply { text="LEFT COLOR"; setOnClickListener { choose(true) } })
        root.addView(MaterialButton(this).apply { text="RIGHT COLOR"; setOnClickListener { choose(false) } })
        root.addView(MaterialButton(this).apply { text="TEST LEDs"; setOnClickListener { test() } })
        status=TextView(this).apply {
            setTextColor(Color.rgb(242,244,255)); textSize=14f; setPadding(0,12,0,12)
        }
        root.addView(status)
        root.addView(MaterialButton(this).apply { text="CLOSE"; setOnClickListener { finish() } })
        setContentView(root)
        updateStatus()
    }

    private fun choose(isLeft:Boolean) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if(isLeft) "Left LED color" else "Right LED color")
            .setItems(names) { _, which ->
                if(isLeft) left=colours[which] else right=colours[which]
                updateStatus()
            }.show()
    }

    private fun updateStatus() {
        status.text = "LEFT #" + String.format("%06X", left and 0xFFFFFF) +
            "    RIGHT #" + String.format("%06X", right and 0xFFFFFF)
    }

    private fun test() {
        startService(Intent(this, LEDService::class.java).apply {
            action=LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR,left)
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR,right)
            putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS,2000L)
        })
        status.text="LED TEST RUNNING"
    }
}

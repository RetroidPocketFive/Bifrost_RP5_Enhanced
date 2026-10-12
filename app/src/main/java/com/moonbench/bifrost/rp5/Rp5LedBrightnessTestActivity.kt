package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.LEDService
import kotlin.math.roundToInt

/** Dedicated RP5 LED brightness test screen. */
class Rp5LedBrightnessTestActivity : AppCompatActivity() {
    private val bg = Color.rgb(8, 12, 24)
    private val panel = Color.rgb(16, 22, 41)
    private val accent = Color.rgb(104, 101, 242)
    private val fg = Color.rgb(242, 244, 255)
    private val muted = Color.rgb(165, 174, 205)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var selectedLevel = 128
    private var levelLabel: TextView? = null
    private var startButton: MaterialButton? = null
    private val sweepLevels = listOf(16, 32, 48, 64, 96, 128, 160, 192, 224, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(20))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, accent, 18)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val back = button("‹", false).apply {
            textSize = 28f
            setOnClickListener { stopTest(); finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(54), dp(48)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        heading.addView(label("LED BRIGHTNESS TEST", 20f, fg, true))
        heading.addView(label("BIFROST  •  RETROID POCKET 5", 12f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val intro = panelCard()
        intro.addView(label("Brightness level", 18f, fg, true))
        intro.addView(label("Set a fixed output level or run the full brightness sweep. Both thumb sticks are tested together in neutral white.", 14f, muted).apply {
            setPadding(0, dp(8), 0, dp(12))
        })
        levelLabel = label("128 / 255  •  50%", 16f, fg, true)
        intro.addView(levelLabel)
        val slider = SeekBar(this).apply {
            max = 255
            progress = selectedLevel
            contentDescription = "LED brightness level"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedLevel = progress.coerceAtLeast(1)
                    levelLabel?.text = "$selectedLevel / 255  •  ${(selectedLevel * 100f / 255f).roundToInt()}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        intro.addView(slider, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        intro.addView(button("TEST SELECTED LEVEL", true).apply {
            setOnClickListener { testLevel(selectedLevel, 2500L) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        root.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val sweep = panelCard()
        sweep.addView(label("Full brightness sweep", 18f, fg, true))
        sweep.addView(label("Steps through 10 levels from very dim to full brightness. Watch both sticks and check for uneven output.", 14f, muted).apply {
            setPadding(0, dp(8), 0, dp(14))
        })
        startButton = button("START BRIGHTNESS SWEEP", true).apply {
            setOnClickListener {
                if (running) stopTest() else startSweep()
            }
        }
        sweep.addView(startButton, LinearLayout.LayoutParams(-1, dp(48)))
        sweep.addView(button("TURN LEDS OFF", false).apply {
            setOnClickListener { stopTest(); sendLevel(0, 250L) }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        root.addView(sweep, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        root.addView(label("Note: actual perceived brightness depends on the device LEDs and ambient light.", 12f, muted))
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun startSweep() {
        running = true
        startButton?.text = "STOP BRIGHTNESS SWEEP"
        Toast.makeText(this, "Brightness sweep started", Toast.LENGTH_SHORT).show()
        runSweepStep(0)
    }

    private fun runSweepStep(index: Int) {
        if (!running || isFinishing || isDestroyed) return
        if (index >= sweepLevels.size) {
            running = false
            startButton?.text = "START BRIGHTNESS SWEEP"
            sendLevel(0, 250L)
            Toast.makeText(this, "Brightness sweep complete", Toast.LENGTH_SHORT).show()
            return
        }
        testLevel(sweepLevels[index], 800L, showToast = false)
        handler.postDelayed({ runSweepStep(index + 1) }, 950L)
    }

    private fun testLevel(level: Int, durationMs: Long, showToast: Boolean = true) {
        sendLevel(level, durationMs)
        if (showToast) Toast.makeText(this, "Testing brightness $level / 255", Toast.LENGTH_SHORT).show()
    }

    private fun sendLevel(level: Int, durationMs: Long) {
        val value = level.coerceIn(0, 255)
        val color = Color.rgb(value, value, value)
        val intent = Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, color)
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, color)
            putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, durationMs.coerceIn(250L, 5000L))
            putExtra(LEDService.EXTRA_RP5_TEST_BYPASS_CALIBRATION, true)
        }
        try {
            startService(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not start LED test. Check that Bifrost LED control is enabled.", Toast.LENGTH_LONG).show()
            stopTest()
        }
    }

    private fun stopTest() {
        running = false
        handler.removeCallbacksAndMessages(null)
        startButton?.text = "START BRIGHTNESS SWEEP"
    }

    override fun onDestroy() {
        stopTest()
        super.onDestroy()
    }

    private fun panelCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel, accent, 18)
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(value: String, filled: Boolean) = MaterialButton(this).apply {
        text = value
        textSize = 13f
        isAllCaps = false
        setTextColor(fg)
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(24)
        backgroundTintList = android.content.res.ColorStateList.valueOf(if (filled) accent else Color.TRANSPARENT)
        strokeWidth = dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(accent)
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

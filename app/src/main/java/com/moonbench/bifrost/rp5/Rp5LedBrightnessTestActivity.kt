package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.services.LEDService
import kotlin.math.roundToInt

/** Dedicated RP5 LED brightness test screen. */
class Rp5LedBrightnessTestActivity : AppCompatActivity() {
    private val palette by lazy { CalibrationTheme.from(this) }
    private val bg get() = palette.background
    private val panel get() = palette.card
    private val accent get() = palette.accent
    private val fg get() = palette.text
    private val muted get() = palette.secondary
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var selectedLevel = 128
    private enum class LedTarget { LEFT, BOTH, RIGHT }
    private var ledTarget = LedTarget.BOTH
    private lateinit var leftButton: MaterialButton
    private lateinit var bothButton: MaterialButton
    private lateinit var rightButton: MaterialButton
    private var levelLabel: TextView? = null
    private var brightnessBar: ProgressBar? = null
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
            contentDescription = "Back to Bifrost Calibration menu"
            setOnClickListener { stopTest(); finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val logo = ImageView(this).apply {
            setImageResource(com.moonbench.bifrost.R.mipmap.ic_launcher)
            contentDescription = "Bifrost logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginStart = dp(8)
        })
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        heading.addView(label("LED BRIGHTNESS CHECK", 18f, fg, true))
        heading.addView(label("Check LED output level and consistency.", 12f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val ledSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        leftButton = button("LEFT LED", false)
        bothButton = button("BOTH LEDS", true)
        rightButton = button("RIGHT LED", false)
        listOf(leftButton, bothButton, rightButton).forEachIndexed { index, b ->
            ledSelector.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        leftButton.setOnClickListener { ledTarget = LedTarget.LEFT; updateLedSelection() }
        bothButton.setOnClickListener { ledTarget = LedTarget.BOTH; updateLedSelection() }
        rightButton.setOnClickListener { ledTarget = LedTarget.RIGHT; updateLedSelection() }
        root.addView(ledSelector, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        updateLedSelection()

        val intro = panelCard()
        intro.addView(label("LED BRIGHTNESS LEVEL", 18f, fg, true))
        intro.addView(label("Set a fixed output level or run the full brightness sweep. Both thumb sticks are tested together in neutral white.", 14f, muted).apply {
            setPadding(0, dp(8), 0, dp(12))
        })
        levelLabel = label("128 / 255  •  50%", 24f, fg, true)
        intro.addView(levelLabel)
        brightnessBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = (selectedLevel * 100f / 255f).roundToInt()
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(muted)
            contentDescription = "Current LED brightness percentage"
        }
        intro.addView(brightnessBar, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(8) })
        val slider = SeekBar(this).apply {
            max = 255
            progress = selectedLevel
            contentDescription = "LED brightness level"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedLevel = progress.coerceIn(0, 255)
                    updateBrightnessIndicator(selectedLevel)
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
        sweep.addView(label("FULL BRIGHTNESS SWEEP", 18f, fg, true))
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

    private fun updateLedSelection() {
        val selected = when (ledTarget) {
            LedTarget.LEFT -> leftButton
            LedTarget.BOTH -> bothButton
            LedTarget.RIGHT -> rightButton
        }
        listOf(leftButton, bothButton, rightButton).forEach { b ->
            val active = b === selected
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(if (active) accent else Color.TRANSPARENT)
            b.strokeWidth = dp(1)
            b.strokeColor = android.content.res.ColorStateList.valueOf(accent)
            b.setTextColor(fg)
        }
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
            sendLevel(0, 1_000L)
            Toast.makeText(this, "Brightness sweep complete", Toast.LENGTH_SHORT).show()
            return
        }
        testLevel(sweepLevels[index], 3_000L, showToast = false)
        handler.postDelayed({
            if (!running || isFinishing || isDestroyed) return@postDelayed
            // Insert a one-second LED-off pause between brightness stages.
            sendLevel(0, 1_000L)
            handler.postDelayed({ runSweepStep(index + 1) }, 1_000L)
        }, 3_000L)
    }

    private fun testLevel(level: Int, durationMs: Long, showToast: Boolean = true) {
        updateBrightnessIndicator(level)
        sendLevel(level, durationMs)
        if (showToast) Toast.makeText(this, "Testing brightness $level / 255", Toast.LENGTH_SHORT).show()
    }

    private fun updateBrightnessIndicator(level: Int) {
        val value = level.coerceIn(0, 255)
        val percent = (value * 100f / 255f).roundToInt()
        levelLabel?.text = "$value / 255  •  $percent%"
        brightnessBar?.progress = percent
    }

    private fun sendLevel(level: Int, durationMs: Long) {
        val value = level.coerceIn(0, 255)
        updateBrightnessIndicator(value)
        val color = Color.rgb(value, value, value)
        val leftColor = if (ledTarget != LedTarget.RIGHT) color else Color.BLACK
        val rightColor = if (ledTarget != LedTarget.LEFT) color else Color.BLACK
        val intent = Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_RP5_LED_TEST
            putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, leftColor)
            putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, rightColor)
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

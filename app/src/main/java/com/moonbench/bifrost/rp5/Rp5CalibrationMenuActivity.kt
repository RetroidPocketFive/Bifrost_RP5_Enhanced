package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import kotlin.math.roundToInt
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.moonbench.bifrost.R
import com.moonbench.bifrost.SamplingEditorActivity
import com.moonbench.bifrost.services.LEDService

/**
 * Calibration menu test built directly on the approved V5.1 home-screen baseline.
 *
 * This screen is intentionally only a menu. The existing calibration activity is
 * left untouched so each option can be reviewed and improved independently later.
 */
class Rp5CalibrationMenuActivity : AppCompatActivity() {

    private val bg = Color.rgb(8, 12, 24)
    private val panel = Color.rgb(16, 22, 41)
    private val accent = Color.rgb(104, 101, 242)
    private val text = Color.rgb(242, 244, 255)
    private val secondary = Color.rgb(145, 154, 190)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            clipToPadding = false
            addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        }

        val header = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            setCardBackgroundColor(panel)
            strokeWidth = dp(1)
            strokeColor = accent
            setContentPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = MaterialButton(this).apply {
            text = "‹"
            isAllCaps = false
            textSize = 30f
            setTextColor(this@Rp5CalibrationMenuActivity.text)
            backgroundTintList = android.content.res.ColorStateList.valueOf(panel)
            strokeColor = android.content.res.ColorStateList.valueOf(accent)
            strokeWidth = dp(1)
            cornerRadius = dp(12)
            minWidth = dp(48)
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { finish() }
            contentDescription = "Back to BIFROST Settings"
            tooltipText = "Back"
        }
        headerRow.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))

        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "BIFROST logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        headerRow.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
        })

        headerRow.addView(View(this).apply {
            setBackgroundColor(accent)
        }, LinearLayout.LayoutParams(dp(1), dp(40)).apply { marginEnd = dp(12) })

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBox.addView(TextView(this).apply {
            text = "BIFROST"
            textSize = 12f
            letterSpacing = 0.12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(accent)
        })
        titleBox.addView(TextView(this).apply {
            text = "CALIBRATION"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(this@Rp5CalibrationMenuActivity.text)
            maxLines = 1
        })
        titleBox.addView(TextView(this).apply {
            text = "Test and calibrate controller lighting."
            textSize = 13f
            setTextColor(secondary)
            maxLines = 2
        })
        headerRow.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(headerRow)

        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(card(
            "▣",
            "SCREEN COLOUR CHECK",
            "Choose how to get the game screen used for calibration.",
            "LIVE GAME SCREEN waits 5 seconds, then captures the game.",
            "STILL IMAGE lets you choose a screenshot.",
            listOf("LIVE IMAGE", "STILL IMAGE")
        ) { which ->
            val source = if (which == 0) SamplingEditorActivity.SOURCE_LIVE else SamplingEditorActivity.SOURCE_STILL
            startActivity(Intent(this, SamplingEditorActivity::class.java)
                .putExtra(SamplingEditorActivity.EXTRA_START_MODE, source))
        })

        content.addView(card(
            "▧",
            "EDIT SAMPLE AREA",
            "Create and manage named viewing-area profiles for Ambient colour matching.",
            "Adjust left and right sample boxes over a live or still image.",
            "Saved profiles are stored separately from calibration.",
            listOf("OPEN")
        ) {
            startActivity(Intent(this, com.moonbench.bifrost.EditSampleAreaActivity::class.java))
        })

        content.addView(card(
            "💡",
            "LED COLOUR MATCH",
            "Matches the LED colours to the game screen for accurate colour reproduction.",
            "",
            "",
            listOf("START")
        ) {
            // Open the established RGB colour-match panel, not the separate RP5 capture UI.
            startActivity(Intent(this, SamplingEditorActivity::class.java)
                .putExtra(SamplingEditorActivity.EXTRA_START_MODE, SamplingEditorActivity.MODE_COLOUR_MATCH))
        })

        content.addView(card(
            "☼",
            "LED BRIGHTNESS TEST",
            "Checks LED output at several brightness levels.",
            "Both sticks illuminate in white from dim to bright.",
            "Each level lasts briefly; keep the device visible.",
            listOf("START")
        ) {
            startActivity(Intent(this, Rp5LedBrightnessTestActivity::class.java))
        })

        setContentView(root)
    }

    private fun runBrightnessSweep() {
        val levels = listOf(32, 64, 96, 128, 176, 224, 255)
        val handler = Handler(Looper.getMainLooper())
        val stepDurationMs = 650L
        val toast = android.widget.Toast.makeText(this, "LED brightness test starting", android.widget.Toast.LENGTH_SHORT)
        toast.show()

        fun step(index: Int) {
            if (isFinishing || isDestroyed) return
            if (index >= levels.size) {
                val clear = Intent(this, LEDService::class.java).apply {
                    action = LEDService.ACTION_RP5_LED_TEST
                    putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, Color.BLACK)
                    putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, Color.BLACK)
                    putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, 300L)
                }
                runCatching { startService(clear) }
                android.widget.Toast.makeText(this, "LED brightness test complete", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val level = levels[index]
            val white = Color.rgb(level, level, level)
            val test = Intent(this, LEDService::class.java).apply {
                action = LEDService.ACTION_RP5_LED_TEST
                putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, white)
                putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, white)
                putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, stepDurationMs)
                putExtra(LEDService.EXTRA_RP5_TEST_BYPASS_CALIBRATION, true)
            }
            try {
                startService(test)
                android.widget.Toast.makeText(this, "LED brightness: $level / 255", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "Could not start LED brightness test. Check that Bifrost LED control is enabled.", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            handler.postDelayed({ step(index + 1) }, stepDurationMs + 200L)
        }
        step(0)
    }

    private fun card(
        icon: String,
        title: String,
        description: String,
        line2: String,
        line3: String,
        actions: List<String>,
        onAction: (Int) -> Unit
    ): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            setCardBackgroundColor(panel)
            strokeWidth = dp(1)
            strokeColor = accent
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val compact = resources.configuration.smallestScreenWidthDp < 720 ||
            resources.configuration.screenWidthDp < 720
        val row = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (compact) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }

        val iconBox = TextView(this).apply {
            this.text = icon
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = roundedDrawable(Color.TRANSPARENT, accent, 14)
        }
        row.addView(iconBox, LinearLayout.LayoutParams(if (compact) dp(56) else dp(64), if (compact) dp(56) else dp(64)).apply {
            if (compact) bottomMargin = dp(12)
        })

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (compact) 0 else dp(16), 0, if (compact) 0 else dp(12), 0)
            gravity = if (compact) Gravity.CENTER_HORIZONTAL else Gravity.START
        }
        textBox.addView(TextView(this).apply {
            text = title
            textSize = 16f
            gravity = if (compact) Gravity.CENTER else Gravity.START
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(this@Rp5CalibrationMenuActivity.text)
        })
        if (description.isNotEmpty()) textBox.addView(TextView(this).apply {
            text = description
            textSize = 13f
            gravity = if (compact) Gravity.CENTER else Gravity.START
            setTextColor(secondary)
            setPadding(0, dp(8), 0, 0)
        })
        if (line2.isNotEmpty()) textBox.addView(TextView(this).apply {
            text = line2
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        if (line3.isNotEmpty()) textBox.addView(TextView(this).apply {
            text = line3
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(textBox, if (compact) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))

        val actionBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.forEachIndexed { index, label ->
            val b = MaterialButton(this).apply {
                text = label
                textSize = 14f
                setTextColor(this@Rp5CalibrationMenuActivity.text)
                minHeight = dp(48)
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(12)
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (index == 0) accent else Color.TRANSPARENT)
                strokeWidth = if (index == 0) 0 else dp(1)
                strokeColor = android.content.res.ColorStateList.valueOf(accent)
                setOnClickListener { onAction(index) }
            }
            actionBox.addView(b, LinearLayout.LayoutParams(if (compact) -1 else dp(if (actions.size == 1) 180 else 160), dp(48)).apply {
                if (compact) topMargin = dp(8) else if (index > 0) marginStart = dp(8)
            })
        }
        if (compact) {
            actionBox.orientation = LinearLayout.VERTICAL
            actionBox.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
        row.addView(actionBox, if (compact) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(-2, -2))

        card.addView(row)
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(16)
            }
        }
    }

    private fun roundedDrawable(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

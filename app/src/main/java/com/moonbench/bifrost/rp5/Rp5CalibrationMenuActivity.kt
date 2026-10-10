package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import kotlin.math.roundToInt
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.moonbench.bifrost.R
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
            setPadding(dp(18), dp(12), dp(18), dp(18))
        }

        val header = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(panel)
            strokeWidth = dp(1)
            strokeColor = accent
            setContentPadding(dp(12), dp(10), dp(18), dp(10))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = MaterialButton(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(this@Rp5CalibrationMenuActivity.text)
            minWidth = dp(58)
            minHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { finish() }
            background = roundedDrawable(Color.TRANSPARENT, accent, 18)
            contentDescription = "Back"
        }
        headerRow.addView(back, LinearLayout.LayoutParams(dp(62), dp(56)))

        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            contentDescription = "Bifrost logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        headerRow.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))

        val brand = TextView(this).apply {
            this.text = "BIFROST"
            textSize = 20f
            setTextColor(this@Rp5CalibrationMenuActivity.text)
            gravity = Gravity.CENTER_VERTICAL
            letterSpacing = 0.18f
            setPadding(dp(8), 0, dp(12), 0)
        }
        headerRow.addView(brand, LinearLayout.LayoutParams(dp(128), dp(56)))

        val divider = View(this).apply { setBackgroundColor(accent) }
        headerRow.addView(divider, LinearLayout.LayoutParams(dp(1), dp(38)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "Calibration"
            textSize = 28f
            setTextColor(this@Rp5CalibrationMenuActivity.text)
        })
        titleBox.addView(TextView(this).apply {
            text = "Adjust your device for the best visual performance."
            textSize = 13f
            setTextColor(secondary)
        })
        headerRow.addView(titleBox, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(headerRow)

        root.addView(header, LinearLayout.LayoutParams(-1, dp(100)).apply {
            bottomMargin = dp(22)
        })

        root.addView(card(
            "▣",
            "SCREEN COLOUR CHECK",
            "Choose how to get the game screen used for calibration.",
            "LIVE GAME SCREEN waits 5 seconds, then captures the game.",
            "STILL IMAGE lets you choose a screenshot.",
            listOf("LIVE IMAGE", "STILL IMAGE")
        ) { which ->
            val source = if (which == 0) Rp5CalibrationActivity.SOURCE_LIVE else Rp5CalibrationActivity.SOURCE_STILL
            startActivity(Intent(this, Rp5CalibrationActivity::class.java).putExtra(Rp5CalibrationActivity.EXTRA_SOURCE, source))
        })

        root.addView(card(
            "💡",
            "LED COLOUR MATCH",
            "Matches the LED colours to the game screen for accurate colour reproduction.",
            "",
            "",
            listOf("START")
        ) {
            // Open the calibration workspace without automatically starting live capture.
            startActivity(Intent(this, Rp5CalibrationActivity::class.java)
                .putExtra(Rp5CalibrationActivity.EXTRA_MODE, Rp5CalibrationActivity.MODE_COLOUR_MATCH))
        })

        root.addView(card(
            "☼",
            "LED BRIGHTNESS TEST",
            "Checks the LED brightness levels and uniformity.",
            "Adjust if needed for even lighting.",
            "",
            listOf("START")
        ) {
            // Run a short neutral-white LED test directly; do not navigate to calibration.
            val test = Intent(this, LEDService::class.java).apply {
                action = LEDService.ACTION_RP5_LED_TEST
                putExtra(LEDService.EXTRA_RP5_LEFT_COLOR, Color.WHITE)
                putExtra(LEDService.EXTRA_RP5_RIGHT_COLOR, Color.WHITE)
                putExtra(LEDService.EXTRA_RP5_TEST_DURATION_MS, 1500L)
            }
            runCatching { startService(test) }.onFailure {
                android.widget.Toast.makeText(this, "Could not start LED brightness test", android.widget.Toast.LENGTH_LONG).show()
            }
        })

        setContentView(root)
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
            radius = dp(20).toFloat()
            setCardBackgroundColor(panel)
            strokeWidth = dp(1)
            strokeColor = accent
            setContentPadding(dp(32), dp(24), dp(24), dp(24))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = TextView(this).apply {
            this.text = icon
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = roundedDrawable(Color.TRANSPARENT, accent, 14)
        }
        row.addView(iconBox, LinearLayout.LayoutParams(dp(92), dp(90)))

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), 0, dp(18), 0)
        }
        textBox.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(this@Rp5CalibrationMenuActivity.text)
        })
        if (description.isNotEmpty()) textBox.addView(TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(8), 0, 0)
        })
        if (line2.isNotEmpty()) textBox.addView(TextView(this).apply {
            text = line2
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        if (line3.isNotEmpty()) textBox.addView(TextView(this).apply {
            text = line3
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))

        val actionBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.forEachIndexed { index, label ->
            val b = MaterialButton(this).apply {
                text = label
                textSize = 14f
                setTextColor(this@Rp5CalibrationMenuActivity.text)
                minHeight = dp(56)
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(28)
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (index == 0) accent else Color.TRANSPARENT)
                strokeWidth = if (index == 0) 0 else dp(1)
                strokeColor = android.content.res.ColorStateList.valueOf(accent)
                setOnClickListener { onAction(index) }
            }
            actionBox.addView(b, LinearLayout.LayoutParams(dp(if (actions.size == 1) 300 else 270), dp(56)).apply {
                if (index > 0) marginStart = dp(18)
            })
        }
        row.addView(actionBox)

        card.addView(row)
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(if (actions.size == 2) 196 else 180)).apply {
                bottomMargin = dp(22)
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

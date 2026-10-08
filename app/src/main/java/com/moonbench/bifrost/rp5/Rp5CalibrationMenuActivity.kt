package com.moonbench.bifrost.rp5

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

/** V5.1 baseline calibration menu. Existing calibration engine is intentionally reused. */
class Rp5CalibrationMenuActivity : AppCompatActivity() {
    private val bg = Color.rgb(8, 12, 24)
    private val panel = Color.rgb(16, 22, 41)
    private val accent = Color.rgb(104, 101, 242)
    private val primary = Color.rgb(242, 244, 255)
    private val secondary = Color.rgb(145, 154, 190)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val header = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            setCardBackgroundColor(panel)
            strokeWidth = dp(1)
            strokeColor = accent
            setContentPadding(dp(10), dp(8), dp(12), dp(8))
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = MaterialButton(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(primary)
            minWidth = dp(48); minHeight = dp(48)
            insetTop = 0; insetBottom = 0
            cornerRadius = dp(12)
            backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
            strokeWidth = dp(1)
            strokeColor = android.content.res.ColorStateList.valueOf(accent)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        headerRow.addView(back, LinearLayout.LayoutParams(dp(52), dp(48)))
        val brand = TextView(this).apply {
            text = "◉  BIFROST"
            textSize = 16f
            setTextColor(primary)
            letterSpacing = .12f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        headerRow.addView(brand, LinearLayout.LayoutParams(dp(150), dp(48)))
        headerRow.addView(View(this).apply { setBackgroundColor(accent) }, LinearLayout.LayoutParams(dp(1), dp(34)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        heading.addView(TextView(this).apply {
            text = "Calibration"
            textSize = 22f
            maxLines = 1
            setTextColor(primary)
        })
        heading.addView(TextView(this).apply {
            text = "Adjust your device for the best visual performance."
            textSize = 12f
            maxLines = 2
            setTextColor(secondary)
        })
        headerRow.addView(heading, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(headerRow)
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        content.addView(menuCard(
            "▣", "SCREEN COLOUR CHECK",
            "Choose the game-screen source used by the original calibration.",
            listOf("LIVE IMAGE", "STILL IMAGE")
        ) { which ->
            val source = if (which == 0) Rp5CalibrationActivity.SOURCE_LIVE else Rp5CalibrationActivity.SOURCE_STILL
            startActivity(Intent(this, Rp5CalibrationActivity::class.java).putExtra(Rp5CalibrationActivity.EXTRA_SOURCE_MODE, source))
        })
        content.addView(menuCard(
            "●", "LED THUMB-STICK COLOUR MATCH",
            "Uses the original left/right thumb-stick colour sampling and LED test.",
            listOf("OPEN CALIBRATION")
        ) {
            // Reuse original V5.1 implementation; do not replace its calibration logic.
            startActivity(Intent(this, com.moonbench.bifrost.SamplingEditorActivity::class.java))
        })
        content.addView(menuCard(
            "☼", "LED BRIGHTNESS TEST",
            "Brightness testing will be refined separately after this menu is approved.",
            listOf("NOT CONFIGURED")
        ) { })
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun menuCard(icon: String, title: String, description: String, actions: List<String>, onAction: (Int) -> Unit): View {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            setCardBackgroundColor(panel)
            strokeWidth = dp(1)
            strokeColor = accent
            setContentPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(Color.TRANSPARENT, accent, 12)
        }
        top.addView(iconView, LinearLayout.LayoutParams(dp(54), dp(54)))
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        copy.addView(TextView(this).apply {
            text = title
            textSize = 15f
            maxLines = 2
            setTextColor(primary)
        })
        copy.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        top.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(top)
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(66), dp(10), 0, 0)
        }
        actions.forEachIndexed { index, label ->
            val b = MaterialButton(this).apply {
                text = label
                textSize = 12f
                maxLines = 1
                minHeight = dp(48)
                insetTop = 0; insetBottom = 0
                cornerRadius = dp(24)
                setTextColor(primary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (label == "NOT CONFIGURED") Color.rgb(45, 49, 75) else accent)
                setOnClickListener { if (label != "NOT CONFIGURED") onAction(index) }
            }
            actionsRow.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        column.addView(actionsRow)
        card.addView(column)
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

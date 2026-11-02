package com.moonbench.bifrost.rp5

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Shared palette for calibration screens, sourced from the app's selected UI theme. */
internal data class CalibrationTheme(
    val background: Int,
    val card: Int,
    val surface: Int,
    val accent: Int,
    val accentLight: Int,
    val text: Int,
    val secondary: Int,
    val border: Int,
) {
    companion object {
        fun from(context: Context): CalibrationTheme {
            val id = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
                .getString("selected_ui_theme", "classic") ?: "classic"
            fun color(value: String) = Color.parseColor(value)
            return when (id) {
                "pip_boy" -> CalibrationTheme(
                    color("#020B05"), color("#061A0A"), color("#0B2A10"),
                    color("#39FF14"), color("#7CFF6B"), color("#A8FFB0"),
                    color("#7CFF6B"), color("#24552B")
                )
                "oled_purple" -> CalibrationTheme(
                    color("#10091A"), color("#1B1029"), color("#2A1740"),
                    color("#C084FC"), color("#D8B4FE"), color("#C084FC"),
                    color("#B78AE8"), color("#43265F")
                )
                else -> CalibrationTheme(
                    color("#080C18"), color("#101629"), color("#151D33"),
                    color("#6865F2"), color("#818CF8"), color("#F2F4FF"),
                    color("#8992AD"), color("#252E48")
                )
            }
        }
    }
}

/** Apply the active palette to a programmatically built calibration screen. */
internal fun applyCalibrationTheme(root: View, theme: CalibrationTheme) {
    root.setBackgroundColor(theme.background)
    fun visit(view: View) {
        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(theme.card)
                view.strokeColor = theme.accent
            }
            is MaterialButton -> {
                if (view.id != com.moonbench.bifrost.R.id.leftReferenceButton &&
                    view.id != com.moonbench.bifrost.R.id.rightReferenceButton) {
                    view.backgroundTintList = ColorStateList.valueOf(theme.surface)
                    view.strokeColor = ColorStateList.valueOf(theme.accent)
                    view.setTextColor(theme.text)
                }
            }
            is TextView -> view.setTextColor(theme.text)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
    }
    visit(root)
}

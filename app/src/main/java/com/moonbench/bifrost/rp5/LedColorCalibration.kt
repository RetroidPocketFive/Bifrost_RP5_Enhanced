package com.moonbench.bifrost.rp5

import android.content.Context
import android.graphics.Color

/**
 * Per-stick RGB calibration.
 *
 * Each calibrated primary stores the raw RGB command that made the physical LED
 * look like that primary on the target screen. Those three command vectors form
 * the columns of a 3x3 correction matrix, allowing cross-channel errors such as
 * "green command looks blue" to be corrected instead of only applying per-channel
 * brightness gains.
 */
object LedColorCalibration {
    enum class Stick { LEFT, RIGHT }

    enum class Primary(val targetColor: Int) {
        RED(Color.RED),
        GREEN(Color.GREEN),
        BLUE(Color.BLUE)
    }

    data class CommandColor(val red: Int, val green: Int, val blue: Int) {
        fun packed(): Int =
            (red.coerceIn(0, 255) shl 16) or
                (green.coerceIn(0, 255) shl 8) or
                blue.coerceIn(0, 255)
    }

    private const val PREFS_NAME = "bifrost_led_color_calibration"

    private fun prefix(stick: Stick): String =
        if (stick == Stick.LEFT) "left_" else "right_"

    private fun key(stick: Stick, primary: Primary, channel: String): String =
        prefix(stick) + primary.name.lowercase() + "_" + channel

    fun getPrimaryCommand(context: Context, stick: Stick, primary: Primary): CommandColor {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val target = primary.targetColor
        return CommandColor(
            prefs.getInt(key(stick, primary, "r"), Color.red(target)),
            prefs.getInt(key(stick, primary, "g"), Color.green(target)),
            prefs.getInt(key(stick, primary, "b"), Color.blue(target))
        )
    }

    fun setPrimaryCommand(
        context: Context,
        stick: Stick,
        primary: Primary,
        command: CommandColor
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(key(stick, primary, "r"), command.red.coerceIn(0, 255))
            .putInt(key(stick, primary, "g"), command.green.coerceIn(0, 255))
            .putInt(key(stick, primary, "b"), command.blue.coerceIn(0, 255))
            .apply()
    }

    fun reset(context: Context, stick: Stick) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (primary in Primary.values()) {
            editor.remove(key(stick, primary, "r"))
            editor.remove(key(stick, primary, "g"))
            editor.remove(key(stick, primary, "b"))
        }
        editor.apply()
    }

    fun apply(context: Context, stick: Stick, color: Int): Int {
        val red = getPrimaryCommand(context, stick, Primary.RED)
        val green = getPrimaryCommand(context, stick, Primary.GREEN)
        val blue = getPrimaryCommand(context, stick, Primary.BLUE)

        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        // The three calibrated command vectors are the matrix columns.
        val outR = red.red * r + green.red * g + blue.red * b
        val outG = red.green * r + green.green * g + blue.green * b
        val outB = red.blue * r + green.blue * g + blue.blue * b

        return Color.rgb(
            outR.roundToInt().coerceIn(0, 255),
            outG.roundToInt().coerceIn(0, 255),
            outB.roundToInt().coerceIn(0, 255)
        )
    }

    fun applyDual(context: Context, left: Int, right: Int): Pair<Int, Int> =
        apply(context, Stick.LEFT, left) to apply(context, Stick.RIGHT, right)

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}

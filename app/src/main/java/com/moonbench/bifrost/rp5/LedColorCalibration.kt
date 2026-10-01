package com.moonbench.bifrost.rp5

import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * RP5 thumb-stick LED colour calibration.
 *
 * The calibration is a 3x3 command matrix for each physical stick.  RED/GREEN/BLUE
 * are the matrix primaries; WHITE is intentionally only a verification colour.
 * This mirrors the calibration model used by the 1.7.0-alpha.3 reference APK.
 */
data class LedColorCalibration(
    val left: StickCalibration = StickCalibration.identity(),
    val right: StickCalibration = StickCalibration.identity(),
    val version: Int = CURRENT_VERSION,
) {
    data class CommandColor(val red: Int, val green: Int, val blue: Int) {
        fun clamped(): CommandColor = CommandColor(
            red.coerceIn(0, 255),
            green.coerceIn(0, 255),
            blue.coerceIn(0, 255),
        )
    }

    enum class Primary { RED, GREEN, BLUE, WHITE }
    enum class Stick { LEFT, RIGHT }

    data class StickCalibration(
        val red: CommandColor,
        val green: CommandColor,
        val blue: CommandColor,
    ) {
        fun commandFor(screenColor: Int): Int {
            val sr = Color.red(screenColor) / 255f
            val sg = Color.green(screenColor) / 255f
            val sb = Color.blue(screenColor) / 255f

            val r = (red.red * sr + green.red * sg + blue.red * sb).roundToInt()
            val g = (red.green * sr + green.green * sg + blue.green * sb).roundToInt()
            val b = (red.blue * sr + green.blue * sg + blue.blue * sb).roundToInt()
            return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }

        companion object {
            fun identity() = StickCalibration(
                red = CommandColor(255, 0, 0),
                green = CommandColor(0, 255, 0),
                blue = CommandColor(0, 0, 255),
            )
        }
    }

    fun commandFor(stick: Stick, screenColor: Int): Int =
        (if (stick == Stick.LEFT) left else right).commandFor(screenColor)

    fun withPrimary(stick: Stick, primary: Primary, command: CommandColor): LedColorCalibration {
        if (primary == Primary.WHITE) return this
        val current = if (stick == Stick.LEFT) left else right
        val updated = when (primary) {
            Primary.RED -> current.copy(red = command.clamped())
            Primary.GREEN -> current.copy(green = command.clamped())
            Primary.BLUE -> current.copy(blue = command.clamped())
            Primary.WHITE -> current
        }
        return if (stick == Stick.LEFT) copy(left = updated) else copy(right = updated)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("left", stickToJson(left))
        put("right", stickToJson(right))
    }

    private fun stickToJson(stick: StickCalibration): JSONObject = JSONObject().apply {
        put("red", colorToJson(stick.red))
        put("green", colorToJson(stick.green))
        put("blue", colorToJson(stick.blue))
    }

    private fun colorToJson(color: CommandColor): JSONObject = JSONObject().apply {
        put("r", color.red)
        put("g", color.green)
        put("b", color.blue)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val PREF_KEY = "bifrost_led_color_calibration"

        fun load(prefs: SharedPreferences): LedColorCalibration {
            val raw = prefs.getString(PREF_KEY, null) ?: return LedColorCalibration()
            return runCatching {
                val root = JSONObject(raw)
                val left = parseStick(root.optJSONObject("left")) ?: StickCalibration.identity()
                val right = parseStick(root.optJSONObject("right")) ?: StickCalibration.identity()
                LedColorCalibration(left = left, right = right, version = root.optInt("version", CURRENT_VERSION))
            }.getOrDefault(LedColorCalibration())
        }

        fun save(prefs: SharedPreferences, calibration: LedColorCalibration) {
            prefs.edit().putString(PREF_KEY, calibration.toJson().toString()).apply()
        }

        fun reset(prefs: SharedPreferences) {
            prefs.edit().remove(PREF_KEY).apply()
        }

        private fun parseStick(obj: JSONObject?): StickCalibration? {
            if (obj == null) return null
            fun parseColor(key: String): CommandColor? {
                val c = obj.optJSONObject(key) ?: return null
                return CommandColor(c.optInt("r", 0), c.optInt("g", 0), c.optInt("b", 0)).clamped()
            }
            val red = parseColor("red") ?: return null
            val green = parseColor("green") ?: return null
            val blue = parseColor("blue") ?: return null
            return StickCalibration(red, green, blue)
        }
    }
}

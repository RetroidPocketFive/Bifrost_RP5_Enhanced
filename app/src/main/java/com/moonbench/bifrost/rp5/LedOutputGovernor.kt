package com.moonbench.bifrost.rp5

/**
 * V6 output safety/governor.
 *
 * Keeps the user's requested LED brightness as the ceiling, then applies
 * conservative automatic caps for low battery and high battery temperature.
 * The function is pure so the policy can be tested independently of Android.
 */
object LedOutputGovernor {

    data class Inputs(
        val requestedBrightness: Int,
        val batteryPercent: Int = 100,
        val batteryTemperatureC: Float? = null,
        val pluggedIn: Boolean = false,
    )

    data class Decision(
        val brightness: Int,
        val scale: Float,
        val batteryScale: Float,
        val thermalScale: Float,
    )

    fun evaluate(input: Inputs): Decision {
        val requested = input.requestedBrightness.coerceIn(0, 255)
        val battery = input.batteryPercent.coerceIn(0, 100)

        val batteryScale = when {
            input.pluggedIn -> 1f
            battery <= 5 -> 0.50f
            battery <= 10 -> 0.70f
            battery <= 15 -> 0.85f
            else -> 1f
        }

        val thermalScale = when {
            input.batteryTemperatureC == null -> 1f
            input.batteryTemperatureC >= 50f -> 0.25f
            input.batteryTemperatureC >= 48f -> 0.50f
            input.batteryTemperatureC >= 45f -> 0.75f
            input.batteryTemperatureC >= 42f -> 0.90f
            else -> 1f
        }

        val scale = minOf(batteryScale, thermalScale)
        return Decision(
            brightness = (requested * scale).toInt().coerceIn(0, 255),
            scale = scale,
            batteryScale = batteryScale,
            thermalScale = thermalScale,
        )
    }
}

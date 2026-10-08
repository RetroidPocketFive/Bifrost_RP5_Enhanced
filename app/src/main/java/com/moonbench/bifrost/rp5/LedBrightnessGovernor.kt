package com.moonbench.bifrost.rp5

/**
 * V6 brightness governor.
 *
 * Converts device conditions into one bounded LED brightness scale. The class is
 * deliberately pure so the service can feed it real battery/thermal/screen data
 * without coupling the policy to Android framework APIs.
 */
data class LedGovernorInput(
    val userBrightness: Float = 1f,
    val screenBrightness: Float = 1f,
    val batteryPercent: Int = 100,
    val temperatureC: Float? = null,
    val charging: Boolean = false,
    val adaptiveScreenBrightness: Boolean = false,
)

data class LedGovernorOutput(
    val brightnessScale: Float,
    val thermalScale: Float,
    val batteryScale: Float,
    val screenScale: Float,
)

class LedBrightnessGovernor(
    private val minimumScale: Float = 0.20f,
    private val batteryLimitPercent: Int = 15,
    private val batteryFloorScale: Float = 0.55f,
    private val thermalStartC: Float = 45f,
    private val thermalLimitC: Float = 55f,
    private val thermalFloorScale: Float = 0.45f,
) {
    init {
        require(minimumScale in 0f..1f)
        require(batteryLimitPercent in 0..100)
        require(batteryFloorScale in 0f..1f)
        require(thermalStartC < thermalLimitC)
        require(thermalFloorScale in 0f..1f)
    }

    fun evaluate(input: LedGovernorInput): LedGovernorOutput {
        val user = input.userBrightness.coerceIn(0f, 1f)
        val screen = if (input.adaptiveScreenBrightness) {
            0.25f + input.screenBrightness.coerceIn(0f, 1f) * 0.75f
        } else 1f

        val battery = if (input.charging || input.batteryPercent > batteryLimitPercent) {
            1f
        } else {
            val progress = input.batteryPercent.coerceIn(0, batteryLimitPercent) /
                batteryLimitPercent.coerceAtLeast(1).toFloat()
            batteryFloorScale + progress * (1f - batteryFloorScale)
        }

        val thermal = input.temperatureC?.let { temperature ->
            when {
                temperature <= thermalStartC -> 1f
                temperature >= thermalLimitC -> thermalFloorScale
                else -> {
                    val progress = (temperature - thermalStartC) / (thermalLimitC - thermalStartC)
                    1f - progress * (1f - thermalFloorScale)
                }
            }
        } ?: 1f

        val scale = (user * screen * battery * thermal).coerceIn(minimumScale, 1f)
        return LedGovernorOutput(scale, thermal, battery, screen)
    }
}

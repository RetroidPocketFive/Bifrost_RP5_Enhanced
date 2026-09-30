package com.moonbench.bifrost.rp5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedBrightnessGovernorTest {
    @Test fun fullConditionsPreserveRequestedBrightness() {
        val output = LedBrightnessGovernor().evaluate(
            LedGovernorInput(userBrightness = 0.8f)
        )
        assertEquals(0.8f, output.brightnessScale, 0.0001f)
    }

    @Test fun adaptiveScreenBrightnessScalesOutput() {
        val output = LedBrightnessGovernor().evaluate(
            LedGovernorInput(
                userBrightness = 1f,
                screenBrightness = 0f,
                adaptiveScreenBrightness = true
            )
        )
        assertEquals(0.25f, output.screenScale, 0.0001f)
        assertEquals(0.25f, output.brightnessScale, 0.0001f)
    }

    @Test fun lowBatteryReducesOutputButChargingBypassesBatteryLimit() {
        val governor = LedBrightnessGovernor()
        val low = governor.evaluate(LedGovernorInput(batteryPercent = 0))
        val charging = governor.evaluate(LedGovernorInput(batteryPercent = 0, charging = true))
        assertEquals(0.55f, low.batteryScale, 0.0001f)
        assertEquals(1f, charging.batteryScale, 0.0001f)
    }

    @Test fun thermalLimitingRampsBetweenThresholds() {
        val governor = LedBrightnessGovernor()
        val cool = governor.evaluate(LedGovernorInput(temperatureC = 45f))
        val hot = governor.evaluate(LedGovernorInput(temperatureC = 55f))
        val middle = governor.evaluate(LedGovernorInput(temperatureC = 50f))
        assertEquals(1f, cool.thermalScale, 0.0001f)
        assertEquals(0.45f, hot.thermalScale, 0.0001f)
        assertTrue(middle.thermalScale < 1f && middle.thermalScale > 0.45f)
    }

    @Test fun minimumOutputPreventsZeroLedCommand() {
        val output = LedBrightnessGovernor().evaluate(
            LedGovernorInput(userBrightness = 0f, batteryPercent = 0, temperatureC = 60f)
        )
        assertEquals(0.20f, output.brightnessScale, 0.0001f)
    }
}

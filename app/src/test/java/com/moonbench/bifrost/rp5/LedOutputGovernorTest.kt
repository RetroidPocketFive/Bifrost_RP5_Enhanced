package com.moonbench.bifrost.rp5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LedOutputGovernorTest {

    @Test
    fun normalConditionsPreserveRequestedBrightness() {
        val d = LedOutputGovernor.evaluate(
            LedOutputGovernor.Inputs(
                requestedBrightness = 200,
                batteryPercent = 80,
                batteryTemperatureC = 35f,
            )
        )
        assertEquals(200, d.brightness)
        assertEquals(1f, d.scale, 0.001f)
    }

    @Test
    fun lowBatteryAppliesConservativeCapWhenUnplugged() {
        val d = LedOutputGovernor.evaluate(
            LedOutputGovernor.Inputs(
                requestedBrightness = 200,
                batteryPercent = 10,
                batteryTemperatureC = 35f,
            )
        )
        assertEquals(140, d.brightness)
        assertEquals(0.70f, d.batteryScale, 0.001f)
    }

    @Test
    fun chargingDisablesBatteryCap() {
        val d = LedOutputGovernor.evaluate(
            LedOutputGovernor.Inputs(
                requestedBrightness = 200,
                batteryPercent = 5,
                batteryTemperatureC = 35f,
                pluggedIn = true,
            )
        )
        assertEquals(200, d.brightness)
    }

    @Test
    fun hotBatteryAppliesThermalCap() {
        val d = LedOutputGovernor.evaluate(
            LedOutputGovernor.Inputs(
                requestedBrightness = 200,
                batteryPercent = 80,
                batteryTemperatureC = 46f,
            )
        )
        assertEquals(150, d.brightness)
        assertEquals(0.75f, d.thermalScale, 0.001f)
    }

    @Test
    fun strongestLimitWins() {
        val d = LedOutputGovernor.evaluate(
            LedOutputGovernor.Inputs(
                requestedBrightness = 255,
                batteryPercent = 5,
                batteryTemperatureC = 46f,
            )
        )
        assertEquals(127, d.brightness)
        assertEquals(0.50f, d.scale, 0.001f)
    }
}

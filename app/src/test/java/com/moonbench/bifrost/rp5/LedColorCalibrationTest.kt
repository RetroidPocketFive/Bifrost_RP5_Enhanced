package com.moonbench.bifrost.rp5

import org.junit.Assert.assertEquals
import org.junit.Test

class LedColorCalibrationTest {
    @Test
    fun identityMappingPreservesColor() {
        val color = 0x1478F0
        val red = LedColorCalibration.CommandColor(255, 0, 0)
        val green = LedColorCalibration.CommandColor(0, 255, 0)
        val blue = LedColorCalibration.CommandColor(0, 0, 255)
        assertEquals(color, LedColorCalibration.applyWithCommands(color, red, green, blue))
    }

    @Test
    fun calibratedPrimaryCanCorrectCrossChannelOutput() {
        val red = LedColorCalibration.CommandColor(255, 0, 0)
        val green = LedColorCalibration.CommandColor(0, 255, 120)
        val blue = LedColorCalibration.CommandColor(0, 10, 255)
        assertEquals(
            0x00FF78,
            LedColorCalibration.applyWithCommands(0x00FF00, red, green, blue)
        )
    }

    @Test
    fun transformUsesAllThreePrimaryColumns() {
        val red = LedColorCalibration.CommandColor(240, 20, 0)
        val green = LedColorCalibration.CommandColor(0, 200, 80)
        val blue = LedColorCalibration.CommandColor(10, 0, 250)
        assertEquals(
            0x0AC8FA,
            LedColorCalibration.applyWithCommands(0x00FFFF, red, green, blue)
        )
    }
}

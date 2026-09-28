package com.moonbench.bifrost.rp5

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LedColorCalibrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bifrost_led_color_calibration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun identityMappingPreservesColor() {
        val color = Color.rgb(20, 120, 240)
        assertEquals(color, LedColorCalibration.apply(context, LedColorCalibration.Stick.LEFT, color))
    }

    @Test
    fun calibratedPrimaryCanCorrectCrossChannelOutput() {
        LedColorCalibration.setPrimaryCommand(
            context, LedColorCalibration.Stick.LEFT,
            LedColorCalibration.Primary.RED,
            LedColorCalibration.CommandColor(255, 0, 0)
        )
        LedColorCalibration.setPrimaryCommand(
            context, LedColorCalibration.Stick.LEFT,
            LedColorCalibration.Primary.GREEN,
            LedColorCalibration.CommandColor(0, 255, 120)
        )
        LedColorCalibration.setPrimaryCommand(
            context, LedColorCalibration.Stick.LEFT,
            LedColorCalibration.Primary.BLUE,
            LedColorCalibration.CommandColor(0, 10, 255)
        )

        assertEquals(
            Color.rgb(0, 255, 120),
            LedColorCalibration.apply(context, LedColorCalibration.Stick.LEFT, Color.GREEN)
        )
    }

    @Test
    fun resetRestoresIdentityMapping() {
        LedColorCalibration.setPrimaryCommand(
            context, LedColorCalibration.Stick.RIGHT,
            LedColorCalibration.Primary.GREEN,
            LedColorCalibration.CommandColor(0, 200, 255)
        )
        LedColorCalibration.reset(context, LedColorCalibration.Stick.RIGHT)

        assertEquals(
            Color.GREEN,
            LedColorCalibration.apply(context, LedColorCalibration.Stick.RIGHT, Color.GREEN)
        )
    }
}

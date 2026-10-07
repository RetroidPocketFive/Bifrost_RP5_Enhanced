package com.moonbench.bifrost.rp5

/** Temporary state used by LED Calibration; never persisted as a Fine Tune. */
data class CalibrationSession(
    val leftViewArea: NormalizedRegion = NormalizedRegion(0.25f, 0.78f, 0.18f),
    val rightViewArea: NormalizedRegion = NormalizedRegion(0.75f, 0.78f, 0.18f),
    val samplingMethod: SamplingMethod = SamplingMethod.CENTER_WEIGHTED,
    val showSamplingAreas: Boolean = true,
)
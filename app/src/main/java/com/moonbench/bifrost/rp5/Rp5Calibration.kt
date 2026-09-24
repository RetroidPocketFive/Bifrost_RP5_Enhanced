package com.moonbench.bifrost.rp5

data class Rp5Calibration(
    val left: NormalizedRegion = NormalizedRegion(0.25f, 0.78f, 0.18f),
    val right: NormalizedRegion = NormalizedRegion(0.75f, 0.78f, 0.18f),
    val method: SamplingMethod = SamplingMethod.CENTER_WEIGHTED,
    val showSamplingAreas: Boolean = true,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

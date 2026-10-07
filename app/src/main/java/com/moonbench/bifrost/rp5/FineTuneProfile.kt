package com.moonbench.bifrost.rp5

/**
 * Reusable thumb-stick viewing-area configuration intended to be selected by
 * a Bifrost profile.
 */
data class FineTuneProfile(
    val name: String,
    val leftViewArea: NormalizedRegion,
    val rightViewArea: NormalizedRegion,
    val colourMode: ThumbstickColourMode = ThumbstickColourMode.AVERAGE,
    val version: Int = CURRENT_VERSION,
) {
    init {
        require(name.isNotBlank()) { "Fine Tune name must not be blank" }
        require(version > 0) { "Fine Tune version must be positive" }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
package com.mochyy.mochymusic.eq.autoeq

import kotlinx.serialization.Serializable

/** which kind of headphone a measurement was made on; picks the icon and the search grouping */
enum class HeadphoneForm { OverEar, InEar, Earbud }

/** how a correction is fitted: autoeq's own 10-band fit unless something here is changed */
@Serializable
data class AutoEqTuning(
    val bands: Int = 10,
    val maxHz: Int = 16_000,
    /** a low shelf at 105 hz added to the target */
    val bassDb: Double = 0.0,
    /** db per octave around 1 khz, positive brighter */
    val tiltDbPerOctave: Double = 0.0,
) {
    val isDefault: Boolean get() = bands == 10 && maxHz == 16_000 && bassDb == 0.0 && tiltDbPerOctave == 0.0

    companion object {
        val BandChoices = listOf(5, 8, 10, 15)
        val MaxHzChoices = listOf(10_000, 16_000, 20_000)
    }
}

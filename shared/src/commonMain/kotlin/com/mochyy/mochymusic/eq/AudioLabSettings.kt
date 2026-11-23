package com.mochyy.mochymusic.eq

import com.mochyy.mochymusic.eq.audio.EightDParams
import com.mochyy.mochymusic.eq.audio.LoudnessParams
import com.mochyy.mochymusic.eq.audio.ReplayGainMode
import com.mochyy.mochymusic.eq.audio.ReplayGainParams
import com.mochyy.mochymusic.eq.audio.SpatialMode
import com.mochyy.mochymusic.eq.audio.SpatialParams
import com.mochyy.mochymusic.eq.audio.VolumeCurve
import com.mochyy.mochymusic.eq.autoeq.AutoEqTuning
import com.mochyy.mochymusic.eq.autoeq.HeadphoneForm
import com.mochyy.mochymusic.eq.data.FilterType
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import kotlinx.serialization.Serializable
import kotlin.math.pow

/*
 * Everything Audio Lab saves: the equalizer curve and its presets, the audio fx settings and the
 * fx presets. These are plain serializable models so the shared screens (and one day the desktop
 * and iOS players) can read them; AudioLabStore itself stays on Android because it pushes every
 * change into the media3 processors and saves into the "audio_lab" SharedPreferences file.
 */

/** what sonic, the player's speed stage, can do */
const val MinSpeed = 0.1f
const val MaxSpeed = 8f

/** a headphone correction picked from the autoeq database, with the filters it came to */
@Serializable
data class AutoEqCorrection(
    val model: String,
    val source: String,
    val rig: String? = null,
    val path: String,
    val form: HeadphoneForm = HeadphoneForm.OverEar,
    val tuning: AutoEqTuning = AutoEqTuning(),
    val filters: List<ParametricEQBand>,
    val preamp: Double,
)

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val nodes: List<ParametricEQBand>,
    val preamp: Double = 0.0,
)

@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    /** the user's own curve: the graph's nodes and the bands' sliders are the same list */
    val nodes: List<ParametricEQBand> = EqPresets.flatNodes(),
    val preamp: Double = 0.0,
    /** pulls the level down by the part of the boost the limiter can't catch, so nothing clips */
    val autoPreamp: Boolean = true,
    /** the preset the curve still matches; null once it's been edited */
    val presetId: String? = EqPresets.FlatId,
    val correction: AutoEqCorrection? = null,
    val correctionEnabled: Boolean = true,
)

@Serializable
data class LoudnessSettings(
    val enabled: Boolean = false,
    val boostDb: Float = 4f,
    val compression: Float = 0.3f,
) {
    fun toParams() = LoudnessParams(enabled, boostDb, compression)
}

@Serializable
data class SpatialSettings(
    val enabled: Boolean = false,
    val mode: SpatialMode = SpatialMode.Crossfeed,
    val cutoffHz: Float = 700f,
    val feedDb: Float = 4.5f,
    val speakerAngle: Float = 30f,
    val amount: Float = 1f,
    /** dolby atmos / surround rendered binaurally by mochy rather than by the device */
    val surround: Boolean = false,
    /** both ears hear the whole mix */
    val mono: Boolean = false,
) {
    fun toParams() = SpatialParams(enabled, mode, cutoffHz, feedDb, speakerAngle, amount, surround, mono)
}

/** "even out songs" for files that carry replaygain (monochrome's flacs, many local files) */
@Serializable
data class NormalizeSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Track,
    val preampDb: Float = 3f,
) {
    fun toParams() = ReplayGainParams(replayGainMode, preampDb)
}

@Serializable
data class PlaybackSettings(
    val speed: Float = 1f,
    /** off: pitch follows speed, like a record played fast or slow */
    val preservePitch: Boolean = true,
    /** semitones, while pitch is preserved */
    val pitchSemitones: Float = 0f,
) {
    val pitch: Float
        get() = if (preservePitch) 2f.pow(pitchSemitones / 12f) else speed

    val isNormal: Boolean get() = speed == 1f && pitch == 1f
}

@Serializable
data class VolumeSettings(
    val enabled: Boolean = false,
    val exponent: Float = 2f,
    val limit: Float = 1f,
) {
    fun toCurve() = VolumeCurve(enabled, exponent, limit)
}

@Serializable
data class EightDSettings(
    val enabled: Boolean = false,
    val rotationSpeedHz: Float = 0.15f,
    val depth: Float = 0.85f,
    val reverbLevel: Float = 0.4f,
) {
    fun normalized() = copy(
        rotationSpeedHz = rotationSpeedHz.coerceIn(0.05f, 0.5f),
        depth = depth.coerceIn(0f, 1f),
        reverbLevel = reverbLevel.coerceIn(0f, 1f),
    )

    fun toParams() = EightDParams(enabled, rotationSpeedHz, depth, reverbLevel)
}

/** the built-in curves, as gains for the ten classic bands */
object EqPresets {
    const val FlatId = "flat"

    val Frequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    private const val OctaveQ = 1.41

    class Preset(val id: String, val name: String, val gains: DoubleArray) {
        val nodes: List<ParametricEQBand> get() = nodesFor(gains)
    }

    fun nodesFor(gains: DoubleArray): List<ParametricEQBand> = Frequencies.mapIndexed { i, f ->
        ParametricEQBand(frequency = f, gain = gains.getOrElse(i) { 0.0 }, q = OctaveQ, filterType = FilterType.PK)
    }

    fun flatNodes(): List<ParametricEQBand> = nodesFor(DoubleArray(Frequencies.size))

    val BuiltIn = listOf(
        Preset(FlatId, "Flat", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset("acoustic", "Acoustic", doubleArrayOf(4.5, 4.5, 3.5, 1.0, 1.5, 1.5, 3.0, 3.5, 3.0, 1.5)),
        Preset("bass_boost", "Bass Booster", doubleArrayOf(5.5, 4.5, 3.5, 2.5, 1.2, 0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset("bass_cut", "Bass Reducer", doubleArrayOf(-5.5, -4.5, -3.5, -2.5, -1.2, 0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset("classical", "Classical", doubleArrayOf(4.5, 3.5, 3.0, 2.5, -1.5, -1.5, 0.0, 2.0, 3.0, 3.5)),
        Preset("dance", "Dance", doubleArrayOf(3.5, 6.5, 5.0, 0.0, 2.0, 3.5, 5.0, 4.5, 3.5, 0.0)),
        Preset("deep", "Deep", doubleArrayOf(5.0, 3.5, 1.5, 1.0, 3.0, 2.5, 1.5, -2.0, -3.5, -4.5)),
        Preset("electronic", "Electronic", doubleArrayOf(4.5, 4.0, 1.5, 0.0, -2.0, 2.0, 1.0, 1.5, 4.5, 5.0)),
        Preset("hiphop", "Hip-Hop", doubleArrayOf(5.0, 4.0, 1.5, 3.0, -1.0, -1.0, 1.5, -0.5, 2.0, 3.0)),
        Preset("jazz", "Jazz", doubleArrayOf(4.0, 3.0, 1.5, 2.0, -1.5, -1.5, 0.0, 1.5, 3.0, 3.5)),
        Preset("loudness", "Loudness", doubleArrayOf(6.0, 4.0, 0.0, 0.0, -2.0, 0.0, -1.0, -5.0, 5.0, 1.0)),
        Preset("lounge", "Lounge", doubleArrayOf(-3.0, -1.5, -0.5, 1.5, 4.0, 2.5, 0.0, -1.5, 2.0, 1.0)),
        Preset("piano", "Piano", doubleArrayOf(3.0, 2.0, 0.0, 2.5, 3.0, 1.5, 3.5, 4.5, 3.0, 3.5)),
        Preset("pop", "Pop", doubleArrayOf(-1.5, -1.0, 0.0, 2.0, 4.0, 4.0, 2.0, 0.0, -1.0, -1.5)),
        Preset("rnb", "R&B", doubleArrayOf(2.5, 7.0, 5.5, 1.5, -2.5, -1.5, 2.5, 3.0, 3.0, 3.5)),
        Preset("rock", "Rock", doubleArrayOf(5.0, 4.0, 3.0, 1.5, -0.5, -1.0, 0.5, 2.5, 3.5, 4.5)),
        Preset("small_speakers", "Small Speakers", doubleArrayOf(5.5, 4.0, 3.5, 2.5, 1.0, 0.0, -1.0, -2.0, -3.5, -4.0)),
        Preset("spoken", "Spoken Word", doubleArrayOf(-3.5, -0.5, 0.0, 0.5, 3.5, 4.5, 5.0, 4.0, 2.5, 0.0)),
        Preset("treble_boost", "Treble Booster", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.5, 3.5, 4.5, 5.5)),
        Preset("treble_cut", "Treble Reducer", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -2.5, -3.5, -4.5, -5.5)),
        Preset("vocal", "Vocal Booster", doubleArrayOf(-1.5, -3.0, -3.0, 1.5, 3.5, 3.5, 3.0, 1.5, 0.0, -1.5)),
    )
}

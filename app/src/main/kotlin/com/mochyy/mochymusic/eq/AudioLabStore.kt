package com.mochyy.mochymusic.eq

import android.content.Context
import androidx.core.content.edit
import com.mochyy.mochymusic.eq.data.EQProfileRepository
import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.stage.StageNative
import com.mochyy.mochymusic.eq.stage.StageOutput
import com.mochyy.mochymusic.eq.stage.StageSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** everything audio lab sets (equalizer and audio fx), each change goes to the players live and is saved a moment later, so a slider can be dragged freely */
@Singleton
class AudioLabStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val equalizerService: EqualizerService,
    private val audioFx: AudioFxService,
    private val legacyProfiles: EQProfileRepository,
) {
    private val prefs = context.getSharedPreferences("audio_lab", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _eq = MutableStateFlow(load("eq") ?: EqSettings())
    val eq: StateFlow<EqSettings> = _eq.asStateFlow()

    // the playing song's own preset from its edit sheet, heard instead of the curve above; never saved, the service sets it per song
    private val _songPreset = MutableStateFlow<EqPreset?>(null)
    val songPreset: StateFlow<EqPreset?> = _songPreset.asStateFlow()

    private val _reverb = MutableStateFlow(load("reverb") ?: ReverbSettings())
    val reverb: StateFlow<ReverbSettings> = _reverb.asStateFlow()

    private val _presets = MutableStateFlow(load<List<EqPreset>>("presets") ?: emptyList())
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _fxPresets = MutableStateFlow(load<List<FxPreset>>("fx_presets") ?: emptyList())
    val fxPresets: StateFlow<List<FxPreset>> = _fxPresets.asStateFlow()

    // the fx preset that's picked, every change on the page saves into it; null while none is
    private val _fxPresetId = MutableStateFlow(prefs.getString("fx_preset_id", null)?.takeIf { id -> _fxPresets.value.any { it.id == id } })
    val fxPresetId: StateFlow<String?> = _fxPresetId.asStateFlow()

    private val _loudness = MutableStateFlow(load("loudness") ?: LoudnessSettings())
    val loudness: StateFlow<LoudnessSettings> = _loudness.asStateFlow()

    private val _spatial = MutableStateFlow(load("spatial") ?: SpatialSettings())
    val spatial: StateFlow<SpatialSettings> = _spatial.asStateFlow()

    private val _eightD = MutableStateFlow((load("eight_d") ?: EightDSettings()).normalized())
    val eightD: StateFlow<EightDSettings> = _eightD.asStateFlow()

    /** MusicService applies this to whichever player is playing */
    private val _playback = MutableStateFlow(load("playback") ?: PlaybackSettings())
    val playback: StateFlow<PlaybackSettings> = _playback.asStateFlow()

    private val _volume = MutableStateFlow(load("volume") ?: VolumeSettings())
    val volume: StateFlow<VolumeSettings> = _volume.asStateFlow()

    private val _normalize = MutableStateFlow(load("normalize") ?: NormalizeSettings())
    val normalize: StateFlow<NormalizeSettings> = _normalize.asStateFlow()

    private val _stage = MutableStateFlow(load("stage") ?: StageSettings())
    val stage: StateFlow<StageSettings> = _stage.asStateFlow()

    /** where the music is playing right now, told by the service whenever the route changes */
    private val _stageOutput = MutableStateFlow(StageOutput.PhoneSpeaker)
    val stageOutput: StateFlow<StageOutput> = _stageOutput.asStateFlow()

    @Volatile
    private var hrtfRequested = false

    /** whether anything here changes the sound: offloaded playback skips every effect, so it has to stay off while this is true */
    val shapesSound: Flow<Boolean> = combine(
        combine(eq, reverb, loudness, spatial, songPreset) { eq, reverb, loudness, spatial, songPreset ->
            eq.enabled || songPreset != null || reverb.enabled || loudness.enabled || spatial.enabled || spatial.mono
        },
        stage,
        eightD,
    ) { others, stage, eightD -> others || stage.enabled || eightD.enabled }.distinctUntilChanged()

    init {
        migrateLegacyEqualizer()
        pushEq()
        pushFx()
    }

    fun updateReverb(transform: (ReverbSettings) -> ReverbSettings) {
        _reverb.update(transform)
        pushFx()
        saveIntoFxPreset()
        scheduleSave()
    }

    fun updateLoudness(transform: (LoudnessSettings) -> LoudnessSettings) {
        _loudness.update(transform)
        pushFx()
        saveIntoFxPreset()
        scheduleSave()
    }

    fun updateSpatial(transform: (SpatialSettings) -> SpatialSettings) {
        _spatial.update(transform)
        pushFx()
        saveIntoFxPreset()
        scheduleSave()
    }

    fun updateStage(transform: (StageSettings) -> StageSettings) {
        _stage.update(transform)
        pushFx()
        saveIntoFxPreset()
        scheduleSave()
    }

    fun updateEightD(transform: (EightDSettings) -> EightDSettings) {
        _eightD.update { transform(it).normalized() }
        pushFx()
        saveIntoFxPreset()
        scheduleSave()
    }

    fun setStageOutput(output: StageOutput) {
        if (_stageOutput.value == output) return
        _stageOutput.value = output
        pushFx()
    }

    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) {
        _playback.update { transform(it).let { s -> s.copy(speed = s.speed.coerceIn(MinSpeed, MaxSpeed)) } }
        saveIntoFxPreset()
        scheduleSave()
    }

    fun updateNormalize(transform: (NormalizeSettings) -> NormalizeSettings) {
        _normalize.update(transform)
        pushFx()
        scheduleSave()
    }

    fun updateVolume(transform: (VolumeSettings) -> VolumeSettings) {
        _volume.update(transform)
        pushFx()
        saveIntoFxPreset()
        scheduleSave()
    }

    fun updateEq(transform: (EqSettings) -> EqSettings) {
        _eq.update(transform)
        pushEq()
        scheduleSave()
    }

    // a hand change to the curve: it saves into the picked preset when that one is yours, and a built in one turns custom
    fun editEqCurve(transform: (EqSettings) -> EqSettings) {
        updateEq { s ->
            val next = transform(s)
            if (next == s) s else next.copy(presetId = s.presetId?.takeIf { id -> _presets.value.any { it.id == id } })
        }
        saveIntoEqPreset()
    }

    // the preamp rides along with your own presets too, a built in one stays picked since it never set it
    fun setEqPreamp(db: Double) {
        updateEq { it.copy(preamp = db) }
        saveIntoEqPreset()
    }

    fun savePreset(name: String) {
        val current = _eq.value
        val preset = EqPreset("user_${System.currentTimeMillis()}", name.trim(), current.nodes, current.preamp)
        _presets.update { it + preset }
        updateEq { it.copy(presetId = preset.id) }
    }

    fun renamePreset(id: String, name: String) {
        _presets.update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        scheduleSave()
    }

    // keeps your picked preset in step with the curve, so a tweak never needs saving again
    private fun saveIntoEqPreset() {
        val eq = _eq.value
        val id = eq.presetId ?: return
        _presets.update { list -> list.map { if (it.id == id) it.copy(nodes = eq.nodes, preamp = eq.preamp) else it } }
    }

    // ---- audio fx presets ----------------------------------------------------------------------

    // keeps what's playing now under a new name, and picks it so later changes go into it
    fun saveFxPreset(name: String) {
        val preset = currentFx("fx_${System.currentTimeMillis()}", name.trim())
        _fxPresets.update { it + preset }
        _fxPresetId.value = preset.id
        scheduleSave()
    }

    fun applyFxPreset(id: String) {
        val preset = _fxPresets.value.firstOrNull { it.id == id } ?: return
        _reverb.value = preset.reverb
        _eightD.value = preset.eightD.normalized()
        _loudness.value = preset.loudness
        _spatial.value = preset.spatial
        _playback.value = preset.playback.let { it.copy(speed = it.speed.coerceIn(MinSpeed, MaxSpeed)) }
        _volume.value = preset.volume
        _stage.value = preset.stage.copy(outputKinds = _stage.value.outputKinds)
        _fxPresetId.value = id
        pushFx()
        scheduleSave()
    }

    // lets go of the picked preset: the sound stays, changes just stop going into it
    fun releaseFxPreset() {
        if (_fxPresetId.value == null) return
        _fxPresetId.value = null
        scheduleSave()
    }

    fun renameFxPreset(id: String, name: String) {
        _fxPresets.update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        scheduleSave()
    }

    fun deleteFxPreset(id: String) {
        _fxPresets.update { list -> list.filterNot { it.id == id } }
        if (_fxPresetId.value == id) _fxPresetId.value = null
        scheduleSave()
    }

    // every effect on the audio fx page as it plays right now, under a preset's id and name
    private fun currentFx(id: String, name: String) = FxPreset(
        id = id,
        name = name,
        reverb = _reverb.value,
        eightD = _eightD.value,
        loudness = _loudness.value,
        spatial = _spatial.value,
        playback = _playback.value,
        volume = _volume.value,
        stage = _stage.value.copy(outputKinds = emptyMap()),
    )

    // a change while one of your fx presets is picked goes straight into it, so it never needs saving by hand
    private fun saveIntoFxPreset() {
        val id = _fxPresetId.value ?: return
        _fxPresets.update { list -> list.map { if (it.id == id) currentFx(it.id, it.name) else it } }
    }

    // a song's own preset while it plays, null to go back to the user's curve; the headphone correction stays on either way
    fun useSongPreset(preset: EqPreset?) {
        if (_songPreset.value == preset) return
        _songPreset.value = preset
        pushEq()
    }

    // a built in or saved preset by its id, as the song edit sheet stores it
    fun presetById(id: String): EqPreset? =
        _presets.value.firstOrNull { it.id == id }
            ?: EqPresets.BuiltIn.firstOrNull { it.id == id }?.let { EqPreset(it.id, it.name, it.nodes) }

    fun deletePreset(id: String) {
        _presets.update { list -> list.filterNot { it.id == id } }
        if (_eq.value.presetId == id) updateEq { it.copy(presetId = null) } else scheduleSave()
    }

    /** the db the auto preamp takes off for settings (0 or less), worked out in shared EqDesign */
    fun autoHeadroom(settings: EqSettings = _eq.value): Double = EqDesign.autoHeadroom(settings)

    /** what the processors play for settings; null is a straight wire */
    fun design(settings: EqSettings): ParametricEQ? = EqDesign.design(settings)

    private fun pushEq() {
        val song = _songPreset.value
        val playing = if (song == null) _eq.value else _eq.value.copy(enabled = true, nodes = song.nodes, preamp = song.preamp, presetId = song.id)
        equalizerService.apply(design(playing))
    }

    private fun pushFx() {
        audioFx.reverb = _reverb.value.toParams()
        audioFx.loudness = _loudness.value.toParams()
        audioFx.spatial = _spatial.value.toParams()
        val stage = _stage.value
        audioFx.stage = stage.toParams(speaker = _stageOutput.value.isSpeaker(stage))
        if (stage.enabled) loadHrtf()
        audioFx.eightD = _eightD.value.toParams()
        audioFx.replayGain = _normalize.value.toParams()
        val curve = _volume.value.toCurve()
        if (curve != audioFx.volumeCurve) audioFx.volumeCurve = curve
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            prefs.edit {
                putString("eq", json.encodeToString(EqSettings.serializer(), _eq.value))
                putString("reverb", json.encodeToString(ReverbSettings.serializer(), _reverb.value))
                putString("loudness", json.encodeToString(LoudnessSettings.serializer(), _loudness.value))
                putString("spatial", json.encodeToString(SpatialSettings.serializer(), _spatial.value))
                putString("eight_d", json.encodeToString(EightDSettings.serializer(), _eightD.value))
                putString("playback", json.encodeToString(PlaybackSettings.serializer(), _playback.value))
                putString("volume", json.encodeToString(VolumeSettings.serializer(), _volume.value))
                putString("normalize", json.encodeToString(NormalizeSettings.serializer(), _normalize.value))
                putString("stage", json.encodeToString(StageSettings.serializer(), _stage.value))
                putString(
                    "presets",
                    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EqPreset.serializer()), _presets.value),
                )
                putString(
                    "fx_presets",
                    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FxPreset.serializer()), _fxPresets.value),
                )
                val fxPresetId = _fxPresetId.value
                if (fxPresetId != null) putString("fx_preset_id", fxPresetId) else remove("fx_preset_id")
            }
        }
    }

    // the ear responses 3d sims places sounds with, read once the first time it's switched on
    private fun loadHrtf() {
        if (hrtfRequested) return
        hrtfRequested = true
        // tried once: without the library or the asset the room falls back to plain stereo placement
        scope.launch {
            runCatching {
                StageNative.loaded && StageNative.loadHrtf(context.assets.open("stage_hrtf.bin").use { it.readBytes() })
            }.onFailure { Timber.tag("AudioLab").w(it, "Couldn't load the 3D Sims ear responses") }
        }
    }

    private inline fun <reified T> load(key: String): T? = prefs.getString(key, null)?.let {
        runCatching { json.decodeFromString<T>(it) }
            .onFailure { e -> Timber.tag("AudioLab").w(e, "Couldn't read saved $key") }
            .getOrNull()
    }

    /** brings over the old 10-band screen's curve and any profiles saved with it, once */
    private fun migrateLegacyEqualizer() {
        if (prefs.getBoolean("migrated_legacy_eq", false)) return
        runCatching {
            val legacyActive = legacyProfiles.getActiveProfile()
            if (prefs.getString("eq", null) == null && legacyActive != null && legacyActive.bands.isNotEmpty()) {
                _eq.value = EqSettings(
                    enabled = true,
                    nodes = legacyActive.bands,
                    preamp = legacyActive.preamp,
                    presetId = null,
                )
            }
            val saved = legacyProfiles.getSortedProfiles().filter { it.id != "musicfy_custom_eq" && it.bands.isNotEmpty() }
            if (saved.isNotEmpty()) {
                _presets.update { it + saved.map { p -> EqPreset("legacy_${p.id}", p.name, p.bands, p.preamp) } }
            }
        }.onFailure { Timber.tag("AudioLab").w(it, "Legacy equalizer migration failed") }
        prefs.edit { putBoolean("migrated_legacy_eq", true) }
        scheduleSave()
    }
}

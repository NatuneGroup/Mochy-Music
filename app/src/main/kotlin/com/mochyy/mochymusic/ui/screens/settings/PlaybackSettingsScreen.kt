package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioOffload
import com.mochyy.mochymusic.constants.DontSkipOnFailureKey
import com.mochyy.mochymusic.constants.EfficientDecodingKey
import com.mochyy.mochymusic.constants.InfiniteQueueKey
import com.mochyy.mochymusic.constants.KeepCpuAwakeKey
import com.mochyy.mochymusic.constants.QueueAddInOrderKey
import com.mochyy.mochymusic.constants.QueueEditLockKey
import com.mochyy.mochymusic.constants.QueuePreventDuplicatesKey
import com.mochyy.mochymusic.constants.SponsorBlockCategoriesKey
import com.mochyy.mochymusic.constants.SponsorBlockEnabledKey
import com.mochyy.mochymusic.playback.SponsorBlockDefaultCategories
import com.mochyy.mochymusic.constants.MixWithOtherAppsKey
import com.mochyy.mochymusic.constants.PauseOnMute
import com.mochyy.mochymusic.constants.PauseOnMuteRecoveryPercentKey
import com.mochyy.mochymusic.constants.PersistentQueueKey
import com.mochyy.mochymusic.constants.PersistentShuffleAcrossQueuesKey
import com.mochyy.mochymusic.constants.PreloadNextTrackKey
import com.mochyy.mochymusic.constants.PreloadQueueLyricsKey
import com.mochyy.mochymusic.constants.ResumeOnBluetoothConnectKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.StopPlaybackOnTaskRemovedKey
import com.mochyy.mochymusic.constants.StreamFailureTimeoutKey
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.utils.rememberPreference

const val PlaybackSettingsRoute = "playback_settings"

// settings > playback: how music keeps going, shares the speaker, and what happens when a song won't load
@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_playback_category),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        SettingsSection(stringResource(R.string.settings_section_listening), listeningItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_failures), failureItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_sponsorblock), sponsorBlockItems().matching(query))
        SettingsSection(stringResource(R.string.settings_related), relatedItems(navController).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun listeningItems(): List<SettingsItem> {
    val (stopOnTaskRemoved, onStopOnTaskRemoved) = rememberPreference(StopPlaybackOnTaskRemovedKey, defaultValue = false)
    val (mixWithOtherApps, onMixWithOtherAppsChange) = rememberPreference(MixWithOtherAppsKey, defaultValue = false)
    val (resumeOnBluetooth, onResumeOnBluetoothChange) = rememberPreference(ResumeOnBluetoothConnectKey, defaultValue = false)
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(PauseOnMute, defaultValue = false)
    val (muteRecoveryPercent, onMuteRecoveryPercentChange) = rememberPreference(PauseOnMuteRecoveryPercentKey, defaultValue = 30)
    val (preloadQueueLyrics, onPreloadQueueLyricsChange) = rememberPreference(PreloadQueueLyricsKey, defaultValue = false)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(PersistentQueueKey, defaultValue = true)
    val (persistentShuffle, onPersistentShuffleChange) = rememberPreference(PersistentShuffleAcrossQueuesKey, defaultValue = true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    val (preloadNextTrack, onPreloadNextTrackChange) = rememberPreference(PreloadNextTrackKey, defaultValue = true)
    val (audioOffload, onAudioOffloadChange) = rememberPreference(AudioOffload, defaultValue = false)
    val (efficientDecoding, onEfficientDecodingChange) = rememberPreference(EfficientDecodingKey, defaultValue = true)
    val (infiniteQueue, onInfiniteQueueChange) = rememberPreference(InfiniteQueueKey, defaultValue = true)
    val (keepCpuAwake, onKeepCpuAwakeChange) = rememberPreference(KeepCpuAwakeKey, defaultValue = false)
    val (preventDuplicates, onPreventDuplicatesChange) = rememberPreference(QueuePreventDuplicatesKey, defaultValue = false)
    val (addInOrder, onAddInOrderChange) = rememberPreference(QueueAddInOrderKey, defaultValue = true)
    val (queueLocked, onQueueLockedChange) = rememberPreference(QueueEditLockKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "Infinite queue",
            title = stringResource(R.string.playback_infinite_queue),
            description = stringResource(R.string.playback_infinite_queue_desc),
            detail = stringResource(R.string.playback_infinite_queue_detail),
            icon = painterResource(R.drawable.playlist_play),
            checked = infiniteQueue,
            onCheckedChange = onInfiniteQueueChange,
        ),
        switchItem(
            key = "Keep CPU awake during playback",
            title = stringResource(R.string.settings_keep_cpu_awake),
            description = stringResource(R.string.settings_keep_cpu_awake_desc),
            icon = painterResource(R.drawable.sleep_timer),
            checked = keepCpuAwake,
            onCheckedChange = onKeepCpuAwakeChange,
        ),
        switchItem(
            key = "Prevent duplicates in queue",
            title = stringResource(R.string.settings_queue_prevent_duplicates),
            description = stringResource(R.string.settings_queue_prevent_duplicates_desc),
            icon = painterResource(R.drawable.playlist_play),
            checked = preventDuplicates,
            onCheckedChange = onPreventDuplicatesChange,
        ),
        switchItem(
            key = "Add to queue in order",
            title = stringResource(R.string.settings_queue_add_in_order),
            description = stringResource(R.string.settings_queue_add_in_order_desc),
            icon = painterResource(R.drawable.playlist_play),
            checked = addInOrder,
            onCheckedChange = onAddInOrderChange,
        ),
        switchItem(
            key = "Lock queue",
            title = stringResource(R.string.settings_queue_lock),
            description = stringResource(R.string.settings_queue_lock_desc),
            icon = painterResource(R.drawable.lock),
            checked = queueLocked,
            onCheckedChange = onQueueLockedChange,
        ),
        switchItem(
            key = "Keep playing even when closed",
            title = stringResource(R.string.playback_keep_playing),
            description = stringResource(R.string.playback_keep_playing_desc),
            detail = stringResource(R.string.playback_keep_playing_detail),
            icon = painterResource(R.drawable.clear_all),
            checked = !stopOnTaskRemoved,
            onCheckedChange = { onStopOnTaskRemoved(!it) },
        ),
        switchItem(
            key = "Play alongside other apps",
            title = stringResource(R.string.playback_mix_other_apps),
            description = stringResource(R.string.playback_mix_other_apps_desc),
            detail = stringResource(R.string.playback_mix_other_apps_detail),
            icon = painterResource(R.drawable.volume_up),
            checked = mixWithOtherApps,
            onCheckedChange = onMixWithOtherAppsChange,
        ),
        switchItem(
            key = "Resume on Bluetooth connection",
            title = stringResource(R.string.playback_resume_bluetooth),
            description = stringResource(R.string.playback_resume_bluetooth_desc),
            icon = painterResource(R.drawable.bluetooth),
            checked = resumeOnBluetooth,
            onCheckedChange = onResumeOnBluetoothChange,
        ),
        switchItem(
            key = "Pause when muted",
            title = stringResource(R.string.pause_music_when_media_is_muted),
            description = stringResource(R.string.pause_music_when_muted_desc),
            icon = painterResource(R.drawable.volume_off),
            checked = pauseOnMute,
            onCheckedChange = onPauseOnMuteChange,
        ),
        SettingsItem(
            title = { ValueTitle(stringResource(R.string.settings_mute_recovery_volume), if (muteRecoveryPercent == 0) stringResource(R.string.mute_recovery_off) else "$muteRecoveryPercent%") },
            highlightKey = "Mute recovery volume",
            searchText = stringResource(R.string.settings_mute_recovery_volume),
            description = {
                DefaultPlayerSeekBarSlider(
                    value = muteRecoveryPercent.toFloat(),
                    onValueChange = { onMuteRecoveryPercentChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.volume_up),
            iconShape = CircleShape,
            isVisible = pauseOnMute,
            isSubOption = true,
        ),
        switchItem(
            key = "Save Player's last state",
            title = stringResource(R.string.playback_save_state),
            description = stringResource(R.string.playback_save_state_desc),
            detail = stringResource(R.string.playback_save_state_detail),
            icon = painterResource(R.drawable.cached),
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange,
        ),
        // shuffle stays on from one playlist to the next, like spotify, until it's turned off
        switchItem(
            key = "Keep shuffle on",
            title = stringResource(R.string.settings_persistent_shuffle),
            description = stringResource(R.string.settings_persistent_shuffle_desc),
            icon = painterResource(R.drawable.shuffle),
            checked = persistentShuffle,
            onCheckedChange = onPersistentShuffleChange,
        ),
        switchItem(
            key = "Preload next song",
            title = stringResource(R.string.playback_preload_next),
            description = stringResource(R.string.playback_preload_next_desc),
            icon = painterResource(R.drawable.download),
            checked = preloadNextTrack,
            onCheckedChange = onPreloadNextTrackChange,
        ),
        switchItem(
            key = "Preload queue lyrics",
            title = stringResource(R.string.playback_preload_lyrics),
            description = stringResource(R.string.playback_preload_lyrics_desc),
            icon = painterResource(R.drawable.lyrics),
            checked = preloadQueueLyrics,
            onCheckedChange = onPreloadQueueLyricsChange,
        ),
        switchItem(
            key = "Audio offload",
            title = stringResource(R.string.playback_audio_offload),
            description = stringResource(R.string.playback_audio_offload_desc),
            icon = painterResource(R.drawable.speed),
            checked = audioOffload,
            onCheckedChange = onAudioOffloadChange,
        ),
        switchItem(
            key = "Efficient decoding",
            title = stringResource(R.string.playback_efficient_decoding),
            description = stringResource(R.string.playback_efficient_decoding_desc),
            icon = painterResource(R.drawable.graphic_eq),
            checked = efficientDecoding,
            onCheckedChange = onEfficientDecodingChange,
        ),
        switchItem(
            key = "Skip Silence",
            title = stringResource(R.string.playback_skip_silence),
            description = stringResource(R.string.playback_skip_silence_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange,
        ),
    )
}

@Composable
private fun sponsorBlockItems(): List<SettingsItem> {
    val (enabled, onEnabledChange) = rememberPreference(SponsorBlockEnabledKey, defaultValue = false)
    val (categories, onCategoriesChange) = rememberPreference(SponsorBlockCategoriesKey, defaultValue = SponsorBlockDefaultCategories)
    fun setCategory(name: String, on: Boolean) = onCategoriesChange(if (on) categories + name else categories - name)
    return listOf(
        switchItem(
            key = "SponsorBlock",
            title = stringResource(R.string.settings_sponsorblock),
            description = stringResource(R.string.settings_sponsorblock_desc),
            detail = stringResource(R.string.settings_sponsorblock_detail),
            icon = painterResource(R.drawable.skip_next),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        ),
        switchItem(
            key = "SponsorBlock non-music sections",
            title = stringResource(R.string.settings_sponsorblock_offtopic),
            description = stringResource(R.string.settings_sponsorblock_offtopic_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = "music_offtopic" in categories,
            onCheckedChange = { setCategory("music_offtopic", it) },
            subOptionOf = enabled,
            visible = enabled,
        ),
        switchItem(
            key = "SponsorBlock sponsor reads",
            title = stringResource(R.string.settings_sponsorblock_sponsor),
            description = stringResource(R.string.settings_sponsorblock_sponsor_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = "sponsor" in categories,
            onCheckedChange = { setCategory("sponsor", it) },
            subOptionOf = enabled,
            visible = enabled,
        ),
        switchItem(
            key = "SponsorBlock self-promotion",
            title = stringResource(R.string.settings_sponsorblock_selfpromo),
            description = stringResource(R.string.settings_sponsorblock_selfpromo_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = "selfpromo" in categories,
            onCheckedChange = { setCategory("selfpromo", it) },
            subOptionOf = enabled,
            visible = enabled,
        ),
        switchItem(
            key = "SponsorBlock intros",
            title = stringResource(R.string.settings_sponsorblock_intro),
            description = stringResource(R.string.settings_sponsorblock_intro_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = "intro" in categories,
            onCheckedChange = { setCategory("intro", it) },
            subOptionOf = enabled,
            visible = enabled,
        ),
        switchItem(
            key = "SponsorBlock outros",
            title = stringResource(R.string.settings_sponsorblock_outro),
            description = stringResource(R.string.settings_sponsorblock_outro_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = "outro" in categories,
            onCheckedChange = { setCategory("outro", it) },
            subOptionOf = enabled,
            visible = enabled,
        ),
    )
}

@Composable
private fun failureItems(): List<SettingsItem> {
    val (dontSkipOnFailure, onDontSkipOnFailureChange) = rememberPreference(DontSkipOnFailureKey, defaultValue = false)
    val (failureTimeout, onFailureTimeoutChange) = rememberPreference(StreamFailureTimeoutKey, defaultValue = 6)
    return listOf(
        switchItem(
            key = "Don't skip on failure",
            title = stringResource(R.string.settings_dont_skip_failure),
            description = stringResource(R.string.settings_dont_skip_failure_desc),
            detail = stringResource(R.string.settings_dont_skip_failure_detail),
            icon = painterResource(R.drawable.skip_next),
            checked = dontSkipOnFailure,
            onCheckedChange = onDontSkipOnFailureChange,
        ),
        SettingsItem(
            title = { ValueTitle(stringResource(R.string.settings_skip_timeout), stringResource(R.string.settings_seconds_short, failureTimeout)) },
            highlightKey = "Skip timeout failure",
            searchText = stringResource(R.string.settings_skip_timeout),
            description = {
                DefaultPlayerSeekBarSlider(
                    value = failureTimeout.toFloat(),
                    onValueChange = { onFailureTimeoutChange(it.toInt()) },
                    valueRange = 5f..20f,
                    steps = 14,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.speed),
            iconShape = CircleShape,
            isVisible = !dontSkipOnFailure,
            isSubOption = true,
        ),
    )
}

// the sound side of playback lives in audio lab, these take people there
@Composable
private fun relatedItems(navController: NavController): List<SettingsItem> = listOf(
    linkItem(
        key = "Transition crossfade",
        title = stringResource(R.string.transition_title),
        description = stringResource(R.string.settings_transition_link_desc),
        icon = painterResource(R.drawable.linear_scale),
        onClick = { navController.navigate("crossmix_settings") },
    ),
    linkItem(
        key = "Audio Pass output usb dac",
        title = stringResource(R.string.audiopass_hub_title),
        description = stringResource(R.string.settings_audio_pass_desc),
        icon = rememberVectorPainter(Icons.Rounded.GraphicEq),
        onClick = { navController.navigate("audio_pass") },
    ),
)

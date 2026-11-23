// IosRoutes.kt
// Every destination the iOS host registers. The settings routes are the literal strings the shared
// settings list and its pages navigate to, imported from the shared constants where a page exports one
// and spelled here where only the Android graph has them; a platform that can't run a page still
// registers its route, so no shared row can crash navigation.

package com.mochyy.mochymusic.shared.ios.ui

import com.mochyy.mochymusic.ui.screens.settings.AiTranslatorSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.InternetSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.NowPlayingSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.PlaybackSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.ProxySettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.AccountImportRoutePattern
import com.mochyy.mochymusic.ui.screens.settings.importsync.AccountSwitcherRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProgressRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProvidersRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportTuneMyMusicRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeLoginRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.ListenBrainzRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.ScrobbleServiceRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.backup.BackupRoute
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageRoute

internal object IosRoutes {
    // the settings tab's own pages
    const val Settings = "settings"
    const val Appearance = "appearance_settings"
    const val AppBehaviour = "general_settings"
    const val Experimental = "experimental_settings"
    const val Stats = "stats"
    const val Storage = StorageRoute
    val Playback = PlaybackSettingsRoute
    val NowPlaying = NowPlayingSettingsRoute
    val Internet = InternetSettingsRoute
    val AiTranslator = AiTranslatorSettingsRoute
    val Proxy = ProxySettingsRoute

    // rows the shared list and pages open; iOS says where those pages live until their work moves
    const val Customisation = "customisation_settings"
    const val NavBar = "nav_bar_settings"
    const val Cipher = "cipher_settings"
    const val PlaybackDiagnostics = "playback_diagnostics"
    const val AudioLab = "audio_lab"
    const val AudioFx = "audio_fx"
    const val AudioStage = "audio_stage"
    const val AudioQuality = "audio_quality"
    const val AudioPass = "audio_pass"
    const val Crossmix = "crossmix_settings"
    const val Equalizer = "equalizer"
    const val Gesture = "gesture_settings"
    const val Modules = "modules"
    const val ModuleMonochrome = "module/monochrome"
    const val ModuleAppleMusic = "module/applemusic"
    const val ModuleStore = "module_store/{post}"
    const val DeviceIntegration = "device_integration"
    const val AndroidAuto = "integrations_settings/android_auto"
    const val Discord = "integrations_settings/discord"
    const val SpotifyIntegration = "integrations_settings/spotify"
    const val LastFm = "integrations_settings/scrobble/LASTFM"
    const val LibreFm = "integrations_settings/scrobble/LIBREFM"
    val Integrations = IntegrationsRoute
    val ListenBrainz = ListenBrainzRoute
    val ScrobbleService = ScrobbleServiceRoute
    val Backup = BackupRoute
    val ImportSync = ImportSyncRoute
    val ImportProviders = ImportProvidersRoute
    val ImportTuneMyMusic = ImportTuneMyMusicRoute
    val ImportProgress = ImportProgressRoute
    val YouTubeSync = YouTubeSyncRoute
    val YouTubeLogin = YouTubeLoginRoute
    val AccountSwitcher = AccountSwitcherRoute
    val AccountImport = AccountImportRoutePattern
    val DevPlayground = DevPlaygroundRoute
    const val DeveloperSpace = "developer_space?person={person}"
}

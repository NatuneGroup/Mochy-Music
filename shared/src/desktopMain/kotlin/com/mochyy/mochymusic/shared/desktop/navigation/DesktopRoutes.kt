// DesktopRoutes.kt
// Every destination the desktop host can reach. Shared screens navigate with the same route strings the
// Android graph uses, so this file is the desktop side of that contract: one place that names them and
// says which of them belong to the settings tab.

package com.mochyy.mochymusic.shared.desktop.navigation

import com.mochyy.mochymusic.ui.screens.settings.AiTranslatorSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.InternetSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.NowPlayingSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.PlaybackSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.ProxySettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.ListenBrainzRoute
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProgressRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportProvidersRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportTuneMyMusicRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute

/** The desktop shell's own roots, and the shared pages that have no exported route constant yet. */
object DesktopRoutes {
    const val Home = "desktop_home"
    const val Search = "desktop_search"
    const val Library = "desktop_library"
    const val NowPlaying = "desktop_now_playing"
    const val Settings = "settings"
    const val Stats = "stats"
    const val DesktopWindow = "desktop_window_settings"
    const val About = "desktop_about"

    // shared settings pages with an exported route constant
    const val Playback = PlaybackSettingsRoute
    const val NowPlayingSettings = NowPlayingSettingsRoute
    const val Internet = InternetSettingsRoute
    const val Proxy = ProxySettingsRoute
    const val AiTranslator = AiTranslatorSettingsRoute
    const val Storage = StorageRoute
    const val Integrations = IntegrationsRoute
    const val ListenBrainz = ListenBrainzRoute
    const val ImportSync = ImportSyncRoute
    const val ImportProviders = ImportProvidersRoute
    const val ImportTuneMyMusic = ImportTuneMyMusicRoute
    const val ImportProgress = ImportProgressRoute
    const val YouTubeSync = YouTubeSyncRoute
    const val DeveloperSpace = "developer_space"
    const val DevPlayground = DevPlaygroundRoute

    // shared pages whose route is still a literal in the shared screen
    const val Appearance = "appearance_settings"
    const val AppBehaviour = "general_settings"
    const val NavBar = "nav_bar_settings"
    const val Experimental = "experimental_settings"

    // rows a module store adds under the modules row; the store doesn't run here, but the list still
    // names them, and an unknown route must never fail navigation
    const val ModuleAppleMusic = "module/applemusic"
    const val ModuleMonochrome = "module/monochrome"
    const val YouTubeLogin = "youtube_login"

    /**
     * A route the desktop graph doesn't know opens this page with the route as its title, instead of
     * throwing as an unregistered destination would.
     */
    const val Unavailable = "desktop_unavailable"

    /** the pattern the graph registers for [Unavailable], with its route argument */
    const val UnavailablePattern = "$Unavailable?route={route}"

    fun unavailable(route: String): String =
        "$Unavailable?route=${java.net.URLEncoder.encode(route, "UTF-8")}"

    // Android-only pages the shared settings rows still link to. Desktop registers a page that says so
    // instead of letting the navigation call fail.
    const val Customisation = "customisation_settings"
    const val Cipher = "cipher_settings"
    const val PlaybackDiagnostics = "playback_diagnostics"
    const val AudioLab = "audio_lab"
    const val AudioFx = "audio_fx"
    const val AudioStage = "audio_stage"
    const val AudioQuality = "audio_quality"
    const val AudioPass = "audio_pass"
    const val Crossmix = "crossmix_settings"
    const val Equalizer = "equalizer"
    const val Modules = "modules"
    const val ModuleBrowser = "module_browser"
    const val SpotifyIntegration = "integrations_settings/spotify"
    const val DeviceIntegration = "device_integration"
    const val Backup = "integrations_settings/backup"
    const val ScrobbleService = "integrations_settings/scrobble/{site}"
    const val AndroidAutoIntegration = "integrations_settings/android_auto"
    const val DiscordIntegration = "integrations_settings/discord"
    const val AccountSwitcher = "youtube_accounts"

    /** Routes the Settings tab owns, so the rail keeps Settings lit while one of them is open. */
    val settingsRoutes: Set<String> = setOf(
        Settings, Stats, DesktopWindow, About, Appearance, AppBehaviour, Playback, NowPlayingSettings, Internet, Proxy,
        AiTranslator, NavBar, Experimental, Storage, Integrations, ListenBrainz, ImportSync,
        ImportProviders, ImportTuneMyMusic, ImportProgress, YouTubeSync, DeveloperSpace, DevPlayground,
        Customisation, Cipher, PlaybackDiagnostics, AudioLab, AudioFx, AudioStage, AudioQuality,
        AudioPass, Crossmix, Equalizer, Modules, ModuleBrowser, SpotifyIntegration, DeviceIntegration,
        Backup, AndroidAutoIntegration, DiscordIntegration, AccountSwitcher, ScrobbleService,
        ModuleAppleMusic, ModuleMonochrome, YouTubeLogin, UnavailablePattern,
    )
}

// IosSettingsTab.kt
// The iOS Settings tab: the shared settings list and every shared page iOS can run, in one navigation
// graph of its own (the tab remembers its stack while the other tabs are open, because MochyShell
// keeps each tab's content alive with movable content).
//
// The pages built on common code are the real shared screens; the routes whose work is still in :app
// (Audio Lab, the import engine, the scrobblers, cloud backup, the cipher cache, modules, devices,
// the AI translator and the proxy) are registered as pages that say where the screen lives, so a row
// from the shared list can never fail navigation or land on controls that do nothing.

package com.mochyy.mochymusic.shared.ios.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mochyy.mochymusic.shared.ios.rememberIosStorageHost
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.audio_quality
import com.mochyy.mochymusic.shared.resources.audiopass_hub_title
import com.mochyy.mochymusic.shared.resources.car_settings_title
import com.mochyy.mochymusic.shared.resources.cipher_settings_title
import com.mochyy.mochymusic.shared.resources.customise_gesture
import com.mochyy.mochymusic.shared.resources.dev_spaces_back_label
import com.mochyy.mochymusic.shared.resources.person_dev
import com.mochyy.mochymusic.shared.resources.shell_credit
import com.mochyy.mochymusic.shared.resources.devices_title
import com.mochyy.mochymusic.shared.resources.equalizer
import com.mochyy.mochymusic.shared.resources.home
import com.mochyy.mochymusic.shared.resources.import_sync_title
import com.mochyy.mochymusic.shared.resources.integrations
import com.mochyy.mochymusic.shared.resources.playback_diagnostics_title
import com.mochyy.mochymusic.shared.resources.proxy
import com.mochyy.mochymusic.shared.resources.search
import com.mochyy.mochymusic.shared.resources.settings_ai_translator
import com.mochyy.mochymusic.shared.resources.settings_audio_lab_category
import com.mochyy.mochymusic.shared.resources.settings_customisation_category
import com.mochyy.mochymusic.shared.resources.settings_modules_category
import com.mochyy.mochymusic.shared.resources.settings_nav_bar
import com.mochyy.mochymusic.shared.resources.shell_coming_body
import com.mochyy.mochymusic.shared.resources.shell_coming_title
import com.mochyy.mochymusic.shared.resources.transition_title
import com.mochyy.mochymusic.shared.resources.your_library
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.recap.StatsScreen
import com.mochyy.mochymusic.ui.screens.settings.AppBehaviourSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.AppearanceSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.ExperimentalSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.InternetSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.MochySettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.NowPlayingSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.PlaybackSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.SettingsListPlatform
import com.mochyy.mochymusic.ui.screens.settings.StartTabChoice
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundScreen
import com.mochyy.mochymusic.ui.screens.settings.devspace.DeveloperSpaceScreen
import com.mochyy.mochymusic.ui.screens.settings.linkItem
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IosSettingsTab() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = IosRoutes.Settings,
        modifier = Modifier.fillMaxSize(),
    ) {
        iosSettingsGraph(navController)
    }
}

/** Every settings destination; shared by the tab above so the graph is built in one place. */
private fun NavGraphBuilder.iosSettingsGraph(navController: NavHostController) {
    composable(IosRoutes.Settings) {
        MochySettingsScreen(
            navController = navController,
            // none of the Android-only bubbles: audio lab, customisation, the module store and the
            // device sheet each live in :app, so the list leaves their rows out instead of opening
            // pages that can't work here
            platform = SettingsListPlatform(),
            // the avatar opens the recap, the one shared "you" page iOS has
            onProfileClick = { navController.navigate(IosRoutes.Stats) },
            // the list's tips slot carries the one shared "about" page iOS can run: Mochy's developers,
            // their numbers and the playground
            recommendations = { open ->
                SettingsGroup(
                    style = SettingsGroupStyle.Grouped,
                    items = listOf(
                        linkItem(
                            key = "Dev Spaces",
                            title = stringResource(Res.string.dev_spaces_back_label),
                            description = stringResource(Res.string.shell_credit),
                            icon = painterResource(Res.drawable.person_dev),
                            onClick = { open(IosRoutes.DeveloperSpace) },
                        ),
                    ),
                )
            },
        )
    }

    // the pages that run on shared code alone
    composable(IosRoutes.Appearance) {
        AppearanceSettingsScreen(
            navController = navController,
            startTabs = listOf(
                StartTabChoice("HOME", stringResource(Res.string.home)),
                StartTabChoice("SEARCH", stringResource(Res.string.search)),
                StartTabChoice("LIBRARY", stringResource(Res.string.your_library)),
            ),
            // the font picker and the theme editor are the app's; iOS keeps the system font
            customFonts = null,
            customThemeRows = emptyList(),
        )
    }
    composable(IosRoutes.AppBehaviour) {
        AppBehaviourSettingsScreen(navController = navController)
    }
    composable(IosRoutes.Playback) {
        PlaybackSettingsScreen(navController = navController)
    }
    composable(IosRoutes.NowPlaying) {
        // the video size sheet, the canvas order picker and the floating lyrics switch are Android
        // widgets; leaving them out hides those rows instead of opening a control that can't work
        NowPlayingSettingsScreen(navController = navController)
    }
    composable(IosRoutes.Internet) {
        InternetSettingsScreen(navController = navController)
    }
    composable(IosRoutes.Experimental) {
        ExperimentalSettingsScreen(navController = navController)
    }
    composable(IosRoutes.Storage) {
        StorageScreen(navController = navController, host = rememberIosStorageHost())
    }
    composable(IosRoutes.Stats) {
        StatsScreen(navController = navController)
    }
    composable(
        route = IosRoutes.DeveloperSpace,
        arguments = listOf(navArgument("person") { type = NavType.IntType; defaultValue = 0 }),
    ) {
        // the donate row is the app's sheet; iOS leaves it out and keeps the rest of the page.
        // The route keeps its person argument so links from other screens resolve; iOS opens the
        // first page, as it has no profile card to swipe from yet.
        DeveloperSpaceScreen(navController = navController, onDonate = null)
    }
    composable(IosRoutes.DevPlayground) {
        // the playground's own row into experimental settings is an app page; it is left out
        DevPlaygroundScreen(navController = navController, experimentalSettingsEntry = {})
    }

    // pages whose work is still :app's: the shared rows open them, so iOS keeps navigation working
    // and says where the page lives today
    unavailable(navController, IosRoutes.Customisation, Res.string.settings_customisation_category)
    unavailable(navController, IosRoutes.NavBar, Res.string.settings_nav_bar)
    unavailable(navController, IosRoutes.AiTranslator, Res.string.settings_ai_translator)
    unavailable(navController, IosRoutes.Proxy, Res.string.proxy)
    unavailable(navController, IosRoutes.Cipher, Res.string.cipher_settings_title)
    unavailable(navController, IosRoutes.PlaybackDiagnostics, Res.string.playback_diagnostics_title)
    unavailable(navController, IosRoutes.AudioLab, Res.string.settings_audio_lab_category)
    unavailable(navController, IosRoutes.AudioFx, Res.string.settings_audio_lab_category)
    unavailable(navController, IosRoutes.AudioStage, Res.string.settings_audio_lab_category)
    unavailable(navController, IosRoutes.AudioQuality, Res.string.audio_quality)
    unavailable(navController, IosRoutes.AudioPass, Res.string.audiopass_hub_title)
    unavailable(navController, IosRoutes.Crossmix, Res.string.transition_title)
    unavailable(navController, IosRoutes.Equalizer, Res.string.equalizer)
    unavailable(navController, IosRoutes.Gesture, Res.string.customise_gesture)
    unavailable(navController, IosRoutes.Modules, Res.string.settings_modules_category)
    unavailable(navController, IosRoutes.ModuleMonochrome, Res.string.settings_modules_category)
    unavailable(navController, IosRoutes.ModuleAppleMusic, Res.string.settings_modules_category)
    unavailable(navController, IosRoutes.SpotifyIntegration, Res.string.settings_modules_category)
    unavailable(navController, IosRoutes.DeviceIntegration, Res.string.devices_title)
    unavailable(navController, IosRoutes.AndroidAuto, Res.string.car_settings_title)
    unavailable(navController, IosRoutes.Discord, Res.string.integrations)
    unavailable(navController, IosRoutes.LastFm, Res.string.integrations)
    unavailable(navController, IosRoutes.LibreFm, Res.string.integrations)
    unavailable(navController, IosRoutes.Integrations, Res.string.integrations)
    unavailable(navController, IosRoutes.ListenBrainz, Res.string.integrations)
    unavailable(navController, IosRoutes.Backup, Res.string.integrations)
    unavailable(navController, IosRoutes.ImportSync, Res.string.import_sync_title)
    unavailable(navController, IosRoutes.ImportProviders, Res.string.import_sync_title)
    unavailable(navController, IosRoutes.ImportTuneMyMusic, Res.string.import_sync_title)
    unavailable(navController, IosRoutes.ImportProgress, Res.string.import_sync_title)
    unavailable(navController, IosRoutes.YouTubeSync, Res.string.import_sync_title)
    unavailable(navController, IosRoutes.YouTubeLogin, Res.string.import_sync_title)
    unavailable(navController, IosRoutes.AccountSwitcher, Res.string.import_sync_title)
    // the rows with arguments that the shared list or a page can open
    composable(
        route = IosRoutes.ModuleStore,
        arguments = listOf(navArgument("post") { type = NavType.StringType }),
    ) {
        IosUnavailablePage(navController, stringResource(Res.string.settings_modules_category))
    }
    composable(
        route = IosRoutes.ScrobbleService,
        arguments = listOf(navArgument("site") { type = NavType.StringType }),
    ) {
        IosUnavailablePage(navController, stringResource(Res.string.integrations))
    }
    composable(
        route = IosRoutes.AccountImport,
        arguments = listOf(
            navArgument("service") { type = NavType.StringType },
            navArgument("resync") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("then") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        IosUnavailablePage(navController, stringResource(Res.string.import_sync_title))
    }
}

private fun NavGraphBuilder.unavailable(
    navController: NavHostController,
    route: String,
    title: StringResource,
) {
    composable(route) {
        IosUnavailablePage(navController, stringResource(title))
    }
}

/** A page the Android app still owns: says so in the shared words the shell uses, and goes back. */
@Composable
private fun IosUnavailablePage(navController: NavController, title: String) {
    SubSettingsScaffold(title = title, onBack = { navController.navigateUp() }) {
        Text(stringResource(Res.string.shell_coming_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.shell_coming_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

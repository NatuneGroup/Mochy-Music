// DesktopNavHost.kt
// The desktop navigation graph: the shell's four roots, the desktop player page, and every shared
// settings page the desktop can run. Rows that open an Android-only page land on a page that says so,
// so no shared link can fail navigation here.

package com.mochyy.mochymusic.shared.desktop.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.rememberCoroutineScope
import com.mochyy.mochymusic.constants.CustomBoldFontKey
import com.mochyy.mochymusic.constants.CustomFontKey
import com.mochyy.mochymusic.constants.NavTabsKey
import com.mochyy.mochymusic.shared.desktop.about.DesktopAboutPage
import com.mochyy.mochymusic.shared.desktop.storage.DesktopStoragePage
import com.mochyy.mochymusic.shared.desktop.fonts.DesktopFonts
import com.mochyy.mochymusic.shared.desktop.online.CollectionKind
import com.mochyy.mochymusic.shared.desktop.online.DesktopBrowsePage
import com.mochyy.mochymusic.shared.desktop.online.DesktopChartsPage
import com.mochyy.mochymusic.shared.desktop.online.DesktopCollectionPage
import com.mochyy.mochymusic.shared.desktop.online.DesktopExplorePage
import com.mochyy.mochymusic.shared.desktop.window.DesktopWindowSettingsPage
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.lyrics.translationTargetIds
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.audio_quality
import com.mochyy.mochymusic.shared.resources.audiopass_hub_title
import com.mochyy.mochymusic.shared.resources.car_settings_title
import com.mochyy.mochymusic.shared.resources.cipher_settings_title
import com.mochyy.mochymusic.shared.resources.devices_title
import com.mochyy.mochymusic.shared.resources.discord_integration_settings
import com.mochyy.mochymusic.shared.resources.equalizer
import com.mochyy.mochymusic.shared.resources.experimental_settings_title
import com.mochyy.mochymusic.shared.resources.home
import com.mochyy.mochymusic.shared.resources.import_sync_btn_ok
import com.mochyy.mochymusic.shared.resources.import_sync_title
import com.mochyy.mochymusic.shared.resources.nav_home
import com.mochyy.mochymusic.shared.resources.nav_lib
import com.mochyy.mochymusic.shared.resources.nav_search
import com.mochyy.mochymusic.shared.resources.nav_settings
import com.mochyy.mochymusic.shared.resources.integrations
import com.mochyy.mochymusic.shared.resources.playback_diagnostics_title
import com.mochyy.mochymusic.shared.resources.search
import com.mochyy.mochymusic.shared.resources.settings
import com.mochyy.mochymusic.shared.resources.settings_audio_lab_category
import com.mochyy.mochymusic.shared.resources.settings_customisation_category
import com.mochyy.mochymusic.shared.resources.settings_modules_category
import com.mochyy.mochymusic.shared.resources.transition_title
import com.mochyy.mochymusic.shared.resources.your_library
import com.mochyy.mochymusic.shared.ui.search.SearchScreen
import com.mochyy.mochymusic.shared.ui.search.SearchState
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.screens.recap.StatsScreen
import com.mochyy.mochymusic.ui.screens.settings.AiTranslatorSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.AppBehaviourSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.AppearanceSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.CustomFontActions
import com.mochyy.mochymusic.ui.screens.settings.ExperimentalSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundScreen
import com.mochyy.mochymusic.ui.screens.settings.devspace.DeveloperSpaceScreen
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsScreen
import com.mochyy.mochymusic.ui.screens.settings.integrations.ListenBrainzScreen
import com.mochyy.mochymusic.ui.screens.settings.InternetSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.NavBarSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.NavTab
import com.mochyy.mochymusic.ui.screens.settings.NowPlayingSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.PlaybackSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.ProxySettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.StartTabChoice
import com.mochyy.mochymusic.utils.rememberPreference
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopNavHost(
    navController: NavHostController,
    startRoute: String,
    searchState: SearchState,
    library: LocalLibrary,
    queue: SharedQueue,
    providers: List<LyricsProvider>,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
    ) {
        composable(DesktopRoutes.Home) { DesktopHomePage(library, queue) }
        composable(DesktopRoutes.Search) {
            SearchScreen(state = searchState, onPlay = queue::play, onQueue = queue::add)
        }
        composable(DesktopRoutes.Library) { DesktopLibraryPage(library, queue) }
        composable(DesktopRoutes.Settings) { DesktopSettingsHome(navController) }
        composable(DesktopRoutes.Stats) { StatsScreen(navController) }
        composable(DesktopRoutes.DesktopWindow) { DesktopWindowSettingsPage(navController) }
        composable(DesktopRoutes.About) { DesktopAboutPage(navController) }
        composable(DesktopRoutes.Storage) { DesktopStoragePage(navController) }
        composable(DesktopRoutes.Integrations) {
            // no scrobble services, discord or cloud backup here yet: the page passes no routes and
            // those rows stay out, while ListenBrainz (a plain token setting) is a real page below
            IntegrationsScreen(navController = navController, scrobbleSites = emptyList())
        }
        composable(DesktopRoutes.ListenBrainz) { ListenBrainzScreen(navController = navController) }
        composable(DesktopRoutes.NowPlaying) { DesktopNowPlayingPage(navController, queue, providers) }
        // the online pages: YouTube Music's explore, its browse sections, and one album or playlist
        composable(DesktopRoutes.Explore) { DesktopExplorePage(navController, queue) }
        composable(DesktopRoutes.Charts) { DesktopChartsPage(navController, queue) }
        composable(
            route = DesktopRoutes.Browse,
            arguments = listOf(
                navArgument("browseId") { type = NavType.StringType },
                navArgument("params") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            DesktopBrowsePage(
                navController = navController,
                browseId = entry.arguments?.getString("browseId").orEmpty(),
                params = entry.arguments?.getString("params").orEmpty().ifBlank { null },
                queue = queue,
            )
        }
        composable(
            route = DesktopRoutes.Collection,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entry ->
            DesktopCollectionPage(
                navController = navController,
                kind = CollectionKind.fromRoute(entry.arguments?.getString("kind").orEmpty()),
                id = entry.arguments?.getString("id").orEmpty(),
                queue = queue,
            )
        }
        desktopSettingsGraph(navController)
    }
}

/**
 * Every settings destination a shared row can navigate to. The pages built on common code are the
 * real shared screens; the ones whose work is still Android's say so and go back.
 */
private fun NavGraphBuilder.desktopSettingsGraph(navController: NavHostController) {
    composable(DesktopRoutes.Appearance) {
        val startTabs = listOf(
            StartTabChoice("HOME", stringResource(Res.string.home)),
            StartTabChoice("SEARCH", stringResource(Res.string.search)),
            StartTabChoice("LIBRARY", stringResource(Res.string.your_library)),
        )
        // the custom font rows: the desktop file dialog picks a ttf/otf, which is copied into Mochy's
        // font folder; the window redraws in it as soon as the preference changes
        val (customFont, setCustomFont) = rememberPreference(CustomFontKey, defaultValue = "")
        val (customBoldFont, setCustomBoldFont) = rememberPreference(CustomBoldFontKey, defaultValue = "")
        val fontScope = rememberCoroutineScope()
        val customFonts = remember(customFont, customBoldFont) {
            fun pick(bold: Boolean) = fontScope.launch {
                DesktopFonts.pickAndImport()?.let { name ->
                    if (bold) {
                        if (customBoldFont.isNotEmpty() && customBoldFont != customFont) DesktopFonts.remove(customBoldFont)
                        setCustomBoldFont(name)
                    } else {
                        if (customFont.isNotEmpty() && customFont != customBoldFont) DesktopFonts.remove(customFont)
                        setCustomFont(name)
                    }
                }
            }
            CustomFontActions(
                pickRegular = { pick(bold = false) },
                pickBold = { pick(bold = true) },
                removeRegular = {
                    if (customFont.isNotEmpty() && customFont != customBoldFont) DesktopFonts.remove(customFont)
                    setCustomFont("")
                },
                removeBold = {
                    if (customBoldFont.isNotEmpty() && customBoldFont != customFont) DesktopFonts.remove(customBoldFont)
                    setCustomBoldFont("")
                },
            )
        }
        AppearanceSettingsScreen(
            navController = navController,
            startTabs = startTabs,
            customFonts = customFonts,
            customThemeRows = emptyList(),
        )
    }
    composable(DesktopRoutes.AppBehaviour) {
        AppBehaviourSettingsScreen(navController = navController)
    }
    composable(DesktopRoutes.Playback) {
        PlaybackSettingsScreen(navController = navController)
    }
    composable(DesktopRoutes.NowPlayingSettings) {
        // the video size sheet, canvas order picker and floating lyrics switch are Android widgets;
        // leaving them out hides those rows instead of opening a control that can't work here
        NowPlayingSettingsScreen(navController = navController)
    }
    composable(DesktopRoutes.Internet) {
        InternetSettingsScreen(navController = navController)
    }
    composable(DesktopRoutes.Proxy) {
        ProxySettingsScreen(navController = navController, tester = null)
    }
    composable(DesktopRoutes.AiTranslator) {
        val targets = translationTargetOptions()
        AiTranslatorSettingsScreen(
            navController = navController,
            targets = targets,
            languagePicker = { title, selected, onPick, onDismiss ->
                DesktopLanguagePicker(
                    title = title,
                    selected = selected,
                    targets = targets,
                    onPick = onPick,
                    onDismiss = onDismiss,
                )
            },
        )
    }
    composable(DesktopRoutes.NavBar) {
        val (saved, setSaved) = rememberPreference(NavTabsKey, defaultValue = "")
        NavBarSettingsScreen(
            navController = navController,
            saved = saved,
            onSave = setSaved,
            tabs = desktopNavTabs(),
        )
    }
    composable(DesktopRoutes.Experimental) {
        ExperimentalSettingsScreen(navController = navController)
    }

    // pages whose work is still in :app (audio lab, cipher cache, SAF folders, modules, cloud
    // backup...). The shared rows open them, so the desktop keeps the navigation working and says
    // where the page lives today.
    desktopUnavailable(navController, DesktopRoutes.ImportSync, Res.string.import_sync_title)
    desktopUnavailable(navController, DesktopRoutes.ImportProviders, Res.string.import_sync_title)
    desktopUnavailable(navController, DesktopRoutes.ImportTuneMyMusic, Res.string.import_sync_title)
    desktopUnavailable(navController, DesktopRoutes.ImportProgress, Res.string.import_sync_title)
    desktopUnavailable(navController, DesktopRoutes.YouTubeSync, Res.string.import_sync_title)
    desktopUnavailable(navController, DesktopRoutes.Backup, Res.string.integrations)
    desktopUnavailable(navController, DesktopRoutes.Customisation, Res.string.settings_customisation_category)
    desktopUnavailable(navController, DesktopRoutes.Cipher, Res.string.cipher_settings_title)
    desktopUnavailable(navController, DesktopRoutes.PlaybackDiagnostics, Res.string.playback_diagnostics_title)
    desktopUnavailable(navController, DesktopRoutes.AudioLab, Res.string.settings_audio_lab_category)
    desktopUnavailable(navController, DesktopRoutes.AudioFx, Res.string.settings_audio_lab_category)
    desktopUnavailable(navController, DesktopRoutes.AudioStage, Res.string.settings_audio_lab_category)
    desktopUnavailable(navController, DesktopRoutes.AudioQuality, Res.string.audio_quality)
    desktopUnavailable(navController, DesktopRoutes.AudioPass, Res.string.audiopass_hub_title)
    desktopUnavailable(navController, DesktopRoutes.Crossmix, Res.string.transition_title)
    desktopUnavailable(navController, DesktopRoutes.Equalizer, Res.string.equalizer)
    desktopUnavailable(navController, DesktopRoutes.Modules, Res.string.settings_modules_category)
    desktopUnavailable(navController, DesktopRoutes.ModuleBrowser, Res.string.settings_modules_category)
    desktopUnavailable(navController, DesktopRoutes.SpotifyIntegration, Res.string.settings_modules_category)
    desktopUnavailable(navController, DesktopRoutes.DeviceIntegration, Res.string.devices_title)
    desktopUnavailable(navController, DesktopRoutes.AndroidAutoIntegration, Res.string.car_settings_title)
    desktopUnavailable(navController, DesktopRoutes.DiscordIntegration, Res.string.discord_integration_settings)
    desktopUnavailable(navController, DesktopRoutes.AccountSwitcher, Res.string.import_sync_title)
    composable(
        route = DesktopRoutes.ScrobbleService,
        arguments = listOf(navArgument("site") { type = NavType.StringType }),
    ) {
        DesktopUnavailablePage(navController, stringResource(Res.string.integrations))
    }
    composable(DesktopRoutes.DeveloperSpace) {
        // the donate sheet is the Android app's; without it the section is left out
        DeveloperSpaceScreen(navController = navController, onDonate = null)
    }
    composable(DesktopRoutes.DevPlayground) {
        DevPlaygroundScreen(
            navController = navController,
            experimentalSettingsEntry = {
                // the page's last row points back at this build's experimental settings page
                SettingsGroup(
                    style = SettingsGroupStyle.Grouped,
                    items = listOf(
                        SettingsItem(
                            title = { Text(stringResource(Res.string.experimental_settings_title)) },
                            highlightKey = "Experimental",
                            onClick = { navController.navigate(DesktopRoutes.Experimental) { launchSingleTop = true } },
                        ),
                    ),
                )
            },
        )
    }
    desktopUnavailable(navController, DesktopRoutes.ModuleAppleMusic, Res.string.settings_modules_category)
    desktopUnavailable(navController, DesktopRoutes.ModuleMonochrome, Res.string.settings_modules_category)
    desktopUnavailable(navController, DesktopRoutes.YouTubeLogin, Res.string.import_sync_title)
    // a row whose route this graph doesn't name at all still has somewhere to land
    composable(
        route = DesktopRoutes.UnavailablePattern,
        arguments = listOf(navArgument("route") { type = NavType.StringType; defaultValue = "" }),
    ) { entry ->
        val route = entry.arguments?.getString("route").orEmpty()
        DesktopUnavailablePage(navController, route.ifBlank { stringResource(Res.string.settings) })
    }
}

private fun NavGraphBuilder.desktopUnavailable(
    navController: NavHostController,
    route: String,
    title: StringResource,
) {
    composable(route) {
        DesktopUnavailablePage(navController, stringResource(title))
    }
}

@Composable
private fun translationTargetOptions(): List<Pair<String, String>> {
    val targets = ArrayList<Pair<String, String>>(translationTargetIds.size)
    for ((id, label) in translationTargetIds) {
        targets += id to stringResource(label)
    }
    return targets
}

@Composable
private fun desktopNavTabs(): List<NavTab> = listOf(
    NavTab(
        route = DesktopRoutes.Home,
        title = stringResource(Res.string.home),
        icon = painterResource(Res.drawable.nav_home),
        locked = false,
    ),
    NavTab(
        route = DesktopRoutes.Search,
        title = stringResource(Res.string.search),
        icon = painterResource(Res.drawable.nav_search),
        locked = false,
    ),
    NavTab(
        route = DesktopRoutes.Library,
        title = stringResource(Res.string.your_library),
        icon = painterResource(Res.drawable.nav_lib),
        locked = false,
    ),
    NavTab(
        route = DesktopRoutes.Settings,
        title = stringResource(Res.string.settings),
        icon = painterResource(Res.drawable.nav_settings),
        locked = true,
    ),
)

@Composable
private fun DesktopLanguagePicker(
    title: String,
    selected: String,
    targets: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.import_sync_btn_ok)) }
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 12.dp)) {
            items(targets) { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(id) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    Text(
                        text = label,
                        color = if (id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

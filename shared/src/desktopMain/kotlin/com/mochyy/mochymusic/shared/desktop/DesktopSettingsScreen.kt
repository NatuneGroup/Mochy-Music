// DesktopSettingsScreen.kt
// The desktop Settings tab: the shared settings pages from commonMain, reached through the tab's own
// navigation. The desktop launcher provides the composition locals those pages read (LocalAppPreferences,
// LocalSubSettingsChrome, LocalPlayerAwareWindowInsets) at the root, as every platform does.

package com.mochyy.mochymusic.shared.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.audiopass_hub_title
import com.mochyy.mochymusic.shared.resources.shell_coming_body
import com.mochyy.mochymusic.shared.resources.shell_coming_title
import com.mochyy.mochymusic.shared.resources.transition_title
import com.mochyy.mochymusic.shared.settings.PlaybackPreferences
import com.mochyy.mochymusic.shared.ui.settings.CodecBadgeWriter
import com.mochyy.mochymusic.shared.ui.settings.SharedSettingsScreen
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.platformCancelLabel
import com.mochyy.mochymusic.ui.screens.settings.AiTranslatorSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.AiTranslatorSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.PlaybackSettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.PlaybackSettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.ProxySettingsRoute
import com.mochyy.mochymusic.ui.screens.settings.ProxySettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.integrations.ListenBrainzRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.ListenBrainzScreen
import org.jetbrains.compose.resources.stringResource

private const val DesktopSettingsHomeRoute = "desktop_settings"

@Composable
internal fun DesktopSettingsScreen(
    settings: PlaybackPreferences,
    codecBadge: CodecBadgeWriter,
) {
    val navController = rememberNavController()
    // built once: the screen above recomposes with playback and preference changes, and a new builder
    // each time would make NavHost rebuild its graph and put the user back on the settings list mid-page
    val graphBuilder: NavGraphBuilder.() -> Unit = remember(navController) {
        {
            composable(DesktopSettingsHomeRoute) {
                SharedSettingsScreen(
                    settings = settings,
                    codecBadge = codecBadge,
                    onOpenPlayback = { navController.navigate(PlaybackSettingsRoute) },
                    onOpenAiTranslator = { navController.navigate(AiTranslatorSettingsRoute) },
                    onOpenListenBrainz = { navController.navigate(ListenBrainzRoute) },
                    onOpenProxy = { navController.navigate(ProxySettingsRoute) },
                )
            }
            composable(PlaybackSettingsRoute) {
                PlaybackSettingsScreen(navController)
            }
            composable(AiTranslatorSettingsRoute) {
                val targets = desktopTranslationTargets()
                AiTranslatorSettingsScreen(
                    navController = navController,
                    targets = targets,
                    languagePicker = { title, selected, onPick, onDismiss ->
                        DesktopLanguagePicker(title, selected, targets, onPick, onDismiss)
                    },
                )
            }
            composable(ListenBrainzRoute) {
                ListenBrainzScreen(navController)
            }
            composable(ProxySettingsRoute) {
                // no ProxyTester here: the page hides its live-check row when the platform has no connect path
                ProxySettingsScreen(navController)
            }
            // Playback's related rows open Audio Lab pages the Android app registers; desktop has no page
            // for them yet, so they open this instead of failing navigation
            composable("crossmix_settings") {
                DesktopMissingPage(navController, stringResource(Res.string.transition_title))
            }
            composable("audio_pass") {
                DesktopMissingPage(navController, stringResource(Res.string.audiopass_hub_title))
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = DesktopSettingsHomeRoute,
        modifier = Modifier.fillMaxSize(),
        builder = graphBuilder,
    )
}

// the shared picker asks for this: same list as the lyrics menu, names from the app's strings
@Composable
private fun desktopTranslationTargets(): List<Pair<String, String>> =
    desktopTranslationTargetIds
        .map { (code, name) -> code to stringResource(name) }
        .sortedBy { it.second }

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
        horizontalAlignment = Alignment.Start,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(platformCancelLabel()) }
        },
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            items(targets, key = { it.first }) { (code, name) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPick(code)
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                ) {
                    RadioButton(
                        selected = code == selected,
                        onClick = {
                            onPick(code)
                            onDismiss()
                        },
                    )
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// a page the shared settings screens link to but this platform hasn't ported: says so, and goes back
@Composable
private fun DesktopMissingPage(navController: NavController, title: String) {
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

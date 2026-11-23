// MainViewController.kt
// What Swift mounts for the app: the shared Compose UI inside one UIViewController, with the iOS
// runtime's player, queue and library above it. CarPlay builds the same runtime through its own
// scene; this file only wires the phone UI.

package com.mochyy.mochymusic.shared.ios

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeUIViewController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.shared.ios.ui.IosMusicApp
import com.mochyy.mochymusic.shared.ios.ui.IosSettingsTab
import com.mochyy.mochymusic.shared.playback.LocalPlayerConnection
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.inter_regular
import com.mochyy.mochymusic.shared.resources.inter_semibold
import com.mochyy.mochymusic.shared.resources.playback_failed
import com.mochyy.mochymusic.shared.ui.component.LocalSubSettingsChrome
import com.mochyy.mochymusic.shared.ui.component.PlainSubSettingsChrome
import com.mochyy.mochymusic.shared.ui.theme.StandaloneMochyTheme
import com.mochyy.mochymusic.utils.LocalAppPreferences
import com.mochyy.mochymusic.utils.PreferencesCache
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

@Suppress("FunctionName") // Swift calls MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController {
    // warm before the first frame, so rememberPreference starts on the saved value
    PreferencesCache.start(iosPlaybackPreferences.store)
    // covers get the same crossfade, memory share and size setting as Android, with a disk cache
    configureIosImageLoader()
    // shared ViewModels get the store above and iosDatabase, plus the iOS platform bindings; nothing
    // opens either database until a screen asks
    com.mochyy.mochymusic.di.startMochyKoinOnIos()
    return mainViewController()
}

private fun mainViewController(): UIViewController = ComposeUIViewController {
    val playbackFailed = stringResource(Res.string.playback_failed)
    SideEffect { IosPlaybackRuntime.playbackFailedText = playbackFailed }
    val youtube = IosPlaybackRuntime.youtube
    val providers = IosPlaybackRuntime.lyricsProviders
    val library = IosPlaybackRuntime.library
    val queue = IosPlaybackRuntime.queue

    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )
    CompositionLocalProvider(
        LocalAppPreferences provides iosPlaybackPreferences.store,
        LocalPlayerConnection provides IosPlaybackRuntime.connection,
        // the settings pages keep their bottom spacer clear of the home indicator; the shell above
        // pads the tabs and the mini player by the safe area itself
        LocalPlayerAwareWindowInsets provides WindowInsets.safeDrawing,
        // settings sub-pages: nothing covers the bottom here, and their header is a plain tint
        LocalSubSettingsChrome provides PlainSubSettingsChrome,
    ) {
        StandaloneMochyTheme(inter, iosPlaybackPreferences) {
            IosMusicApp(
                source = youtube,
                lyricsProviders = providers,
                settings = iosPlaybackPreferences,
                library = library,
                queue = queue,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                // the Settings tab is the whole shared settings tree, reached through its own graph
                settingsContent = { _, _ -> IosSettingsTab() },
            )
        }
    }
}

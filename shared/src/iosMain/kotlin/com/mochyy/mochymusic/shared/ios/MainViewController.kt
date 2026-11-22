package com.mochyy.mochymusic.shared.ios

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeUIViewController
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.inter_regular
import com.mochyy.mochymusic.shared.resources.inter_semibold
import com.mochyy.mochymusic.shared.resources.playback_failed
import com.mochyy.mochymusic.shared.ui.shell.StandaloneMusicApp
import com.mochyy.mochymusic.shared.ui.theme.StandaloneMochyTheme
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

@Suppress("FunctionName") // Swift calls MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController = ComposeUIViewController {
    val playbackFailed = stringResource(Res.string.playback_failed)
    SideEffect { IosPlaybackRuntime.playbackFailedText = playbackFailed }
    val youtube = IosPlaybackRuntime.youtube
    val player = IosPlaybackRuntime.player
    val providers = IosPlaybackRuntime.lyricsProviders
    val library = IosPlaybackRuntime.library
    val queue = IosPlaybackRuntime.queue

    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )
    StandaloneMochyTheme(inter, iosPlaybackPreferences) {
        StandaloneMusicApp(
            source = youtube,
            player = player,
            lyricsProviders = providers,
            settings = iosPlaybackPreferences,
            library = library,
            queue = queue,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

package com.mochyy.mochymusic.shared.ios

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeUIViewController
import com.mochyy.mochymusic.shared.lyrics.playerLyricsProviders
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.inter_regular
import com.mochyy.mochymusic.shared.resources.inter_semibold
import com.mochyy.mochymusic.shared.resources.playback_failed
import com.mochyy.mochymusic.shared.ui.shell.StandaloneMusicApp
import com.mochyy.mochymusic.shared.ui.theme.MochySharedTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

@Suppress("FunctionName") // Swift calls MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController = ComposeUIViewController {
    val youtube = remember { IosYouTubeClient() }
    val playbackFailed = stringResource(Res.string.playback_failed)
    val player = remember(youtube, playbackFailed) { IosPlayerController(youtube) { playbackFailed } }
    val providers = remember(youtube) { playerLyricsProviders(youtube.http) }

    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(youtube) {
        onDispose { CoroutineScope(Dispatchers.Default).launch { youtube.close() } }
    }

    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )
    MochySharedTheme(font = inter) {
        StandaloneMusicApp(
            source = youtube,
            player = player,
            lyricsProviders = providers,
            settings = iosPlaybackPreferences,
            library = iosLibrary,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

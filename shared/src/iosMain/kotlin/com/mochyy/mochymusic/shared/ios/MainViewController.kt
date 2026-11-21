// MainViewController.kt
// The iOS app's root: Mochy's shared shell in Mochy's theme. Search and playback join once the YouTube
// facade and an AVFoundation PlayerController exist for iOS; until then the tabs show their placeholder.

package com.mochyy.mochymusic.shared.ios

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeUIViewController
import com.mochyy.mochymusic.shared.ui.shell.MochyShell
import com.mochyy.mochymusic.shared.ui.theme.MochySharedTheme
import platform.UIKit.UIViewController

@Suppress("FunctionName") // Swift calls it as MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController = ComposeUIViewController {
    // Inter reaches iOS with the font resources move; the system face stands in until then
    MochySharedTheme(font = FontFamily.Default) {
        MochyShell()
    }
}

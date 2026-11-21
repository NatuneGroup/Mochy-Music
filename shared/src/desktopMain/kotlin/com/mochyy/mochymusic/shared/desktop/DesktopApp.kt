// DesktopApp.kt
// The desktop entry point the launcher (:desktopApp) hands off to. Windows and start-up live here, in
// the library, so the launcher only owns main() and packaging.

package com.mochyy.mochymusic.shared.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.app_name
import com.mochyy.mochymusic.shared.ui.shell.MochyShell
import com.mochyy.mochymusic.shared.ui.theme.MochySharedTheme
import org.jetbrains.compose.resources.stringResource

/** Opens Mochy's main window with [font] as the app's typeface (the launcher loads Inter for it). */
fun runDesktopApp(font: FontFamily) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        MochySharedTheme(font = font) {
            MochyShell()
        }
    }
}

// DesktopWindowSettingsPage.kt
// Settings > Desktop window: the three switches and the folder button a window needs and a phone
// doesn't. It sits on the desktop graph beside the shared settings pages and reads the same
// rememberPreference store the launcher reads before the first frame, so a change applies to the next
// window without a second copy of the setting living in the launcher.

package com.mochyy.mochymusic.shared.desktop.window

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.shared.desktop.DesktopPaths
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.desktop_close_to_tray
import com.mochyy.mochymusic.shared.resources.desktop_close_to_tray_desc
import com.mochyy.mochymusic.shared.resources.desktop_open_folder
import com.mochyy.mochymusic.shared.resources.desktop_open_folder_desc
import com.mochyy.mochymusic.shared.resources.desktop_remember_window
import com.mochyy.mochymusic.shared.resources.desktop_remember_window_desc
import com.mochyy.mochymusic.shared.resources.desktop_shortcuts
import com.mochyy.mochymusic.shared.resources.desktop_shortcuts_body
import com.mochyy.mochymusic.shared.resources.desktop_start_minimized
import com.mochyy.mochymusic.shared.resources.desktop_start_minimized_desc
import com.mochyy.mochymusic.shared.resources.desktop_tray_icon
import com.mochyy.mochymusic.shared.resources.desktop_tray_icon_desc
import com.mochyy.mochymusic.shared.resources.desktop_window_settings
import com.mochyy.mochymusic.shared.resources.nav_mochy_filled
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.rememberPreference
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopWindowSettingsPage(navController: NavController) {
    val (closeToTray, setCloseToTray) = rememberPreference(DesktopCloseToTrayKey, defaultValue = true)
    val (startMinimized, setStartMinimized) = rememberPreference(DesktopStartMinimizedKey, defaultValue = false)
    val (trayIcon, setTrayIcon) = rememberPreference(DesktopTrayIconKey, defaultValue = true)
    val (rememberWindow, setRememberWindow) = rememberPreference(DesktopRememberWindowKey, defaultValue = true)

    SubSettingsScaffold(
        title = stringResource(Res.string.desktop_window_settings),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                toggleItem(
                    title = stringResource(Res.string.desktop_close_to_tray),
                    description = stringResource(Res.string.desktop_close_to_tray_desc),
                    checked = closeToTray,
                    onChange = setCloseToTray,
                ),
                // starting in the tray only means something while there is a tray icon to come back from
                toggleItem(
                    title = stringResource(Res.string.desktop_start_minimized),
                    description = stringResource(Res.string.desktop_start_minimized_desc),
                    checked = startMinimized,
                    onChange = setStartMinimized,
                    enabled = trayIcon,
                ),
                toggleItem(
                    title = stringResource(Res.string.desktop_tray_icon),
                    description = stringResource(Res.string.desktop_tray_icon_desc),
                    checked = trayIcon,
                    onChange = setTrayIcon,
                    icon = Res.drawable.nav_mochy_filled,
                ),
                toggleItem(
                    title = stringResource(Res.string.desktop_remember_window),
                    description = stringResource(Res.string.desktop_remember_window_desc),
                    checked = rememberWindow,
                    onChange = setRememberWindow,
                ),
            ),
        )
        Spacer(Modifier.height(16.dp))
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(Res.string.desktop_open_folder)) },
                    descriptionText = stringResource(Res.string.desktop_open_folder_desc),
                    highlightKey = "Mochy folder",
                    onClick = { openDataFolder() },
                ),
            ),
        )
        Spacer(Modifier.height(16.dp))
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(Res.string.desktop_shortcuts)) },
                    descriptionText = stringResource(Res.string.desktop_shortcuts_body),
                    highlightKey = "Keyboard shortcuts",
                ),
            ),
        )
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun toggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
): SettingsItem = SettingsItem(
    icon = icon?.let { painterResource(it) },
    title = { Text(title) },
    descriptionText = description,
    highlightKey = title,
    enabled = enabled,
    onClick = { onChange(!checked) },
    trailingContent = {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
        )
    },
)

// the platform's own file manager; a machine without one just doesn't open anything
private fun openDataFolder() {
    runCatching {
        val file = DesktopPaths.appData.apply { mkdirs() }
        java.awt.Desktop.getDesktop().open(file)
    }
}

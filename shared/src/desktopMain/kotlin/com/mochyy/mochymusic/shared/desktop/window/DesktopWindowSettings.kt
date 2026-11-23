// DesktopWindowSettings.kt
// The window bits a phone never has: whether closing the window hides it to the tray, whether Mochy
// starts minimised, whether the tray icon exists at all, and whether the window remembers where it
// was. They live in the same settings file the rest of the app reads through LocalAppPreferences,
// under desktop-only keys, so the desktop settings page uses rememberPreference like every other row
// and the launcher reads the same values before the first frame.

package com.mochyy.mochymusic.shared.desktop.window

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

// settings the window page toggles
val DesktopCloseToTrayKey = booleanPreferencesKey("desktopCloseToTray")
val DesktopStartMinimizedKey = booleanPreferencesKey("desktopStartMinimized")
val DesktopTrayIconKey = booleanPreferencesKey("desktopTrayIcon")
val DesktopRememberWindowKey = booleanPreferencesKey("desktopRememberWindow")

// where the window was, in dp, and whether it was maximised there
private val WindowXKey = intPreferencesKey("desktopWindowX")
private val WindowYKey = intPreferencesKey("desktopWindowY")
private val WindowWidthKey = intPreferencesKey("desktopWindowWidth")
private val WindowHeightKey = intPreferencesKey("desktopWindowHeight")
private val WindowMaximizedKey = booleanPreferencesKey("desktopWindowMaximized")

/** the window's last place and size, in dp; [maximized] is how it was left, not a size it ever had */
data class WindowGeometry(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
)

/** everything the launcher needs to open the window the way the app was left */
data class DesktopWindowSettings(
    val closeToTray: Boolean = true,
    val startMinimized: Boolean = false,
    val trayIcon: Boolean = true,
    val rememberWindow: Boolean = true,
    val geometry: WindowGeometry? = null,
)

// the size a first run opens with, the same one the window always had
const val DefaultWindowWidth = 1100
const val DefaultWindowHeight = 720

suspend fun readDesktopWindowSettings(store: DataStore<Preferences>): DesktopWindowSettings {
    val preferences = store.data.first()
    val geometry = if (preferences[WindowWidthKey] != null && preferences[WindowHeightKey] != null) {
        WindowGeometry(
            x = preferences[WindowXKey] ?: 0,
            y = preferences[WindowYKey] ?: 0,
            width = preferences[WindowWidthKey] ?: DefaultWindowWidth,
            height = preferences[WindowHeightKey] ?: DefaultWindowHeight,
            maximized = preferences[WindowMaximizedKey] ?: false,
        )
    } else {
        null
    }
    return DesktopWindowSettings(
        closeToTray = preferences[DesktopCloseToTrayKey] ?: true,
        startMinimized = preferences[DesktopStartMinimizedKey] ?: false,
        trayIcon = preferences[DesktopTrayIconKey] ?: true,
        rememberWindow = preferences[DesktopRememberWindowKey] ?: true,
        geometry = geometry,
    )
}

/** Saves where the window sits now. Only called when remembering is on. */
suspend fun saveWindowGeometry(store: DataStore<Preferences>, geometry: WindowGeometry) {
    store.edit { preferences ->
        preferences[WindowXKey] = geometry.x
        preferences[WindowYKey] = geometry.y
        preferences[WindowWidthKey] = geometry.width
        preferences[WindowHeightKey] = geometry.height
        preferences[WindowMaximizedKey] = geometry.maximized
    }
}

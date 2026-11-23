// DesktopWindowPlacement.kt
// Turning a saved window rectangle back into a window. A screen may have gone, or the saved place may
// be off the edge of every screen now, so a restore only counts when some display still holds its
// top-left corner: otherwise the window opens centred at its old size, never somewhere invisible.

package com.mochyy.mochymusic.shared.desktop.window

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** how much of the window's top-left corner has to be on a display for a restore to be trusted */
private const val RestoreProbeWidth = 96
private const val RestoreProbeHeight = 48

/** The bounds of every display the window could open on, in virtual-screen pixels. */
internal fun displayBounds(): List<Rectangle> =
    runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .map { it.defaultConfiguration.bounds }
    }.getOrDefault(emptyList())

/** True when at least one display still shows the window's corner. */
internal fun WindowGeometry.isOnScreen(displays: List<Rectangle> = displayBounds()): Boolean {
    if (displays.isEmpty()) return true // unknown display layout: keep the size, let the OS place it
    val probe = Rectangle(x, y, RestoreProbeWidth, RestoreProbeHeight)
    return displays.any { it.intersects(probe) }
}

/** The window state a saved [geometry] opens with; null geometry gives the first-run size, centred. */
internal fun desktopWindowState(geometry: WindowGeometry?): WindowState {
    val size = DpSize(
        width = (geometry?.width ?: DefaultWindowWidth).coerceAtLeast(640).dp,
        height = (geometry?.height ?: DefaultWindowHeight).coerceAtLeast(420).dp,
    )
    val placement = if (geometry?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating
    val position = if (geometry != null && geometry.isOnScreen()) {
        WindowPosition.Absolute(geometry.x.dp, geometry.y.dp)
    } else {
        WindowPosition(Alignment.Center)
    }
    return WindowState(placement = placement, position = position, size = size)
}

/** The current window, as it will be saved. A maximised window keeps the size it would restore to. */
internal fun WindowState.currentGeometry(): WindowGeometry {
    val absolute = position as? WindowPosition.Absolute
    return WindowGeometry(
        x = absolute?.x?.value?.toInt() ?: 0,
        y = absolute?.y?.value?.toInt() ?: 0,
        width = size.width.value.toInt(),
        height = size.height.value.toInt(),
        maximized = placement == WindowPlacement.Maximized,
    )
}

// the insets a desktop window has none of; named here so the launcher doesn't spell them inline
internal val NoWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)

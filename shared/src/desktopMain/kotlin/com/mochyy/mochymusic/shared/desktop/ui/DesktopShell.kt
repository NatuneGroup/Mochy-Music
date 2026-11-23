// DesktopShell.kt
// The desktop window frame: Mochy's tabs stand in a rail beside the page (wide windows) or along the
// bottom (narrow ones), the mini player sits under both, and a stats shortcut lives beside the rail.
// The rail follows Settings > Appearance > Navigation bar, the same saved order the phone reads.

package com.mochyy.mochymusic.shared.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mochyy.mochymusic.constants.NavTabsKey
import com.mochyy.mochymusic.shared.desktop.navigation.DesktopRoutes
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.discover_tune
import com.mochyy.mochymusic.shared.resources.explore
import com.mochyy.mochymusic.shared.resources.music_history
import com.mochyy.mochymusic.shared.resources.rc_stats_title
import com.mochyy.mochymusic.shared.ui.shell.ShellTab
import com.mochyy.mochymusic.shared.ui.theme.LocalMochyAccent
import com.mochyy.mochymusic.ui.screens.settings.decodeNavTabOrder
import com.mochyy.mochymusic.utils.rememberPreference
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// below this width the rail has no room and the tabs move under the page, as the shared shell does
private val RailBreakpoint = 600.dp

internal fun ShellTab.route(): String = when (this) {
    ShellTab.Home -> DesktopRoutes.Home
    ShellTab.Search -> DesktopRoutes.Search
    ShellTab.Library -> DesktopRoutes.Library
    ShellTab.Settings -> DesktopRoutes.Settings
}

@Composable
internal fun DesktopShell(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val (savedTabs, _) = rememberPreference(NavTabsKey, defaultValue = "")
    val tabs = remember(savedTabs) {
        val ordered = decodeNavTabOrder(savedTabs, ShellTab.entries.toList()) { it.route() }
            .filter { it.second }
            .map { it.first }
        // Settings can be hidden on the phone; the desktop needs it as the way into the shared pages
        if (ShellTab.Settings in ordered) ordered else ordered + ShellTab.Settings
    }
    val settingsSelected = route != null && route in DesktopRoutes.settingsRoutes

    fun select(tab: ShellTab) {
        navController.navigate(tab.route()) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= RailBreakpoint
        if (wide) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxHeight().width(88.dp).padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tabs.forEach { tab ->
                            val active = if (tab == ShellTab.Settings) settingsSelected else isTabSelected(tab, route)
                            DesktopTabButton(
                                title = stringResource(tab.title),
                                icon = if (active) tab.iconActive else tab.icon,
                                active = active,
                                onClick = { select(tab) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // the online pages sit beside the tabs, like the stats shortcut under them
                        DesktopTabButton(
                            title = stringResource(Res.string.explore),
                            icon = Res.drawable.discover_tune,
                            active = route == DesktopRoutes.Explore,
                            onClick = { navController.navigate(DesktopRoutes.Explore) { launchSingleTop = true } },
                        )
                        DesktopTabButton(
                            title = stringResource(Res.string.rc_stats_title),
                            icon = Res.drawable.music_history,
                            active = route == DesktopRoutes.Stats,
                            onClick = { navController.navigate(DesktopRoutes.Stats) { launchSingleTop = true } },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
                bottomBar()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                bottomBar()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val active = if (tab == ShellTab.Settings) settingsSelected else isTabSelected(tab, route)
                        DesktopTabButton(
                            title = stringResource(tab.title),
                            icon = if (active) tab.iconActive else tab.icon,
                            active = active,
                            onClick = { select(tab) },
                        )
                    }
                    DesktopTabButton(
                        title = stringResource(Res.string.explore),
                        icon = Res.drawable.discover_tune,
                        active = route == DesktopRoutes.Explore,
                        onClick = { navController.navigate(DesktopRoutes.Explore) { launchSingleTop = true } },
                    )
                    DesktopTabButton(
                        title = stringResource(Res.string.rc_stats_title),
                        icon = Res.drawable.music_history,
                        active = route == DesktopRoutes.Stats,
                        onClick = { navController.navigate(DesktopRoutes.Stats) { launchSingleTop = true } },
                    )
                }
            }
        }
    }
}

private fun isTabSelected(tab: ShellTab, route: String?): Boolean = route == tab.route()

@Composable
private fun DesktopTabButton(
    title: String,
    icon: DrawableResource,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) LocalMochyAccent.current ?: Color.White else Color.White.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = title,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(title, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

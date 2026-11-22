// NavBarSettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.NavTabsKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.NavTabSlot
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.component.encodeNavTabs
import com.mochyy.mochymusic.ui.component.navTabSlots
import com.mochyy.mochymusic.ui.screens.Screens
import com.mochyy.mochymusic.utils.rememberPreference

// settings > appearance > navigation bar: which tabs the bar shows and in what order
@Composable
fun NavBarSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    val (saved, setSaved) = rememberPreference(NavTabsKey, defaultValue = "")
    val slots = remember(saved) { navTabSlots(saved) }

    // one save for every change: the new order and shown flags go back as one string
    val save = { changed: List<NavTabSlot> -> setSaved(encodeNavTabs(changed)) }
    val items = slots.mapIndexed { index, slot ->
        tabItem(
            slot = slot,
            canMoveUp = index > 0,
            canMoveDown = index < slots.lastIndex,
            onMoveUp = { save(slots.swapped(index, index - 1)) },
            onMoveDown = { save(slots.swapped(index, index + 1)) },
            onShownChange = { shown -> save(slots.toMutableList().also { it[index] = slot.copy(shown = shown) }) },
        )
    }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_nav_bar),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        SettingsSection(title = null, items = items.matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

private fun List<NavTabSlot>.swapped(from: Int, to: Int): List<NavTabSlot> =
    toMutableList().also { it[from] = this[to]; it[to] = this[from] }

@Composable
private fun tabItem(
    slot: NavTabSlot,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onShownChange: (Boolean) -> Unit,
): SettingsItem {
    val title = stringResource(slot.screen.titleId)
    // settings is always in the bar, so its switch is locked on
    val locked = slot.screen == Screens.Settings
    return SettingsItem(
        title = { Text(title) },
        highlightKey = slot.screen.route,
        searchText = title,
        descriptionText = if (locked) stringResource(R.string.settings_nav_bar_always) else null,
        icon = painterResource(R.drawable.nav_bar),
        iconShape = CircleShape,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoveButton(R.drawable.arrow_upward, stringResource(R.string.settings_nav_move_up), canMoveUp, onMoveUp)
                MoveButton(R.drawable.arrow_downward, stringResource(R.string.settings_nav_move_down), canMoveDown, onMoveDown)
                AppSwitch(checked = slot.shown, onCheckedChange = onShownChange, enabled = !locked)
            }
        },
    )
}

@Composable
private fun MoveButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = Color.White.copy(alpha = if (enabled) 0.8f else 0.25f),
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
    )
}

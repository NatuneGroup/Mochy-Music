// AppearanceSettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AccentColorKey
import com.mochyy.mochymusic.constants.AccentFromArtKey
import com.mochyy.mochymusic.constants.DefaultOpenTabKey
import com.mochyy.mochymusic.constants.HideNavOnScrollKey
import com.mochyy.mochymusic.constants.SystemFontKey
import com.mochyy.mochymusic.constants.TabletLayoutKey
import com.mochyy.mochymusic.constants.UiScalePercentKey
import com.mochyy.mochymusic.shared.ui.theme.MochyAccentPresets
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.screens.NavigationTab
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// settings > appearance: the tabs, the layout and the colours the app opens with
@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        SettingsSection(title = null, items = (startItems() + navBarItems(navController) + accentItems() + customThemeItems()).matching(query))
        SettingsSection(title = null, items = (scaleItems() + tabletItems() + fontItems()).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun startItems(): List<SettingsItem> {
    val (startTab, setStartTab) = rememberEnumPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME)
    val title = stringResource(R.string.settings_start_tab)
    return listOf(
        // the choices sit in the row itself, so there is no separate sheet to open
        SettingsItem(
            title = { Text(title) },
            highlightKey = "Default start tab",
            searchText = title,
            description = { TabChoices(selected = startTab, onPick = setStartTab) },
            icon = painterResource(R.drawable.home),
            iconShape = CircleShape,
        ),
    )
}

@Composable
private fun navBarItems(navController: NavController): List<SettingsItem> {
    val (hideOnScroll, setHideOnScroll) = rememberPreference(HideNavOnScrollKey, defaultValue = false)
    return listOf(
        linkItem(
            key = "Navigation bar",
            title = stringResource(R.string.settings_nav_bar),
            description = stringResource(R.string.settings_nav_bar_desc),
            icon = painterResource(R.drawable.nav_bar),
            onClick = { navController.navigate("nav_bar_settings") },
        ),
        switchItem(
            key = "Hide navigation bar on scroll",
            title = stringResource(R.string.settings_hide_nav_on_scroll),
            description = stringResource(R.string.settings_hide_nav_on_scroll_desc),
            icon = painterResource(R.drawable.arrow_downward),
            checked = hideOnScroll,
            onCheckedChange = setHideOnScroll,
        ),
    )
}

// the steps the scale slider stops on, from smaller than a phone's own size to a little bigger
private val UiScaleSteps = listOf(85, 90, 95, 100, 105, 110, 115)

@Composable
private fun scaleItems(): List<SettingsItem> {
    val (scale, setScale) = rememberPreference(UiScalePercentKey, defaultValue = 100)
    val title = stringResource(R.string.settings_ui_scale)
    return listOf(
        SettingsItem(
            title = { ValueTitle(title, "$scale%") },
            highlightKey = "UI scale",
            searchText = title,
            description = {
                DefaultPlayerSeekBarSlider(
                    value = scale.toFloat(),
                    onValueChange = { setScale(it.roundToInt()) },
                    valueRange = 85f..115f,
                    steps = UiScaleSteps.size - 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.fullscreen),
            iconShape = CircleShape,
        ),
    )
}

@Composable
private fun fontItems(): List<SettingsItem> {
    val (systemFont, setSystemFont) = rememberPreference(SystemFontKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "System font",
            title = stringResource(R.string.settings_system_font),
            description = stringResource(R.string.settings_system_font_desc),
            icon = painterResource(R.drawable.translate),
            checked = systemFont,
            onCheckedChange = setSystemFont,
        ),
    )
}

@Composable
private fun tabletItems(): List<SettingsItem> {
    val (tablet, setTablet) = rememberPreference(TabletLayoutKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "Tablet layout",
            title = stringResource(R.string.settings_tablet_layout),
            description = stringResource(R.string.settings_tablet_layout_desc),
            icon = painterResource(R.drawable.screen_rotation),
            checked = tablet,
            onCheckedChange = setTablet,
        ),
    )
}

@Composable
private fun accentItems(): List<SettingsItem> {
    val (accentInt) = rememberPreference(AccentColorKey, defaultValue = 0)
    val (fromArt) = rememberPreference(AccentFromArtKey, defaultValue = false)
    val store = LocalContext.current.applicationContext.dataStore
    val scope = rememberCoroutineScope()
    fun selectAccent(color: Int?) {
        scope.launch {
            store.edit { preferences ->
                if (color != null) preferences[AccentColorKey] = color
                preferences[AccentFromArtKey] = color == null
            }
        }
    }
    val title = stringResource(R.string.settings_accent)
    return listOf(
        SettingsItem(
            title = { Text(title) },
            highlightKey = "Accent colour",
            searchText = title,
            description = {
                AccentChoices(
                    picked = accentInt,
                    fromArt = fromArt,
                    onPick = ::selectAccent,
                    onPickArt = { selectAccent(null) },
                )
            },
            icon = painterResource(R.drawable.palette),
            iconShape = CircleShape,
        ),
    )
}

@Composable
private fun AccentChoices(picked: Int, fromArt: Boolean, onPick: (Int) -> Unit, onPickArt: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 0 is mochy's own grey, the first swatch puts it back
            Swatch(color = Color(0xFF8E8E8E), selected = !fromArt && picked == 0) { onPick(0) }
            MochyAccentPresets.forEach { argb ->
                Swatch(color = Color(argb), selected = !fromArt && picked == argb) { onPick(argb) }
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (fromArt) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                .clickable { onPickArt() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_accent_art),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = if (fromArt) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun TabChoices(selected: NavigationTab, onPick: (NavigationTab) -> Unit) {
    val choices = listOf(
        NavigationTab.HOME to R.string.home,
        NavigationTab.SEARCH to R.string.search,
        NavigationTab.LIBRARY to R.string.your_library,
    )
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (tab, label) ->
            val on = tab == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                    .clickable { onPick(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(label),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                )
            }
        }
    }
}

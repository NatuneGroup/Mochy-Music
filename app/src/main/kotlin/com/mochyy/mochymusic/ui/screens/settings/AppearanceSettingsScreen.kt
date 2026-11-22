// AppearanceSettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.DefaultOpenTabKey
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.screens.NavigationTab
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference

// settings > appearance: the tabs, the layout and the colours the app opens with
@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        SettingsSection(title = null, items = startItems().matching(query))
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

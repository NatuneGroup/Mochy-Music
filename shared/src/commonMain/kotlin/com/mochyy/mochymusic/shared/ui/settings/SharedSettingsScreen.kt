package com.mochyy.mochymusic.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.settings
import com.mochyy.mochymusic.shared.resources.settings_player_display
import com.mochyy.mochymusic.shared.resources.settings_playback_category
import com.mochyy.mochymusic.shared.resources.settings_write_failed
import com.mochyy.mochymusic.shared.resources.show_format_badge
import com.mochyy.mochymusic.shared.resources.show_format_sample_rate
import com.mochyy.mochymusic.shared.settings.PlaybackPreferenceKeys
import com.mochyy.mochymusic.shared.settings.PlaybackPreferences
import kotlinx.coroutines.launch
import okio.IOException
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedSettingsScreen(
    settings: PlaybackPreferences,
    modifier: Modifier = Modifier,
    additionalSections: @Composable ColumnScope.() -> Unit = {},
) {
    val preferences by settings.values.collectAsState(initial = emptyPreferences())
    val showBadge = preferences[PlaybackPreferenceKeys.showFormatBadge] ?: true
    val showSampleRate = preferences[PlaybackPreferenceKeys.showSampleRate] ?: false
    var writeFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun setPreference(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch {
            writeFailed = try {
                settings.set(key, value)
                false
            } catch (_: IOException) {
                true
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(Res.string.settings),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        SettingsSection(stringResource(Res.string.settings_playback_category)) {
            Text(
                stringResource(Res.string.settings_player_display),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            SettingsCard {
                PlaybackSettingRow(stringResource(Res.string.show_format_badge), showBadge) {
                    setPreference(PlaybackPreferenceKeys.showFormatBadge, it)
                }
                PlaybackSettingRow(
                    stringResource(Res.string.show_format_sample_rate),
                    showSampleRate,
                    enabled = showBadge,
                ) {
                    setPreference(PlaybackPreferenceKeys.showSampleRate, it)
                }
            }
        }
        additionalSections()
        if (writeFailed) {
            Text(stringResource(Res.string.settings_write_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun PlaybackSettingRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

// The Internet settings page, ported from the screenshots' design: the YouTube Music region the
// requests pin themselves to, a quality ceiling per connection, DNS over HTTPS, and the proxy —
// all the settings that shape how the app reaches the network, on one page, with the warning
// card out front because these are the rows a listener behind a block reaches for.

package com.mochyy.mochymusic.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.constants.AudioQualityCellularKey
import com.mochyy.mochymusic.constants.AudioQualityKey
import com.mochyy.mochymusic.constants.AudioQualityWifiKey
import com.mochyy.mochymusic.constants.ContentCountryKey
import com.mochyy.mochymusic.constants.CountryCodeToName
import com.mochyy.mochymusic.constants.DnsOverHttpsCustomUrlKey
import com.mochyy.mochymusic.constants.DnsOverHttpsEnabledKey
import com.mochyy.mochymusic.constants.DnsOverHttpsProviderKey
import com.mochyy.mochymusic.constants.SYSTEM_DEFAULT
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.utils.AppDns
import com.mochyy.mochymusic.utils.NetMetered
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

const val InternetSettingsRoute = "internet_settings"

@Composable
fun InternetSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val (region, setRegion) = rememberPreference(ContentCountryKey, SYSTEM_DEFAULT)
    val (wifiQuality, setWifiQuality) = rememberPreference(AudioQualityWifiKey, AudioQuality.AUTO.name)
    val (cellularQuality, setCellularQuality) = rememberPreference(AudioQualityCellularKey, AudioQuality.AUTO.name)
    val metered by NetMetered.metered.collectAsState()
    val (dohEnabled, setDohEnabled) = rememberPreference(DnsOverHttpsEnabledKey, defaultValue = false)
    val (dohProvider, setDohProvider) = rememberPreference(DnsOverHttpsProviderKey, defaultValue = "Cloudflare")
    val (dohCustomUrl, setDohCustomUrl) = rememberPreference(DnsOverHttpsCustomUrlKey, defaultValue = "https://")
    val (proxyEnabled, setProxyEnabled) = rememberPreference(com.mochyy.mochymusic.constants.ProxyEnabledKey, defaultValue = false)
    var dialog by remember { mutableStateOf<InternetDialog?>(null) }

    // the single audio-quality choice feeds the per-connection ones the first time in, so an
    // update keeps what was picked and this page shows what plays
    LaunchedEffect(Unit) {
        val store = context.dataStore.data
        val prefs = store.first()
        if (prefs[AudioQualityWifiKey] == null && prefs[AudioQualityCellularKey] == null) {
            prefs[AudioQualityKey]?.let { legacy ->
                context.dataStore.updateData { prefs ->
                    prefs.toMutablePreferences().apply {
                        set(AudioQualityWifiKey, legacy)
                        set(AudioQualityCellularKey, legacy)
                    }
                }
            }
        }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.internet),
        onBack = { navController.navigateUp() },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InternetWarningCard()

            // ── the region the YouTube Music requests pin themselves to ──
            SettingsGroup(
                title = stringResource(R.string.youtube_music_region),
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.youtube_music_region)) },
                        descriptionText = stringResource(R.string.youtube_music_region_desc),
                        icon = painterResource(R.drawable.music_note),
                        iconShape = CircleShape,
                        onClick = { dialog = InternetDialog.Region },
                        trailingContent = {
                            Text(
                                text = regionLabel(region).ifEmpty { stringResource(R.string.system_default) },
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    ),
                ),
            )

            // ── the quality ceilings, one per connection, the one in force badged ──
            SettingsGroup(
                title = stringResource(R.string.audio_quality),
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.on_wifi)) },
                        descriptionText = if (metered == false) stringResource(R.string.in_use) else null,
                        icon = painterResource(R.drawable.wifi),
                        iconShape = CircleShape,
                        onClick = { dialog = InternetDialog.WifiQuality },
                        trailingContent = { QualityLabel(wifiQuality) },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.on_mobile_data)) },
                        descriptionText = if (metered == true) stringResource(R.string.in_use) else null,
                        icon = painterResource(R.drawable.cellular),
                        iconShape = CircleShape,
                        onClick = { dialog = InternetDialog.CellularQuality },
                        trailingContent = { QualityLabel(cellularQuality) },
                    ),
                ),
            )

            // ── DNS over HTTPS, and the provider that answers it ──
            SettingsGroup(
                title = stringResource(R.string.dns_over_https),
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.dns_over_https)) },
                            descriptionText = stringResource(R.string.dns_over_https_desc),
                            icon = painterResource(R.drawable.dns),
                            iconShape = CircleShape,
                            onClick = { setDohEnabled(!dohEnabled) },
                            trailingContent = {
                                AppSwitch(checked = dohEnabled, onCheckedChange = { setDohEnabled(it) })
                            },
                        ),
                    )
                    if (dohEnabled) {
                        add(
                            SettingsItem(
                                title = { Text(stringResource(R.string.dns_provider)) },
                                icon = painterResource(R.drawable.language),
                                iconShape = CircleShape,
                                onClick = { dialog = InternetDialog.DnsProvider },
                                trailingContent = {
                                    Text(
                                        text = dohProvider,
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                            ),
                        )
                        if (dohProvider == "Custom") {
                            add(
                                SettingsItem(
                                    title = { Text(stringResource(R.string.dns_custom_url)) },
                                    descriptionText = dohCustomUrl,
                                    icon = painterResource(R.drawable.link),
                                    iconShape = CircleShape,
                                    onClick = { dialog = InternetDialog.CustomDnsUrl },
                                ),
                            )
                        }
                    }
                },
            )

            // ── the proxy: its switch here, its own page for the details ──
            SettingsGroup(
                title = stringResource(R.string.proxy),
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.enable_proxy)) },
                        icon = painterResource(R.drawable.wifi_proxy),
                        iconShape = CircleShape,
                        onClick = { setProxyEnabled(!proxyEnabled) },
                        trailingContent = {
                            AppSwitch(checked = proxyEnabled, onCheckedChange = { setProxyEnabled(it) })
                        },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.proxy_settings)) },
                        descriptionText = stringResource(R.string.proxy_settings_desc),
                        icon = painterResource(R.drawable.network_node),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(ProxySettingsRoute) },
                    ),
                ),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    when (dialog) {
        InternetDialog.Region -> RegionDialog(
            selected = region,
            onSelect = {
                dialog = null
                if (it != region) {
                    setRegion(it)
                    restartForRegionChange(context)
                }
            },
            onDismiss = { dialog = null },
        )
        InternetDialog.WifiQuality -> QualityDialog(
            title = stringResource(R.string.on_wifi),
            selected = wifiQuality,
            onSelect = { setWifiQuality(it); dialog = null },
            onDismiss = { dialog = null },
        )
        InternetDialog.CellularQuality -> QualityDialog(
            title = stringResource(R.string.on_mobile_data),
            selected = cellularQuality,
            onSelect = { setCellularQuality(it); dialog = null },
            onDismiss = { dialog = null },
        )
        InternetDialog.DnsProvider -> DefaultDialog(
            onDismiss = { dialog = null },
            title = { Text(stringResource(R.string.dns_provider)) },
            horizontalAlignment = Alignment.Start,
        ) {
            AppDns.providers.forEach { provider ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setDohProvider(provider); dialog = null }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = provider == dohProvider, onClick = { setDohProvider(provider); dialog = null })
                    Text(provider, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        InternetDialog.CustomDnsUrl -> TextFieldDialog(
            title = { Text(stringResource(R.string.dns_custom_url)) },
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(dohCustomUrl),
            placeholder = { Text("https://") },
            isInputValid = { it.startsWith("https://") },
            onDone = { setDohCustomUrl(it.trim()); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** the quality rows' value, the rung's own name in the app's own words */
@Composable
private fun QualityLabel(stored: String) {
    Text(
        text = qualityLabel(stored),
        color = Color.White.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun qualityLabel(name: String): String {
    val quality = AudioQuality.entries.find { it.name == name } ?: AudioQuality.AUTO
    return when (quality) {
        AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
        AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
        AudioQuality.MEDIUM -> stringResource(R.string.audio_quality_medium)
        AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
        AudioQuality.LOSSLESS -> stringResource(R.string.audio_quality_lossless)
        AudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.audio_quality_hi_res)
        AudioQuality.DOLBY_ATMOS -> stringResource(R.string.audio_quality_dolby)
    }
}

@Composable
private fun QualityDialog(title: String, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        horizontalAlignment = Alignment.Start,
    ) {
        AudioQuality.entries.forEach { quality ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(quality.name) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = quality.name == selected, onClick = { onSelect(quality.name) })
                Text(qualityLabel(quality.name), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun RegionDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.youtube_music_region)) },
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .height(384.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            (listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.sorted()).forEach { code ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(code) }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = code == selected, onClick = { onSelect(code) })
                    Text(
                        text = if (code == SYSTEM_DEFAULT) stringResource(R.string.system_default)
                        else CountryCodeToName[code] ?: code,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

internal fun regionLabel(code: String): String =
    if (code == SYSTEM_DEFAULT) "" else CountryCodeToName[code] ?: code

/**
 * Applies a region change the way the ported design does: the visitor data and every cached
 * response were minted in the old region, so the process is replaced rather than
 * half-invalidated. The toast is held open briefly before the relaunch so it reads.
 */
internal fun restartForRegionChange(context: Context) {
    Toast.makeText(context, context.getString(R.string.youtube_music_region_restarting), Toast.LENGTH_SHORT).show()
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
    scope.launch {
        delay(400)
        withContext(kotlinx.coroutines.NonCancellable) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(launchIntent)
            }
            exitProcess(0)
        }
    }
}

/** which of the page's dialogs is open */
private enum class InternetDialog {
    Region,
    WifiQuality,
    CellularQuality,
    DnsProvider,
    CustomDnsUrl,
}

/** the warning card that leads the page, the ported design's own words */
@Composable
private fun InternetWarningCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFB3261E).copy(alpha = 0.42f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.warning),
                contentDescription = null,
                tint = Color(0xFFFFB4AB),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.internet_warning_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFB4AB),
            )
        }
        Text(
            text = stringResource(R.string.internet_warning_doh),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF9DEDC),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.internet_warning_proxy),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF9DEDC),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

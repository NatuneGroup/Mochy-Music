// DesktopAboutPage.kt
// The About page a desktop app keeps in its settings: what Mochy is, the credit it is built on, the
// licence, and the two links worth opening (the source and the licence text). Nothing here is a new
// claim about the app: the credit line is the same shared string the shells show.

package com.mochyy.mochymusic.shared.desktop.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.shared.desktop.navigation.DesktopRoutes
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.app_name
import com.mochyy.mochymusic.shared.resources.desktop_about_body
import com.mochyy.mochymusic.shared.resources.desktop_about_license
import com.mochyy.mochymusic.shared.resources.desktop_about_source
import com.mochyy.mochymusic.shared.resources.desktop_about_title
import com.mochyy.mochymusic.shared.resources.dev_spaces_back_label
import com.mochyy.mochymusic.shared.resources.shell_credit
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

private const val SourceUrl = "https://github.com/realidkroo/mochy-music"
private const val LicenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
internal fun DesktopAboutPage(navController: NavController) {
    SubSettingsScaffold(
        title = stringResource(Res.string.desktop_about_title),
        onBack = { navController.navigateUp() },
    ) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.desktop_about_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // the credit every Mochy build keeps, worded exactly as the shared shells word it
        Text(stringResource(Res.string.shell_credit), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(Res.string.desktop_about_source)) },
                    descriptionText = SourceUrl,
                    highlightKey = "Source",
                    onClick = { openUrl(SourceUrl) },
                ),
                SettingsItem(
                    title = { Text(stringResource(Res.string.desktop_about_license)) },
                    descriptionText = "GPL-3.0",
                    highlightKey = "Licence",
                    onClick = { openUrl(LicenseUrl) },
                ),
                // the phone reaches this from its profile page, which desktop doesn't have
                SettingsItem(
                    title = { Text(stringResource(Res.string.dev_spaces_back_label)) },
                    highlightKey = "Dev spaces",
                    onClick = { navController.navigate(DesktopRoutes.DeveloperSpace) { launchSingleTop = true } },
                ),
            ),
        )
        Spacer(Modifier.height(140.dp))
    }
}

// the platform's browser; a machine without one leaves the link unopened rather than failing
private fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

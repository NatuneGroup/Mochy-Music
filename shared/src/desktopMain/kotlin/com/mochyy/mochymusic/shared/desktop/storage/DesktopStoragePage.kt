// DesktopStoragePage.kt
// The shared storage page, hosted by the desktop: the page's numbers come from DesktopStoragePlatform,
// the confirm sheet it asks the app for is a plain dialog here, and the two rows whose work is
// Android's say so instead of doing nothing. Everything the page shows is the shared screen itself.

package com.mochyy.mochymusic.shared.desktop.storage

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.shared.desktop.DesktopPaths
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.cancel
import com.mochyy.mochymusic.shared.resources.desktop_storage_export_body
import com.mochyy.mochymusic.shared.resources.desktop_storage_export_title
import com.mochyy.mochymusic.shared.resources.desktop_storage_open_folder
import com.mochyy.mochymusic.shared.resources.desktop_storage_place_body
import com.mochyy.mochymusic.shared.resources.desktop_storage_place_title
import com.mochyy.mochymusic.shared.resources.import_sync_btn_ok
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageScreen
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageScreenHost
import com.mochyy.mochymusic.viewmodels.StorageKind
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File

/** what the page asked the host to confirm, and what to run when it's confirmed */
private class DesktopConfirm(
    val title: String,
    val message: String,
    val action: String,
    val onConfirm: () -> Unit,
)

/** a small explanation the page opens for rows whose work is still the Android app's */
private class DesktopNotice(val title: String, val body: String, val folder: File?)

@Composable
internal fun DesktopStoragePage(navController: NavController) {
    var confirm by remember { mutableStateOf<DesktopConfirm?>(null) }
    var notice by remember { mutableStateOf<DesktopNotice?>(null) }
    // the host is a plain callback object, so the page's strings are read here, where composition is
    val placeTitle = stringResource(Res.string.desktop_storage_place_title)
    val placeBody = stringResource(Res.string.desktop_storage_place_body)
    val exportTitle = stringResource(Res.string.desktop_storage_export_title)
    val exportBody = stringResource(Res.string.desktop_storage_export_body)

    val host = remember(placeTitle, placeBody, exportTitle, exportBody) {
        StorageScreenHost(
            // the shared page's confirm sheet is :app's popup kit; a plain dialog stands in for it here
            showConfirm = { title, message, action, onConfirm ->
                confirm = DesktopConfirm(title, message, action, onConfirm)
            },
            // desktop streams: there are no downloads to manage, and the row is disabled when the list is empty
            manageDownloads = {},
            // the folders are fixed on desktop, so the row opens them instead of offering a move
            storagePlace = { kind ->
                notice = DesktopNotice(
                    title = placeTitle,
                    body = placeBody,
                    folder = when (kind) {
                        StorageKind.Downloads -> DesktopPaths.downloads
                        StorageKind.SongCache -> DesktopPaths.songCache
                    },
                )
            },
            exportDownloads = {
                notice = DesktopNotice(title = exportTitle, body = exportBody, folder = null)
            },
        )
    }

    StorageScreen(navController = navController, host = host)

    confirm?.let { request ->
        DefaultDialog(
            onDismiss = { confirm = null },
            buttons = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(Res.string.cancel)) }
                TextButton(
                    onClick = {
                        confirm = null
                        request.onConfirm()
                    },
                ) { Text(request.action) }
            },
        ) {
            Text(request.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(request.message, style = MaterialTheme.typography.bodyLarge)
        }
    }

    notice?.let { shown ->
        DefaultDialog(
            onDismiss = { notice = null },
            buttons = {
                shown.folder?.let { folder ->
                    TextButton(
                        onClick = {
                            notice = null
                            openFolder(folder)
                        },
                    ) { Text(stringResource(Res.string.desktop_storage_open_folder)) }
                }
                TextButton(onClick = { notice = null }) { Text(stringResource(Res.string.import_sync_btn_ok)) }
            },
        ) {
            Text(shown.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(shown.body, style = MaterialTheme.typography.bodyLarge)
            shown.folder?.let { folder ->
                Spacer(Modifier.height(12.dp))
                Text(
                    folder.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Opens a folder in the platform's file manager; a machine without one simply stays put. */
private fun openFolder(folder: File) {
    runCatching {
        folder.mkdirs()
        Desktop.getDesktop().open(folder)
    }
}

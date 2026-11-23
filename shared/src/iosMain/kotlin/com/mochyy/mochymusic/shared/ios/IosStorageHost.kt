// IosStorageHost.kt
// iOS's side of the shared storage page's action sheets: the confirm dialog Android draws from its
// popup kit, and the honest answer for the two folder jobs iOS has no system picker for. Everything
// the page can really do (clear the caches, clear the covers) runs through [StoragePlatform]; this file
// only draws what the page asks the host to open.

package com.mochyy.mochymusic.shared.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.shell_coming_body
import com.mochyy.mochymusic.shared.resources.shell_coming_title
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.platformCancelLabel
import com.mochyy.mochymusic.ui.component.platformOkLabel
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageScreenHost
import org.jetbrains.compose.resources.stringResource

private class PendingConfirm(
    val title: String,
    val message: String,
    val action: String,
    val onConfirm: () -> Unit,
)

/**
 * The storage page's host for iOS. Destructive taps ask first, in the same words the page already
 * worded; taps that would move or export a folder explain that iOS keeps the app in its own sandbox
 * instead of doing nothing.
 */
@Composable
internal fun rememberIosStorageHost(): StorageScreenHost {
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var sandboxNotice by remember { mutableStateOf(false) }

    val host = remember {
        StorageScreenHost(
            showConfirm = { title, message, action, onConfirm ->
                confirm = PendingConfirm(title, message, action, onConfirm)
            },
            // no download engine on iOS: nothing to list and nothing to remove
            manageDownloads = {},
            storagePlace = { sandboxNotice = true },
            exportDownloads = { sandboxNotice = true },
            transferSection = null,
        )
    }

    confirm?.let { pending ->
        DefaultDialog(
            onDismiss = { confirm = null },
            buttons = {
                TextButton(onClick = { confirm = null }) { Text(platformCancelLabel()) }
                TextButton(
                    onClick = {
                        confirm = null
                        pending.onConfirm()
                    },
                ) { Text(pending.action) }
            },
        ) {
            Text(pending.title, style = MaterialTheme.typography.titleLarge)
            Text(
                pending.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (sandboxNotice) {
        DefaultDialog(
            onDismiss = { sandboxNotice = false },
            buttons = {
                TextButton(onClick = { sandboxNotice = false }) { Text(platformOkLabel()) }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.shell_coming_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(Res.string.shell_coming_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    return host
}

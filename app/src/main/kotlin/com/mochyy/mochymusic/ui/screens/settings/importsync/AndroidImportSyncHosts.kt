package com.mochyy.mochymusic.ui.screens.settings.importsync

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.menu.ResyncLinkDialog
import com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink
import com.mochyy.mochymusic.ui.screens.devices.showDeviceIntegration

// Android's platform half of the shared import pages: its file dialogs, its browser intent, its
// device-integration sheet, its WebView sign-in and its toasts. The pages themselves live in
// shared/.../ui/screens/settings/importsync/.

@Composable
fun AndroidImportSyncScreen(navController: NavController) {
    val overlay = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()
    val saver = rememberImportCsvSaver()
    ImportSyncScreen(
        navController = navController,
        saver = saver,
        devicesRow = {
            SettingsItem(
                title = { Text(stringResource(R.string.devices_title)) },
                highlightKey = "Device integration",
                descriptionText = stringResource(R.string.devices_row_detail),
                icon = painterResource(R.drawable.smartphone),
                iconShape = CircleShape,
                onClick = { overlay.showDeviceIntegration(devices) },
            )
        },
    )
}

@Composable
fun AndroidImportProvidersScreen(navController: NavController) {
    val picker = rememberImportFilePicker()
    val notify = rememberToast(Toast.LENGTH_SHORT)
    ImportProvidersScreen(navController = navController, picker = picker, notify = notify)
}

@Composable
fun AndroidTuneMyMusicImportScreen(navController: NavController) {
    val context = LocalContext.current
    val picker = rememberImportFilePicker()
    val notify = rememberToast(Toast.LENGTH_SHORT)
    TuneMyMusicImportScreen(
        navController = navController,
        openUrl = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        },
        picker = picker,
        notify = notify,
    )
}

@Composable
fun AndroidImportProgressScreen(navController: NavController) {
    ImportProgressScreen(navController = navController, notify = rememberToast(Toast.LENGTH_SHORT))
}

@Composable
fun AndroidAccountImportScreen(navController: NavController) {
    val signInSurface = rememberAccountSignInSurface()
    // the re-sync toast says where the run went, so it stays up longer than the other messages
    val notify = rememberToast(Toast.LENGTH_LONG)
    val notifyShort = rememberToast(Toast.LENGTH_SHORT)
    AccountImportScreen(
        navController = navController,
        signInSurface = signInSurface,
        onClearWebSession = { service -> clearWebSession(service.webOrigins) },
        resyncLinkDialog = { sourceLabel, onDismiss, onDone ->
            ResyncLinkDialog(sourceLabel = sourceLabel, onDismiss = onDismiss, onDone = onDone)
        },
        notify = notify,
        notifyShort = notifyShort,
    )
}

@Composable
private fun rememberImportCsvSaver(): ImportCsvSaver {
    var onPicked by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val callback = onPicked
        onPicked = null
        callback?.invoke(uri?.toString())
    }
    return remember(launcher) {
        ImportCsvSaver { defaultName, callback ->
            onPicked = callback
            launcher.launch(defaultName)
        }
    }
}

@Composable
private fun rememberImportFilePicker(): ImportFilePicker {
    var onPicked by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    // some file managers label .csv as text/plain or octet-stream, so don't filter too hard
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = onPicked
        onPicked = null
        callback?.invoke(uri?.toString())
    }
    return remember(launcher) {
        ImportFilePicker { _, callback ->
            onPicked = callback
            launcher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
        }
    }
}

@Composable
private fun rememberAccountSignInSurface(): AccountSignInSurface {
    return remember {
        object : AccountSignInSurface {
            @Composable
            override fun Content(page: AccountSignInPage) {
                CaptureWebView(
                    title = page.title,
                    subtitle = page.subtitle,
                    startUrl = page.service.loginUrl.orEmpty(),
                    playerOrigins = page.service.playerOrigins,
                    desktopSite = page.service.desktopSite,
                    onHeader = page.onHeader,
                    onTick = { view -> page.service.onSignInTick(view, page.onApplePageProbe, page.onCookies) },
                    onExit = page.onExit,
                )
            }
        }
    }
}

@Composable
private fun rememberToast(duration: Int): (String) -> Unit {
    val context = LocalContext.current
    return remember(context, duration) {
        { message -> Toast.makeText(context, message, duration).show() }
    }
}

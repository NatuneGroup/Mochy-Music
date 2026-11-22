// AccountSwitcherScreen.kt

package com.mochyy.mochymusic.ui.screens.settings.importsync

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.SavedAccount
import com.mochyy.mochymusic.viewmodels.AccountSwitcherViewModel

const val AccountSwitcherRoute = "youtube_accounts"

@Composable
fun AccountSwitcherScreen(
    navController: NavController,
    viewModel: AccountSwitcherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var pendingRemove by remember { mutableStateOf<SavedAccount?>(null) }

    SubSettingsScaffold(
        title = stringResource(R.string.account_switcher_title),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = stringResource(R.string.account_switcher_saved_header),
            style = SettingsGroupStyle.Grouped,
            items =
                if (state.accounts.isEmpty()) {
                    listOf(
                        SettingsItem(
                            title = { Text(stringResource(R.string.account_switcher_empty)) },
                            icon = painterResource(R.drawable.account),
                            enabled = false,
                        ),
                    )
                } else {
                    state.accounts.map { account ->
                        val active = account.id == state.activeId
                        val onSwitch: () -> Unit = { viewModel.switchTo(account) }
                        SettingsItem(
                            title = { Text(account.name.ifBlank { account.email.ifBlank { stringResource(R.string.yt_sync_signed_in) } }) },
                            descriptionText = account.email.ifBlank { null },
                            icon = painterResource(R.drawable.account),
                            iconShape = CircleShape,
                            trailingContent = {
                                if (active) {
                                    Text(
                                        stringResource(R.string.account_switcher_active),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    TextButton(onClick = { pendingRemove = account }) {
                                        Text(stringResource(R.string.account_switcher_remove))
                                    }
                                }
                            },
                            onClick = if (active) null else onSwitch,
                        )
                    }
                },
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.account_switcher_add)) },
                        descriptionText = stringResource(R.string.account_switcher_add_desc),
                        icon = painterResource(R.drawable.add),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(YouTubeLoginRoute) },
                    ),
                ),
        )
    }

    pendingRemove?.let { account ->
        DefaultDialog(
            onDismiss = { pendingRemove = null },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.account_switcher_remove_title)) },
            buttons = {
                TextButton(onClick = { pendingRemove = null }) { Text(stringResource(R.string.yt_sync_cancel)) }
                TextButton(onClick = {
                    pendingRemove = null
                    viewModel.remove(account)
                }) { Text(stringResource(R.string.account_switcher_remove)) }
            },
        ) {
            Text(
                stringResource(R.string.account_switcher_remove_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

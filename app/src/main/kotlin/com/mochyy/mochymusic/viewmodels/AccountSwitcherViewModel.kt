// AccountSwitcherViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.mochyy.mochymusic.App
import com.mochyy.mochymusic.constants.AccountChannelHandleKey
import com.mochyy.mochymusic.constants.AccountEmailKey
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.constants.DataSyncIdKey
import com.mochyy.mochymusic.constants.InnerTubeCookieKey
import com.mochyy.mochymusic.constants.SavedAccountsKey
import com.mochyy.mochymusic.constants.VisitorDataKey
import com.mochyy.mochymusic.utils.SavedAccount
import com.mochyy.mochymusic.utils.SyncUtils
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSwitcherState(
    val accounts: List<SavedAccount> = emptyList(),
    val activeId: String? = null,
)

@HiltViewModel
class AccountSwitcherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    val state: StateFlow<AccountSwitcherState> =
        context.dataStore.data
            .map { prefs -> AccountSwitcherState(SavedAccount.parse(prefs[SavedAccountsKey]), SavedAccount.fromActive(prefs)?.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountSwitcherState())

    init {
        // keeps the saved copy of whoever is signed in, so a sign-in from any screen lands in the list
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { SavedAccount.fromActive(it) }
                .distinctUntilChanged()
                .collect { active ->
                    if (active != null) context.dataStore.edit { SavedAccount.upsert(it, active) }
                }
        }
    }

    fun switchTo(account: SavedAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { it.writeActive(account) }
            // the sync reads the client's session, so set it here rather than wait for the app's collector
            runCatching {
                YouTube.cookie = account.cookie
                if (account.visitorData.isNotBlank()) YouTube.visitorData = account.visitorData
                YouTube.dataSyncId = normalizeDataSyncId(account.dataSyncId)
            }
            syncUtils.performFullSync()
        }
    }

    fun remove(account: SavedAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            val wasActive = SavedAccount.fromActive(context.dataStore.data.first())?.id == account.id
            context.dataStore.edit { prefs ->
                prefs[SavedAccountsKey] = SavedAccount.encode(SavedAccount.parse(prefs[SavedAccountsKey]).filterNot { it.id == account.id })
            }
            // removing the signed-in account signs it out, same as the sign-out row
            if (wasActive) App.forgetAccount(context)
        }
    }

    private fun MutablePreferences.writeActive(a: SavedAccount) {
        putOrRemove(InnerTubeCookieKey, a.cookie)
        putOrRemove(VisitorDataKey, a.visitorData)
        putOrRemove(DataSyncIdKey, a.dataSyncId)
        putOrRemove(AccountNameKey, a.name)
        putOrRemove(AccountEmailKey, a.email)
        putOrRemove(AccountChannelHandleKey, a.channelHandle)
    }

    // a missing value is removed rather than written blank, so the previous account's value doesn't carry over
    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String) {
        if (value.isBlank()) remove(key) else this[key] = value
    }

    // same cleanup the app applies to the data sync id before it reaches the client
    private fun normalizeDataSyncId(raw: String): String? =
        raw.takeIf { it.isNotBlank() }?.let {
            it.takeIf { !it.contains("||") } ?: it.takeIf { it.endsWith("||") }?.substringBefore("||") ?: it.substringAfter("||")
        }
}

// AndroidDiscordTokens.kt
//
// Discord's tokens, in the one settings store Android already had: the access and refresh tokens
// are SecretPreferences' sealed keys, so they are encrypted on disk exactly as before and plain
// only in memory. A platform without a sealed store supplies its own keychain instead.

package com.mochyy.mochymusic.discord

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit as editPreferences
import com.mochyy.mochymusic.constants.DiscordAccessTokenKey
import com.mochyy.mochymusic.constants.DiscordRefreshTokenKey
import com.mochyy.mochymusic.constants.DiscordTokenExpiresAtKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AndroidDiscordTokens(
    private val preferences: DataStore<Preferences>,
) : DiscordTokenStore {

    override val signedIn: Flow<Boolean> = preferences.data
        .map { it[DiscordAccessTokenKey] != null }
        .distinctUntilChanged()

    override suspend fun read(): DiscordTokens? {
        val prefs = preferences.data.first()
        val access = prefs[DiscordAccessTokenKey] ?: return null
        return DiscordTokens(
            accessToken = access,
            refreshToken = prefs[DiscordRefreshTokenKey],
            expiresAt = prefs[DiscordTokenExpiresAtKey] ?: 0L,
        )
    }

    override suspend fun save(tokens: DiscordTokens) {
        preferences.editPreferences {
            it[DiscordAccessTokenKey] = tokens.accessToken
            if (tokens.refreshToken != null) it[DiscordRefreshTokenKey] = tokens.refreshToken
            it[DiscordTokenExpiresAtKey] = tokens.expiresAt
        }
    }

    override suspend fun clear() {
        preferences.editPreferences {
            it.remove(DiscordAccessTokenKey)
            it.remove(DiscordRefreshTokenKey)
            it.remove(DiscordTokenExpiresAtKey)
        }
    }
}

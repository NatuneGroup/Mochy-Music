package com.mochyy.mochymusic.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import okio.IOException
import okio.Path.Companion.toPath

// Android uses these keys in its existing encrypted settings store.
object PlaybackPreferenceKeys {
    val showFormatBadge = booleanPreferencesKey("showFormatBadge")
    val showSampleRate = booleanPreferencesKey("showFormatSampleRate")
}

internal class PlaybackPreferences(private val store: DataStore<Preferences>) {
    val values = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { it[key] = value }
    }
}

internal const val PlaybackSettingsFile = "settings.preferences_pb"

internal fun playbackPreferences(path: () -> String): PlaybackPreferences =
    PlaybackPreferences(PreferenceDataStoreFactory.createWithPath { path().toPath() })

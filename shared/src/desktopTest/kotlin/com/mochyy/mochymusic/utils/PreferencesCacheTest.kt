package com.mochyy.mochymusic.utils

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PreferencesCacheTest {
    private val key = booleanPreferencesKey("cacheTestFlag")

    @Test
    fun readsTheSavedValueAtOnceThenFollowsWrites() = runBlocking {
        val file = File.createTempFile("mochy-prefs", ".preferences_pb").apply { delete(); deleteOnExit() }
        val store = PreferenceDataStoreFactory.createWithPath { file.absolutePath.toPath() }
        store.edit { it[key] = true }

        // what a first frame sees: the value on disk, read synchronously
        assertEquals(true, PreferencesCache.current(store)[key])

        PreferencesCache.start(store)
        assertSame(store, PreferencesCache.store)
        PreferencesCache.set(store, key, false)
        withTimeout(5_000) {
            while (PreferencesCache.snapshot.value[key] != false) delay(10)
        }
        assertEquals(false, PreferencesCache.current(store)[key])
    }
}

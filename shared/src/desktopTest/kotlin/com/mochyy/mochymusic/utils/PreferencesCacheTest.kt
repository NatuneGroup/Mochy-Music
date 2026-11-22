package com.mochyy.mochymusic.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.IOException
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreferencesCacheTest {
    private val key = booleanPreferencesKey("cacheTestFlag")

    @Test
    fun readsTheSavedValueAtOnceThenFollowsWrites() = runBlocking {
        val cache = StorePreferencesCache()
        val file = File.createTempFile("mochy-prefs", ".preferences_pb").apply { delete(); deleteOnExit() }
        val store = PreferenceDataStoreFactory.createWithPath { file.absolutePath.toPath() }
        store.edit { it[key] = true }

        // what a first frame sees: the value on disk, read synchronously
        assertEquals(true, cache.current(store)[key])
        assertTrue(cache.isWarm)

        cache.start(store)
        assertSame(store, cache.store)
        cache.set(store, key, false)
        withTimeout(5_000) {
            while (cache.snapshot.value[key] != false) delay(10)
        }
        assertEquals(false, cache.current(store)[key])
    }

    @Test
    fun anUnreadableStoreLeavesTheCacheColdInsteadOfFailing() = runBlocking {
        val cache = StorePreferencesCache()
        val store = UnreadableStore()

        // the first frame's read fails: defaults show, but the empty set isn't taken as saved data
        assertNull(cache.current(store)[key])
        assertTrue(cache.readFailed)
        assertFalse(cache.isWarm)

        // the start-up collector ends quietly; an escaped exception would fail this scope
        coroutineScope { cache.start(store, this) }
        assertTrue(cache.readFailed)
        assertFalse(cache.isWarm)

        // a write to it is dropped, not thrown, and nothing is published
        cache.set(store, key, true)
        delay(200)
        assertNull(cache.snapshot.value[key])
        assertFalse(cache.isWarm)
    }

    private class UnreadableStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("unreadable settings file") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("unwritable settings file")
    }
}

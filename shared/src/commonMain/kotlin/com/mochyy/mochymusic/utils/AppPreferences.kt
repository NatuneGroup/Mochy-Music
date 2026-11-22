// AppPreferences.kt
//
// The settings store every platform's Compose UI reads and writes. Each platform provides its one store
// at the root of its composition (Android: context.dataStore, the SecretPreferences instance; desktop and
// iOS: the store their playback preferences own) and warms PreferencesCache with it at start-up, so a
// rememberPreference reads its value synchronously and the first frame already shows it.

package com.mochyy.mochymusic.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.mochyy.mochymusic.extensions.toEnum
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/** The dispatcher the cache reads and writes the store on (Dispatchers.IO where the platform has one). */
internal expect val preferencesDispatcher: CoroutineDispatcher

/** Reads the store's current preferences, blocking the caller; used only until the cache is warm. */
internal expect fun readPreferencesBlocking(store: DataStore<Preferences>): Preferences

object PreferencesCache {

    private val snapshotState = mutableStateOf(emptyPreferences())

    @Volatile
    private var plain: Preferences = emptyPreferences()

    @Volatile
    private var warm: Boolean = false

    /** The store the platform started the cache with; compositions that provide none read this one. */
    @Volatile
    var store: DataStore<Preferences>? = null
        private set

    private val writeScope = CoroutineScope(SupervisorJob() + preferencesDispatcher)

    val snapshot: State<Preferences> get() = snapshotState

    /** Keeps the cache following [store] for as long as [scope] lives; a second start for the same store does nothing. */
    fun start(store: DataStore<Preferences>, scope: CoroutineScope = writeScope) {
        if (this.store === store) return
        this.store = store
        scope.launch(preferencesDispatcher, start = CoroutineStart.UNDISPATCHED) {
            store.data.collect { prefs ->
                plain = prefs
                warm = true
                snapshotState.value = prefs
            }
        }
    }

    fun current(store: DataStore<Preferences>): Preferences {
        if (warm) return plain
        val prefs = readPreferencesBlocking(store)
        plain = prefs
        warm = true
        snapshotState.value = prefs
        return prefs
    }

    fun <T> set(store: DataStore<Preferences>, key: Preferences.Key<T>, value: T) {

        writeScope.launch {
            store.edit { it[key] = value }
        }
    }
}

/** The app's settings store for this composition. */
val LocalAppPreferences: ProvidableCompositionLocal<DataStore<Preferences>> = staticCompositionLocalOf {
    PreferencesCache.store ?: error("No app preferences: provide LocalAppPreferences or start PreferencesCache")
}

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalAppPreferences.current

    PreferencesCache.current(store)

    val valueState = remember(key, defaultValue) {
        derivedStateOf { PreferencesCache.snapshot.value[key] ?: defaultValue }
    }

    return remember(key, store) {
        object : MutableState<T> {
            override var value: T
                get() = valueState.value
                set(value) = PreferencesCache.set(store, key, value)

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalAppPreferences.current
    PreferencesCache.current(store)

    val valueState = remember(key, defaultValue) {
        derivedStateOf { PreferencesCache.snapshot.value[key].toEnum(defaultValue) }
    }

    return remember(key, store) {
        object : MutableState<T> {
            override var value: T
                get() = valueState.value
                set(value) = PreferencesCache.set(store, key, value.name)

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

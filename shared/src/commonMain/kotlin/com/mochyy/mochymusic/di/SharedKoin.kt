package com.mochyy.mochymusic.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.db.MochyDatabase
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceRepository
import com.mochyy.mochymusic.viewmodels.ArtistAlbumsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import org.koin.core.context.startKoin

/**
 * What shared ViewModels are given, on every platform. The platform hands in its one music database and
 * its one settings store (Android: Hilt's InternalDatabase singleton and context.dataStore, the sealed
 * SecretPreferences instance), so Koin never opens a second of either; it only passes them on.
 * Both are asked for on first use, so a platform that has no shared screen yet never opens them.
 *
 * [devSpace] is the platform's dev spaces fetcher (Android: AppDevSpaceRepository, which needs
 * BuildConfig and a JVM HTTP stack). Null where the dev spaces page isn't reachable, so no platform
 * has to fake one.
 */
fun appModule(
    database: () -> MochyDatabase,
    preferences: () -> DataStore<Preferences>,
    devSpace: (() -> DevSpaceRepository)? = null,
): Module = module {
    single<MochyDatabase> { database() }
    // the one DataStore in the graph, so it needs no qualifier
    single<DataStore<Preferences>> { preferences() }

    if (devSpace != null) {
        val factory = devSpace
        single<DevSpaceRepository> { factory() }
    }

    viewModelOf(::ArtistAlbumsViewModel)
}

/** Starts Koin once per process with [appModule]; a second call (iOS can build its view controller again) does nothing. */
fun startMochyKoin(
    database: () -> MochyDatabase,
    preferences: () -> DataStore<Preferences>,
    devSpace: (() -> DevSpaceRepository)? = null,
) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin { modules(appModule(database, preferences, devSpace)) }
}

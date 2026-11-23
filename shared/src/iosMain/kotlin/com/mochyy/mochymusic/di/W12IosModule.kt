// W12IosModule.kt
// The bindings iOS has to add to the shared graph: the platform halves of the shared settings pages
// that exist on iOS. Common modules stay in :shared's appModule; this file is the iOS-only part, so
// desktop and Android never see an NSFileManager behind a shared screen's contract.

package com.mochyy.mochymusic.di

import com.mochyy.mochymusic.shared.ios.IosDevSpaceRepository
import com.mochyy.mochymusic.shared.ios.IosStoragePlatform
import com.mochyy.mochymusic.shared.ios.iosDatabase
import com.mochyy.mochymusic.shared.ios.iosPlaybackPreferences
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceRepository
import com.mochyy.mochymusic.viewmodels.StoragePlatform
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * What iOS binds on top of [appModule] and the shared modules.
 *
 * Storage and the dev spaces page are the two shared pages whose platform half iOS can run today.
 * The import, scrobble and cloud-backup pages need work that is still :app's (its import engine, its
 * last.fm client and its zipping, its OAuth browsers and WorkManager schedulers), so iOS leaves those
 * contracts unbound and opens those routes with an honest page instead of a screen with dead rows.
 */
val w12IosModule = module {
    single<StoragePlatform> { IosStoragePlatform() }
    single<DevSpaceRepository> { IosDevSpaceRepository() }
}

private var iosBindingsLoaded = false

/**
 * Starts the shared graph and adds [w12IosModule] once per process. [startMochyKoin] itself is a
 * no-op after the first call (Swift can build the view controller twice), so the guard here does the
 * same for the iOS bindings, which a second `loadKoinModules` would reject as duplicates.
 */
fun startMochyKoinOnIos() {
    startMochyKoin(
        database = { iosDatabase },
        preferences = { iosPlaybackPreferences.store },
    )
    if (iosBindingsLoaded) return
    loadKoinModules(w12IosModule)
    iosBindingsLoaded = true
}

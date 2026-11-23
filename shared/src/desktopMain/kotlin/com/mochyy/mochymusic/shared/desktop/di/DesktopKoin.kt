// DesktopKoin.kt
// What only a desktop can hand the shared screens: the storage page's numbers, measured over the
// folders DesktopPaths names. The shared ViewModels are bound in the shared di modules; this file only
// adds the platform half, the way Android's di/koin files add theirs. Loaded by runDesktopApp right
// after startMochyKoin, before the first composition asks for a view model.

package com.mochyy.mochymusic.shared.desktop.di

import com.mochyy.mochymusic.shared.desktop.devspace.DesktopDevSpaceRepository
import com.mochyy.mochymusic.shared.desktop.storage.DesktopStoragePlatform
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceRepository
import com.mochyy.mochymusic.viewmodels.StoragePlatform
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/** The desktop's platform bindings, one per interface a shared screen asks Koin for. */
val w12DesktopModule: Module = module {
    single<StoragePlatform> { DesktopStoragePlatform() }
    // the dev spaces page and its playground share one set of GitHub numbers, as on Android
    single<DevSpaceRepository> { DesktopDevSpaceRepository() }
}

/**
 * Adds [w12DesktopModule] to the graph startMochyKoin opened. Called once from runDesktopApp; a second
 * call would add the same definitions again, so the entry point owns the single call.
 */
internal fun startDesktopKoin() {
    loadKoinModules(w12DesktopModule)
}

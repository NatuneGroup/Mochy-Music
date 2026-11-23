package com.mochyy.mochymusic.di

import com.mochyy.mochymusic.utils.AndroidArtistPageSource
import com.mochyy.mochymusic.utils.ArtistPageSource
import com.mochyy.mochymusic.viewmodels.ArtistSimilarViewModel
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * What only Android can hand the shared ViewModels: ArtistSimilarViewModel reads artist pages
 * through the YouTube facade, which lives in innertube's jvmSharedMain and not in commonMain.
 * Loaded right after startMochyKoin in App.onCreate, before any composition asks for the view model.
 */
fun loadAndroidKoin() {
    loadKoinModules(
        module {
            single<ArtistPageSource> { AndroidArtistPageSource }
            viewModelOf(::ArtistSimilarViewModel)
        }
    )
}

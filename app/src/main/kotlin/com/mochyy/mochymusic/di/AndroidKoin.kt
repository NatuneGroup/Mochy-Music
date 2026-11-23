package com.mochyy.mochymusic.di

import com.mochyy.mochymusic.di.koin.playerTvModule
import com.mochyy.mochymusic.ui.screens.settings.devspace.AppDevSpaceRepository
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceRepository
import com.mochyy.mochymusic.utils.AndroidArtistPageSource
import com.mochyy.mochymusic.utils.ArtistPageSource
import com.mochyy.mochymusic.viewmodels.ArtistSimilarViewModel
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * What only Android can hand the shared code: ArtistSimilarViewModel reads artist pages through the
 * YouTube facade, which lives in innertube's jvmSharedMain and not in commonMain, and the dev spaces
 * page reads its numbers through AppDevSpaceRepository (HttpURLConnection, org.json, BuildConfig).
 * Every Android-only binding goes here, so the shared appModule keeps one shape on every platform.
 * Loaded right after startMochyKoin in App.onCreate, before any composition asks for the view model.
 */
fun loadAndroidKoin() {
    loadKoinModules(playerTvModule)
    loadKoinModules(
        module {
            single<ArtistPageSource> { AndroidArtistPageSource }
            single<DevSpaceRepository> { AppDevSpaceRepository }
            viewModelOf(::ArtistSimilarViewModel)
        }
    )
}

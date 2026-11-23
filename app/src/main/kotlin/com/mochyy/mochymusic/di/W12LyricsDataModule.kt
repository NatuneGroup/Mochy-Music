// W12LyricsDataModule.kt
//
// Hilt's side of the moved lyrics pipeline: the Android source catalogue, the shared LyricsHelper
// built over it, and the platform halves the shared code asks for.

package com.mochyy.mochymusic.di

import android.content.Context
import com.mochyy.mochymusic.lyrics.AndroidLyricsFileReader
import com.mochyy.mochymusic.lyrics.AndroidLyricsNetwork
import com.mochyy.mochymusic.lyrics.LyricsHelper
import com.mochyy.mochymusic.lyrics.LyricsProviderCatalog
import com.mochyy.mochymusic.lyrics.LyricsProviderRegistry
import com.mochyy.mochymusic.lyrics.PaxSenixLyricsProvider
import com.mochyy.mochymusic.utils.NetworkConnectivityObserver
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.reportException
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object W12LyricsDataModule {

    @Provides
    @Singleton
    fun provideLyricsProviderCatalog(@ApplicationContext context: Context): LyricsProviderCatalog {
        // Paxsenix needs the app version for its user agent before its first lookup
        PaxSenixLyricsProvider.initialize(context)
        return LyricsProviderRegistry
    }

    // unscoped, as the @Inject constructor was: every injection site keeps its own in-memory cache
    @Provides
    fun provideLyricsHelper(
        @ApplicationContext context: Context,
        catalog: LyricsProviderCatalog,
        networkConnectivity: NetworkConnectivityObserver,
    ): LyricsHelper = LyricsHelper(
        catalog = catalog,
        // the one sealed settings store, so the shared helper reads the same keys the app always has
        preferences = context.dataStore,
        network = AndroidLyricsNetwork(networkConnectivity),
        localLyrics = AndroidLyricsFileReader(context),
        reportError = { reportException(it) },
    )
}

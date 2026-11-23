// W12ServicesModule.kt
//
// The Hilt bindings the moved services need. The scrobbling, the Discord status and the AI
// requests themselves live in :shared now and are plain classes there; this module hands each
// one its Android side — the settings store, the queue file, WorkManager, the toast — and keeps
// them singletons, exactly as the @Singleton classes they replaced were.

package com.mochyy.mochymusic.di

import android.content.Context
import com.mochyy.mochymusic.scrobble.AndroidScrobbleQueueStore
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.scrobble.Scrobbler
import com.mochyy.mochymusic.scrobble.ToastScrobbleNotices
import com.mochyy.mochymusic.scrobble.WorkManagerScrobbleRetry
import com.mochyy.mochymusic.utils.OfflineMode
import com.mochyy.mochymusic.utils.dataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object W12ServicesModule {

    @Provides
    @Singleton
    fun scrobbler(@ApplicationContext context: Context): Scrobbler = Scrobbler(
        preferences = context.dataStore,
        store = AndroidScrobbleQueueStore(context),
        accounts = ScrobbleSite.entries,
        isOffline = { OfflineMode.isOn },
        retry = WorkManagerScrobbleRetry(context),
        notices = ToastScrobbleNotices(context),
    )
}

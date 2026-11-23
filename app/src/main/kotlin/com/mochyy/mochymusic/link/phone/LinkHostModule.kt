// LinkHostModule.kt

package com.mochyy.mochymusic.link.phone

import android.content.Context
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.di.ApplicationScope
import com.mochyy.mochymusic.link.LinkBackground
import com.mochyy.mochymusic.link.LinkControls
import com.mochyy.mochymusic.link.LinkHostProfile
import com.mochyy.mochymusic.link.LinkKeySealer
import com.mochyy.mochymusic.link.LinkLibrary
import com.mochyy.mochymusic.link.LinkNotices
import com.mochyy.mochymusic.playback.DownloadUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

// What the shared link host is given on Android. The host itself lives in :shared, so Hilt builds it
// from the app's own database, player, profile, notifications and foreground service.
@Module
@InstallIn(SingletonComponent::class)
object LinkHostModule {
    @Provides
    @Singleton
    fun provideLinkLibrary(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        internalDatabase: InternalDatabase,
        downloadUtil: DownloadUtil,
    ): LinkLibrary = AndroidLinkLibrary(context, database, internalDatabase, downloadUtil)

    @Provides
    @Singleton
    fun provideLinkControls(@ApplicationContext context: Context): LinkControls = AndroidLinkControls(context)

    @Provides
    @Singleton
    fun provideLinkHostProfile(@ApplicationContext context: Context): LinkHostProfile = AndroidLinkProfile(context)

    @Provides
    @Singleton
    fun provideLinkNotices(@ApplicationContext context: Context): LinkNotices = AndroidLinkNotices(context)

    @Provides
    @Singleton
    fun provideLinkBackground(@ApplicationContext context: Context): LinkBackground = AndroidLinkBackground(context)

    @Provides
    @Singleton
    fun provideLinkKeySealer(): LinkKeySealer = SecretVaultLinkKeySealer()

    @Provides
    @Singleton
    fun providePhoneLinkHost(
        @ApplicationContext context: Context,
        library: LinkLibrary,
        controls: LinkControls,
        profile: LinkHostProfile,
        notices: LinkNotices,
        background: LinkBackground,
        sealer: LinkKeySealer,
        @ApplicationScope scope: CoroutineScope,
    ): PhoneLinkHost = PhoneLinkHost(
        platformContext = context,
        library = library,
        controls = controls,
        profile = profile,
        notices = notices,
        background = background,
        sealer = sealer,
        scope = scope,
    )

    @Provides
    @Singleton
    fun providePhonePairing(
        @ApplicationContext context: Context,
        host: PhoneLinkHost,
    ): PhonePairing = PhonePairing(context, host)
}

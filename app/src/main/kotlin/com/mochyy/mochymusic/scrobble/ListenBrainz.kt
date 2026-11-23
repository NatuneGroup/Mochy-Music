// ListenBrainz.kt
//
// The player's way in to ListenBrainz: MusicService hands it the playing song, and this turns it
// into the shared ListenBrainzEngine's track and passes it on. The submits, the token and the
// timing rules are in :shared (scrobble/ListenBrainzEngine.kt).

package com.mochyy.mochymusic.scrobble

import android.content.Context
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.OfflineMode
import com.mochyy.mochymusic.utils.dataStore

object ListenBrainz {
    // built on the first song, from the app's one settings store
    @Volatile private var engine: ListenBrainzEngine? = null

    private fun engineOf(context: Context): ListenBrainzEngine =
        engine ?: ListenBrainzEngine(
            preferences = context.applicationContext.dataStore,
            isOffline = { OfflineMode.isOn },
            applicationId = BuildConfig.APPLICATION_ID,
        ).also { engine = it }

    fun onSongStart(context: Context, metadata: MediaMetadata?, duration: Long, playing: Boolean) =
        engineOf(context).onSongStart(metadata?.toListenBrainzTrack(), duration, playing)

    fun onSongChanged(context: Context, ended: Boolean) =
        engineOf(context).onSongChanged(ended)

    fun onPlaybackEnded(context: Context) =
        engineOf(context).onPlaybackEnded()

    fun onServiceDestroyed(context: Context) =
        engineOf(context).onServiceDestroyed()

    fun onPlayingChanged(context: Context, metadata: MediaMetadata?, duration: Long, playing: Boolean) =
        engineOf(context).onPlayingChanged(metadata?.toListenBrainzTrack(), duration, playing)

    fun reset() {
        engine?.reset()
    }
}

private fun MediaMetadata.toListenBrainzTrack() = ListenBrainzTrack(
    id = id,
    title = title,
    artists = artists.map { it.name },
    albumTitle = album?.title,
)

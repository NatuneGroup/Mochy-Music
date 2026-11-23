// AndroidLyricsPlatform.kt
//
// Android's half of the shared lyrics pipeline: what "online" means here (the offline switch plus
// real connectivity) and how an imported file's own lyrics are read (content resolver).

package com.mochyy.mochymusic.lyrics

import android.content.Context
import android.net.Uri
import com.mochyy.mochymusic.utils.NetworkConnectivityObserver
import com.mochyy.mochymusic.utils.OfflineMode

/** The offline switch and the connectivity callback, read together the way LyricsHelper always did. */
class AndroidLyricsNetwork(
    private val networkConnectivity: NetworkConnectivityObserver,
) : LyricsNetworkStatus {
    override suspend fun isOnline(): Boolean {
        if (OfflineMode.isOn) return false
        return try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
    }
}

/** An imported file's own lyrics, through the same content resolver read EmbeddedLyricsFile always did. */
class AndroidLyricsFileReader(
    private val context: Context,
) : LyricsFileReader {
    override suspend fun read(uri: String): String? = readEmbeddedLyrics(context, Uri.parse(uri))
}

// ScrobbleAccounts.kt
//
// What the shared scrobbler needs to know about one service (Last.fm, Libre.fm): its settings
// keys, the name the queue files plays under, and the API client. The app's ScrobbleSite enum
// implements it, so its keys, labels and clients stay exactly where they were.

package com.mochyy.mochymusic.scrobble

import androidx.datastore.preferences.core.Preferences
import com.music.lastfm.Audioscrobbler

/** One service Mochy scrobbles to, in the parts the shared scrobbler uses. */
interface ScrobbleAccount {
    /** The service's own name (LASTFM, LIBREFM): what the queue file stores for it. */
    val siteName: String

    val label: String

    val sessionKey: Preferences.Key<String>
    val usernameKey: Preferences.Key<String>

    /** Scrobbling on or paused, with the account kept either way. */
    val enabledKey: Preferences.Key<Boolean>

    val api: Audioscrobbler
}

/**
 * A play that couldn't go out now: the platform books a retry (Android with WorkManager, which
 * survives the app closing), and calls [send] again when it runs.
 */
fun interface ScrobbleRetry {
    fun sendLater()
}

/** Told when a service took Mochy's access back, so the user hears about it. */
fun interface ScrobbleNotices {
    fun signedOut(label: String)
}

// ScrobbleSite.kt

package com.mochyy.mochymusic.scrobble

import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.EnableLastFMScrobblingKey
import com.mochyy.mochymusic.constants.EnableLibreFmScrobblingKey
import com.mochyy.mochymusic.constants.LastFMSessionKey
import com.mochyy.mochymusic.constants.LastFMUsernameKey
import com.mochyy.mochymusic.constants.LibreFmSessionKey
import com.mochyy.mochymusic.constants.LibreFmUsernameKey
import com.music.lastfm.Audioscrobbler

/**
 * The services Mochy scrobbles to. Both speak Last.fm's API, so one client serves both. Their
 * API keys come from local.properties (or CI secrets) at build time and never sit in the source.
 *
 * The enum stays in the app because it builds the clients from those build-time keys; the
 * scrobbling itself is shared (`com.mochyy.mochymusic.scrobble.Scrobbler`), which is why this
 * implements [ScrobbleAccount].
 */
enum class ScrobbleSite(
    override val label: String,
    override val sessionKey: Preferences.Key<String>,
    override val usernameKey: Preferences.Key<String>,
    /** Scrobbling on or paused, with the account kept either way. */
    override val enabledKey: Preferences.Key<Boolean>,
    private val profilePage: String,
    /** where someone without an account makes one */
    val signUpUrl: String,
    override val api: Audioscrobbler,
) : ScrobbleAccount {
    LASTFM(
        label = "Last.fm",
        sessionKey = LastFMSessionKey,
        usernameKey = LastFMUsernameKey,
        enabledKey = EnableLastFMScrobblingKey,
        profilePage = "https://www.last.fm/user/",
        signUpUrl = "https://www.last.fm/join",
        api = Audioscrobbler(
            apiRoot = "https://ws.audioscrobbler.com/2.0/",
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        ),
    ),
    LIBREFM(
        label = "Libre.fm",
        sessionKey = LibreFmSessionKey,
        usernameKey = LibreFmUsernameKey,
        enabledKey = EnableLibreFmScrobblingKey,
        profilePage = "https://libre.fm/user/",
        signUpUrl = "https://libre.fm/register.php",
        api = Audioscrobbler(
            apiRoot = "https://libre.fm/2.0/",
            apiKey = BuildConfig.LIBREFM_API_KEY,
            secret = BuildConfig.LIBREFM_SECRET,
            signsInWithToken = true,
        ),
    );

    /** the enum's name, which is what the queue file has always stored */
    override val siteName: String get() = name

    fun profileUrl(username: String) = profilePage + username
}

// LinkHostSeams.kt

package com.mochyy.mochymusic.link.phone

import android.content.Context
import androidx.room.InvalidationTracker
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.constants.UsernameKey
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.link.LinkBackground
import com.mochyy.mochymusic.link.LinkControls
import com.mochyy.mochymusic.link.LinkControlRoutes
import com.mochyy.mochymusic.link.LinkHostProfile
import com.mochyy.mochymusic.link.LinkKeySealer
import com.mochyy.mochymusic.link.LinkLibrary
import com.mochyy.mochymusic.link.LinkNotices
import com.mochyy.mochymusic.link.LinkPlayer
import com.mochyy.mochymusic.link.LinkProfileSnapshot
import com.mochyy.mochymusic.link.LinkRequest
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.playback.DownloadUtil
import com.mochyy.mochymusic.utils.SecretVault
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

// The Android side of the shared link host: the library database, the open player, the person's
// profile, the notifications and the foreground service the host asks for.

internal class SecretVaultLinkKeySealer : LinkKeySealer {
    override fun seal(text: String): String = SecretVault.seal(text)

    override fun open(stored: String): String? = SecretVault.open(stored)
}

internal class AndroidLinkProfile(private val context: Context) : LinkHostProfile {
    override fun snapshot(): LinkProfileSnapshot {
        val prefs = runBlocking { context.dataStore.data.first() }
        val path = prefs[ProfilePicUriKey].orEmpty()
        val file = File(path.removePrefix("file://")).takeIf { path.isNotBlank() && it.isFile }
        return LinkProfileSnapshot(
            userName = prefs[UsernameKey].orEmpty(),
            hasAvatar = path.isNotBlank(),
            avatarStamp = file?.let { "${it.length()}:${it.lastModified()}" } ?: "",
        )
    }

    override fun avatarBytes(): ByteArray? {
        val path = runBlocking { context.dataStore.data.first()[ProfilePicUriKey] }.orEmpty()
        val file = File(path.removePrefix("file://")).takeIf { path.isNotBlank() && it.isFile } ?: return null
        return file.readBytes()
    }

    override fun appVersion(): String = BuildConfig.VERSION_NAME
}

internal class AndroidLinkLibrary(
    context: Context,
    database: MusicDatabase,
    private val internalDatabase: InternalDatabase,
    downloadUtil: DownloadUtil,
) : LinkLibrary {
    private val routes = PhoneLibraryRoutes(context, database, internalDatabase, downloadUtil)

    override fun snapshot(): LinkResponse = routes.snapshot()

    override fun files(): String = routes.files().toString()

    override fun file(request: LinkRequest, songId: String): LinkResponse = routes.file(request, songId)

    override fun applyChanges(changes: String) = routes.applyChanges(JSONObject(changes))

    override fun mark(): String = routes.mark()

    // only the tables a tv shows count, a new lyric or format alone isn't worth a whole sync
    override fun watch(onChange: () -> Unit) {
        internalDatabase.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(
                arrayOf("song", "artist", "album", "playlist", "playlist_song_map", "event"),
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    onChange()
                }
            },
        )
    }
}

internal class AndroidLinkControls(context: Context) : LinkControls {
    private val routes = LinkControlRoutes(context)

    // a quiet stand-in from android auto counts too, exactly as LinkPlayer.current() decides it
    override fun handle(request: LinkRequest): LinkResponse? =
        LinkPlayer.current()?.let { routes.handle(request, it) }
}

internal class AndroidLinkNotices(private val context: Context) : LinkNotices {
    override fun connected(deviceName: String) = PhoneLinkNotices.connected(context, deviceName)

    override fun synced(deviceName: String) = PhoneLinkNotices.synced(context, deviceName)
}

internal class AndroidLinkBackground(private val context: Context) : LinkBackground {
    override fun start() = PhoneLinkService.start(context)

    override fun stop() = PhoneLinkService.stop(context)
}

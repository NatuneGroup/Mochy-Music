// LinkHostPlatform.kt

package com.mochyy.mochymusic.link

// What hosting the device link needs from the platform it runs on. Android wraps its library
// database, player and notifications; the desktop app supplies its own, so the host logic itself
// (pairing, heartbeats, routes, the paired list) lives once in shared code.

// the local library a paired tv may copy, read files from and write plays back into
interface LinkLibrary {
    // the whole music database, copied and squeezed the way /library has always sent it
    fun snapshot(): LinkResponse

    // every song this device can play without the internet, as the /files json array text
    fun files(): String

    // one song's bytes, in the range the caller asked for
    fun file(request: LinkRequest, songId: String): LinkResponse

    // plays, likes and library edits made on the other device
    fun applyChanges(changes: String)

    // a short sum of what a tv shows, so it only pulls the library when something visible changed
    fun mark(): String

    // told whenever the library changes; called once per host
    fun watch(onChange: () -> Unit)
}

// the open player, for a paired phone that plays, skips or changes something here.
// null when no screen is up, which the host answers with 409 as it always has.
interface LinkControls {
    fun handle(request: LinkRequest): LinkResponse?
}

// who this device is in the link, and the picture it hands its tvs
data class LinkProfileSnapshot(
    // the person's name set in the app, empty when there is none
    val userName: String,
    // true while a profile picture is set, even if its file has gone missing since
    val hasAvatar: Boolean,
    // which picture the app has right now, so a tv fetches it again only when it changed; empty with no picture
    val avatarStamp: String,
)

interface LinkHostProfile {
    // all three read from the one settings read, as a hello has always done
    fun snapshot(): LinkProfileSnapshot

    // the picture itself, null when there is none
    fun avatarBytes(): ByteArray?

    // the app's version, sent in every hello
    fun appVersion(): String
}

// "your tv and phone are in step", shown as a banner or a quiet notification
interface LinkNotices {
    fun connected(deviceName: String)

    fun synced(deviceName: String)
}

// keeping this device reachable while the app is closed: a foreground service on android, a
// tray app on the desktop; the host only asks for start and stop
interface LinkBackground {
    fun start()

    fun stop()
}

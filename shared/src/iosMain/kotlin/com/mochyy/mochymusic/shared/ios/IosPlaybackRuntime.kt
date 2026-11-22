package com.mochyy.mochymusic.shared.ios

import com.mochyy.mochymusic.shared.lyrics.playerLyricsProviders
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.playback.QueueTransport

/** One player and one library shared by the phone UI and CarPlay scenes. */
internal object IosPlaybackRuntime {
    val youtube by lazy { IosYouTubeClient() }
    val library by lazy { iosLibrary }
    var playbackFailedText: String = "Playback failed."
    /** The phone UI's queue owner; system next/previous go through it. */
    val queueTransport = QueueTransport()

    val player by lazy { IosPlayerController(youtube, queueTransport) { playbackFailedText } }
    val lyricsProviders: List<LyricsProvider> by lazy { playerLyricsProviders(youtube.http) }
}

package com.mochyy.mochymusic.shared.ios

import com.mochyy.mochymusic.shared.lyrics.playerLyricsProviders
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider

/** One player and one library shared by the phone UI and CarPlay scenes. */
internal object IosPlaybackRuntime {
    val youtube by lazy { IosYouTubeClient() }
    val library by lazy { iosLibrary }
    var playbackFailedText: String = "Playback failed."

    val player by lazy { IosPlayerController(youtube) { playbackFailedText } }
    val lyricsProviders: List<LyricsProvider> by lazy { playerLyricsProviders(youtube.http) }
}

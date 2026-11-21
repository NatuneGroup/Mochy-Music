// PlayerController.kt
// What the shared UI knows about playback, on any platform. Each platform's engine implements it:
// desktop with FFmpeg and Java Sound, iOS with AVFoundation; Android keeps its MusicService.

package com.mochyy.mochymusic.shared.playback

import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.StateFlow

/** The codec and rate of what is actually playing, for Mochy's format badge. */
data class StreamFormat(
    /** "OPUS", "AAC", "FLAC"... as the decoder names it */
    val codec: String,
    /** samples per second the decoder is given, 0 when not known yet */
    val sampleRate: Int = 0,
    /** bits per second of the source stream, 0 when the source doesn't say */
    val bitrate: Int = 0,
)

data class PlayerState(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    /** a stream is being resolved or opened: the play button shows progress */
    val isLoading: Boolean = false,
    val hasEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val format: StreamFormat? = null,
    /** why the last song couldn't play, shown under the title until the next one starts */
    val error: String? = null,
)

interface PlayerController {
    val state: StateFlow<PlayerState>

    /** Resolves [song]'s stream and plays it, replacing whatever was playing. */
    fun play(song: SongItem)

    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    fun release()
}

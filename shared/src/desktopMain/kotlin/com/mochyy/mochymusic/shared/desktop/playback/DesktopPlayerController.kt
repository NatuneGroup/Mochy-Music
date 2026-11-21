// DesktopPlayerController.kt
// The desktop PlayerController: InnerTubeX finds the stream, DesktopAudioEngine plays it, and the state
// the shared UI reads is published as a StateFlow.

package com.mochyy.mochymusic.shared.desktop.playback

import com.mochyy.mochymusic.shared.playback.PlayerController
import com.mochyy.mochymusic.shared.playback.PlayerState
import com.mochyy.mochymusic.shared.playback.StreamFormat
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

class DesktopPlayerController(
    private val resolver: DesktopStreamResolver = DesktopStreamResolver(),
    private val playbackFailedText: () -> String,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    @Volatile private var engine: DesktopAudioEngine? = null
    private var loadJob: Job? = null
    private var tickJob: Job? = null

    override fun play(song: SongItem) {
        loadJob?.cancel()
        tickJob?.cancel()
        // the old engine closes off the caller's thread: joining its decode thread can take a moment
        engine?.let { old -> scope.launch { old.close() } }
        engine = null
        _state.value = PlayerState(song = song, isLoading = true, durationMs = (song.duration ?: 0) * 1000L)
        loadJob = scope.launch {
            try {
                val stream = resolver.resolve(song.id) ?: error("no playable stream")
                val next = DesktopAudioEngine(
                    onEnded = { _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) } },
                    onError = { fail(song) },
                )
                val opened = runInterruptible { next.open(stream.url, stream.headers) }
                if (!isActive) {
                    next.close()
                    return@launch
                }
                engine = next
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = true,
                        durationMs = opened.durationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                        format = StreamFormat(codec = opened.codec, sampleRate = opened.sampleRate, bitrate = stream.bitrate),
                    )
                }
                tickJob = scope.launch { tick(next) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                fail(song)
            }
        }
    }

    private fun fail(song: SongItem) {
        if (_state.value.song?.id != song.id) return
        _state.update { it.copy(isLoading = false, isPlaying = false, error = playbackFailedText()) }
    }

    private suspend fun tick(current: DesktopAudioEngine) {
        while (scope.isActive && engine === current) {
            val position = current.positionMs
            _state.update { if (it.isPlaying) it.copy(positionMs = position.coerceAtMost(it.durationMs.takeIf { d -> d > 0 } ?: position)) else it }
            delay(250)
        }
    }

    override fun togglePlayPause() {
        val current = engine ?: return
        val playing = _state.value.isPlaying
        if (playing) current.pause() else {
            // a finished song starts over from the top
            val s = _state.value
            if (s.durationMs > 0 && s.positionMs >= s.durationMs) current.seekTo(0)
            current.resume()
        }
        _state.update { it.copy(isPlaying = !playing) }
    }

    override fun seekTo(positionMs: Long) {
        val current = engine ?: return
        current.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun release() {
        loadJob?.cancel()
        engine?.close()
        engine = null
        scope.cancel()
    }
}

// The desktop PlayerController: InnerTubeX resolves streams and FFmpeg/Java Sound play them.
package com.mochyy.mochymusic.shared.desktop.playback

import com.mochyy.mochymusic.shared.playback.PlayerController
import com.mochyy.mochymusic.shared.playback.PlayerState
import com.mochyy.mochymusic.shared.playback.StreamFormat
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPlayerController(
    private val resolver: DesktopStreamResolver = DesktopStreamResolver(),
    private val playbackFailedText: () -> String,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerState())
    override val state = mutableState.asStateFlow()

    private var engine: DesktopAudioEngine? = null
    private var loadJob: Job? = null
    private var generation = 0L

    override fun play(song: SongItem) {
        val request = ++generation
        loadJob?.cancel()
        retireEngine()
        mutableState.value = PlayerState(song = song, isLoading = true, durationMs = (song.duration ?: 0) * 1000L)
        loadJob = scope.launch {
            val next = DesktopAudioEngine(
                onEnded = {
                    scope.launch {
                        if (generation == request) {
                            retireEngine()
                            val current = state.value
                            mutableState.value = current.copy(
                                isPlaying = false, hasEnded = true, positionMs = current.durationMs,
                            )
                        }
                    }
                },
                onError = { scope.launch { if (generation == request) failPlayback() } },
            )
            var adopted = false
            try {
                val stream = checkNotNull(resolver.resolve(song.id))
                val opened = withContext(Dispatchers.IO) { next.open(stream.url, stream.headers) }
                engine = next
                adopted = true
                mutableState.value = state.value.copy(
                    isLoading = false,
                    isPlaying = true,
                    durationMs = opened.durationMs.takeIf { it > 0 } ?: state.value.durationMs,
                    format = StreamFormat(opened.codec, opened.sampleRate, stream.bitrate),
                )
                next.start()
                while (engine === next) {
                    val current = state.value
                    if (current.isPlaying) {
                        val position = next.positionMs
                        mutableState.value = current.copy(
                            positionMs = position.coerceAtMost(current.durationMs.takeIf { it > 0 } ?: position),
                        )
                    }
                    delay(250)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == request) failPlayback()
            } finally {
                if (!adopted) withContext(NonCancellable + Dispatchers.IO) { next.close() }
            }
        }
    }

    override fun togglePlayPause() {
        val current = state.value
        val active = engine
        if (active == null) {
            if (current.hasEnded || current.error != null) current.song?.let(::play)
            return
        }
        if (current.isPlaying) active.pause() else active.resume()
        mutableState.value = current.copy(isPlaying = !current.isPlaying)
    }

    override fun seekTo(positionMs: Long) {
        val active = engine ?: return
        val current = state.value
        val target = positionMs.coerceIn(0L, current.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        active.seekTo(target)
        mutableState.value = current.copy(positionMs = target)
    }

    override fun release() {
        generation++
        retireEngine()
        scope.cancel()
    }

    private fun failPlayback() {
        retireEngine()
        mutableState.value = state.value.copy(isLoading = false, isPlaying = false, error = playbackFailedText())
    }

    private fun retireEngine() {
        val active = engine ?: return
        engine = null
        scope.launch(NonCancellable + Dispatchers.IO) { active.close() }
    }
}

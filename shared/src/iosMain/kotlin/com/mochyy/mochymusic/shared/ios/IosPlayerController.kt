// AVPlayer integration follows Metrolist's iOS MusicPlayer (GPL-3.0).
package com.mochyy.mochymusic.shared.ios

import com.mochyy.mochymusic.shared.playback.PlayerController
import com.mochyy.mochymusic.shared.playback.PlayerState
import com.mochyy.mochymusic.shared.playback.StreamFormat
import com.music.innertube.models.SongItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusUnknown
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.status
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

/** Owned by the Compose host. Playback actions and AVPlayer state are confined to the main thread. */
@OptIn(ExperimentalForeignApi::class)
internal class IosPlayerController(
    private val youtube: IosYouTubeClient,
    private val playbackFailedText: () -> String,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = AVPlayer()
    private val mutableState = MutableStateFlow(PlayerState())
    override val state = mutableState.asStateFlow()
    private var playbackJob: Job? = null
    private var endObserver: NSObjectProtocol? = null
    private var failObserver: NSObjectProtocol? = null

    override fun play(song: SongItem) {
        playbackJob?.cancel()
        clearItem()
        mutableState.value = PlayerState(song = song, isLoading = true, durationMs = (song.duration ?: 0) * 1000L)
        playbackJob = scope.launch {
            try {
                val stream = checkNotNull(youtube.resolve(song))
                val url = checkNotNull(NSURL.URLWithString(stream.audioUrl))
                ensureActive()
                val item = AVPlayerItem(asset = AVURLAsset(
                    uRL = url,
                    options = mapOf("AVURLAssetHTTPHeaderFieldsKey" to stream.headers),
                ))
                endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVPlayerItemDidPlayToEndTimeNotification,
                    `object` = item,
                    queue = NSOperationQueue.mainQueue,
                ) {
                    if (player.currentItem == item) {
                        mutableState.value = mutableState.value.copy(
                            isLoading = false,
                            isPlaying = false,
                            hasEnded = true,
                            positionMs = mutableState.value.durationMs,
                        )
                    }
                }
                failObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVPlayerItemFailedToPlayToEndTimeNotification,
                    `object` = item,
                    queue = NSOperationQueue.mainQueue,
                ) {
                    if (player.currentItem == item) {
                        playbackJob?.cancel()
                        failPlayback()
                    }
                }
                player.replaceCurrentItemWithPlayerItem(item)
                mutableState.value = mutableState.value.copy(
                    isPlaying = true,
                    format = stream.codecs?.takeIf { it.isNotBlank() }?.let { codec ->
                        StreamFormat(
                            codec = if (codec.startsWith("mp4a")) "AAC" else codec.uppercase(),
                            sampleRate = stream.sampleRate ?: 0,
                            bitrate = stream.bitrate ?: 0,
                        )
                    },
                )
                player.play()
                while (isActive) {
                    check(item.status != AVPlayerItemStatusFailed)
                    updatePosition(item)
                    delay(250)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failPlayback()
            }
        }
    }

    override fun togglePlayPause() {
        if (player.currentItem == null) {
            state.value.song?.takeIf { state.value.error != null }?.let(::play)
            return
        }
        if (mutableState.value.isPlaying) {
            player.pause()
            mutableState.value = mutableState.value.copy(isPlaying = false, isLoading = false)
        } else {
            if (mutableState.value.hasEnded) seekTo(0)
            player.play()
            mutableState.value = mutableState.value.copy(isPlaying = true)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (player.currentItem == null) return
        val duration = mutableState.value.durationMs
        val target = positionMs.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekToTime(CMTimeMakeWithSeconds(target / 1000.0, 1000))
        mutableState.value = mutableState.value.copy(positionMs = target, hasEnded = false)
    }

    override fun release() {
        scope.cancel()
        clearItem()
    }

    private fun updatePosition(item: AVPlayerItem) {
        val current = mutableState.value
        val duration = CMTimeGetSeconds(item.duration)
        val durationMs = if (duration.isFinite() && duration > 0) (duration * 1000).toLong() else current.durationMs
        val position = CMTimeGetSeconds(player.currentTime())
        val positionMs = if (position.isFinite()) (position * 1000).toLong().coerceAtLeast(0) else current.positionMs
        mutableState.value = current.copy(
            isLoading = current.isPlaying && (item.status == AVPlayerItemStatusUnknown ||
                player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate),
            durationMs = durationMs,
            positionMs = if (current.hasEnded && durationMs > 0) durationMs else positionMs,
        )
    }

    private fun failPlayback() {
        clearItem()
        mutableState.value = mutableState.value.copy(
            isLoading = false, isPlaying = false, error = playbackFailedText(),
        )
    }

    private fun clearItem() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        failObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        failObserver = null
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}

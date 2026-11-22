package com.mochyy.mochymusic.shared.playback

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochyy.mochymusic.shared.library.LibraryDatabase
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.settings.playbackPreferences
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The queue with no UI at all, the way a CarPlay-only launch runs it: the saved Up next comes
 * back, a song ending starts the next one, and the system Next control works.
 */
class SharedQueueTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    private class FakePlayer : PlayerController {
        val mutableState = MutableStateFlow(PlayerState())
        val played = MutableStateFlow<List<String>>(emptyList())
        override val state: StateFlow<PlayerState> = mutableState
        override fun play(song: SongItem) {
            mutableState.value = PlayerState(song = song, isPlaying = true)
            played.value = played.value + song.id
        }
        override fun togglePlayPause() {}
        override fun seekTo(positionMs: Long) {}
        override fun release() {}
    }

    private fun withQueue(block: suspend (SharedQueue, FakePlayer, QueueTransport, suspend (() -> Unit) -> Unit) -> Unit) {
        val directory = createTempDirectory("mochy-queue").toFile()
        val executor = Executors.newSingleThreadExecutor()
        val main = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + main)
        val database = Room.databaseBuilder<LibraryDatabase>(directory.resolve("library.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            runBlocking {
                val settings = playbackPreferences { directory.resolve("settings.preferences_pb").absolutePath }
                settings.setUpcomingQueue(listOf(song("b"), song("c")))
                val player = FakePlayer()
                val transport = QueueTransport()
                val queue = SharedQueue(player, settings, LocalLibrary(database.tracks()), transport, scope)
                // the queue is confined to its one thread, as it is to the main thread in the apps
                val onQueueThread: suspend (() -> Unit) -> Unit = { action -> withContext(main) { action() } }
                onQueueThread { queue.start() }
                withTimeout(10_000) { block(queue, player, transport, onQueueThread) }
                onQueueThread { queue.close() }
            }
        } finally {
            scope.cancel()
            database.close()
            executor.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun aColdStartRestoresUpNextAndMovesOnWhenASongEnds() = withQueue { queue, player, _, onQueueThread ->
        queue.ready.first { it }
        assertEquals(listOf("b", "c"), queue.upcoming.value.map { it.id })

        onQueueThread { queue.play(song("a")) }
        player.mutableState.value = player.mutableState.value.copy(isPlaying = false, hasEnded = true)

        player.played.first { it == listOf("a", "b") }
        queue.upcoming.first { songs -> songs.map { it.id } == listOf("c") }
    }

    @Test
    fun theSystemNextControlPlaysTheHeadOfUpNext() = withQueue { _, player, transport, onQueueThread ->
        transport.canGoNext.first { it }
        onQueueThread { transport.next() }
        player.played.first { it == listOf("b") }
    }
}

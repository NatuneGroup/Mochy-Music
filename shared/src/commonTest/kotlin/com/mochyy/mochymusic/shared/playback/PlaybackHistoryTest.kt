package com.mochyy.mochymusic.shared.playback

import com.music.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackHistoryTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    @Test
    fun previousTwiceFromThirdSongReachesFirst() {
        val history = PlaybackHistory()
        val a = song("a")
        val b = song("b")
        val c = song("c")
        listOf(a, b, c).forEach(history::started)

        val first = history.stepBack(c)
        assertEquals(b, first)
        history.started(b)

        val second = history.stepBack(b)
        assertEquals(a, second)
        history.started(a)

        assertNull(history.stepBack(a))
    }

    @Test
    fun replayingASongDoesNotReorderWhatCameBefore() {
        val history = PlaybackHistory()
        val a = song("a")
        val b = song("b")
        val c = song("c")
        listOf(a, b, c, b).forEach(history::started)

        assertEquals(c, history.stepBack(b))
        history.started(c)
        assertEquals(b, history.stepBack(c))
        history.started(b)
        assertEquals(a, history.stepBack(b))
    }

    @Test
    fun theSameSongStartingTwiceInARowIsOneEntry() {
        val history = PlaybackHistory()
        val a = song("a")
        history.started(a)
        history.started(a)
        assertNull(history.stepBack(a))
        assertTrue(history.isEmpty)
    }

    @Test
    fun remembersEverySongThisSessionStarted() {
        val history = PlaybackHistory()
        val a = song("a")
        val b = song("b")
        history.started(a)
        history.started(b)
        history.stepBack(b)
        assertTrue(history.playedThisSession("a"))
        assertTrue(history.playedThisSession("b"))
        assertTrue(!history.playedThisSession("c"))
    }

    @Test
    fun keepsOnlyTheNewestSongsPastTheLimit() {
        val history = PlaybackHistory(limit = 2)
        val songs = listOf("a", "b", "c").map(::song)
        songs.forEach(history::started)
        assertEquals(songs[1], history.stepBack(songs[2]))
        assertNull(history.stepBack(songs[1]))
    }
}

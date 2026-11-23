package com.mochyy.mochymusic.db

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InternalDatabaseTest {
    @Test
    fun opensAndReadsASongBackThroughSongDao() = runTest {
        val directory = createTempDirectory("mochy-db").toFile()
        val database = Room.databaseBuilder<InternalDatabase>(directory.resolve("mochy.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            // no shared DAO inserts a SongEntity yet, so the row goes in as plain SQL
            database.useWriterConnection { connection ->
                connection.usePrepared(
                    "INSERT INTO song (id, title, duration, liked, totalPlayTime) VALUES (?, ?, ?, 0, 0)",
                ) { statement ->
                    statement.bindText(1, "test-song")
                    statement.bindText(2, "Test song")
                    statement.bindLong(3, 180)
                    statement.step()
                }
            }

            val song = assertNotNull(database.songDao.getSongById("test-song"))
            assertEquals("Test song", song.song.title)
            assertEquals(180, song.song.duration)
            assertEquals(45, database.useReaderConnection { connection ->
                connection.usePrepared("PRAGMA user_version") { it.step(); it.getLong(0).toInt() }
            })
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }
}

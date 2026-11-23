package com.mochyy.mochymusic.db

import com.mochyy.mochymusic.db.daos.SpeedDialDao

// bump this with every schema change and add its migration to AllMigrations (Android) and SharedMigrations
// (desktop and iOS, nonAndroidMain); the import checks old exports against it too.
// Both InternalDatabase classes (app/ for Android, nonAndroidMain for desktop and iOS) read it.
const val MusicDatabaseVersion = 45

// The music database as shared code sees it, on every platform. Android's InternalDatabase (app/) and
// the desktop and iOS InternalDatabase (nonAndroidMain) both implement it, so shared code never names
// either class.
interface MochyDatabase {
    val songDao: SongDao
    val playlistDao: PlaylistDao
    val historyDao: HistoryDao
    val suspendingDao: SuspendingDao
    val speedDialDao: SpeedDialDao
}

package com.mochyy.mochymusic.db

import com.mochyy.mochymusic.db.daos.SpeedDialDao

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

package com.mochyy.mochymusic.shared.ios

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.SharedMigrations
import com.mochyy.mochymusic.shared.library.LibraryDatabase
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.settings.PlaybackSettingsFile
import com.mochyy.mochymusic.shared.settings.playbackPreferences

// Both stores live in Application Support, beside each other and beside the settings file, so one
// backup of that folder takes everything the app keeps.
internal val iosPlaybackPreferences by lazy {
    playbackPreferences { "${IosPaths.applicationSupport}/$PlaybackSettingsFile" }
}

internal val iosLibrary by lazy {
    val database = Room.databaseBuilder<LibraryDatabase>(name = "${IosPaths.applicationSupport}/library.db")
        .setDriver(BundledSQLiteDriver())
        .build()
    LocalLibrary(database.tracks())
}

// the music database shared ViewModels read (Koin hands it out), next to library.db under Android's file name
internal val iosDatabase by lazy {
    Room.databaseBuilder<InternalDatabase>(name = "${IosPaths.applicationSupport}/mochy.db")
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*SharedMigrations)
        .build()
}

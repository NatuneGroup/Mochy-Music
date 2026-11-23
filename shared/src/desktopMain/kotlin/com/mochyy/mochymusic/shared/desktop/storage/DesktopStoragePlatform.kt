// DesktopStoragePlatform.kt
// Settings > Storage, measured on a computer instead of a phone. Mochy's desktop build streams its
// music: it has no downloads and no ExoPlayer, so the numbers that matter here are the image cache
// coil writes, the song cache reserved for streamed audio, the databases, and the rest of the app
// data folder. The folder places are fixed the way every desktop app's are, so the page offers the
// one place and opening it rather than a move between drives it can't promise.

package com.mochyy.mochymusic.shared.desktop.storage

import com.mochyy.mochymusic.shared.desktop.DesktopPaths
import com.mochyy.mochymusic.shared.desktop.files.clearDirectory
import com.mochyy.mochymusic.shared.desktop.files.filesStartingWith
import com.mochyy.mochymusic.shared.desktop.files.folderLabel
import com.mochyy.mochymusic.shared.desktop.files.sizeOf
import com.mochyy.mochymusic.viewmodels.CanvasGifStats
import com.mochyy.mochymusic.viewmodels.DownloadedSong
import com.mochyy.mochymusic.viewmodels.DriveUsage
import com.mochyy.mochymusic.viewmodels.ExportChoice
import com.mochyy.mochymusic.viewmodels.ExportStep
import com.mochyy.mochymusic.viewmodels.MoveStep
import com.mochyy.mochymusic.viewmodels.PlaceChoice
import com.mochyy.mochymusic.viewmodels.StorageKind
import com.mochyy.mochymusic.viewmodels.StoragePlaceInfo
import com.mochyy.mochymusic.viewmodels.StoragePlatform
import com.mochyy.mochymusic.viewmodels.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

internal class DesktopStoragePlatform(
    private val appData: File = DesktopPaths.appData,
    private val imageCache: File = DesktopPaths.images,
    private val songCache: File = DesktopPaths.songCache,
    private val downloadsDir: File = DesktopPaths.downloads,
) : StoragePlatform {

    override val moveStep = MutableStateFlow<MoveStep>(MoveStep.Idle)
    override val exportStep = MutableStateFlow<ExportStep>(ExportStep.Idle)

    // a desktop build doesn't download songs: the list is empty, so the download rows stay off
    override val downloads: StateFlow<List<DownloadedSong>?> = MutableStateFlow<List<DownloadedSong>?>(emptyList())

    override suspend fun measure(): StorageUsage = withContext(Dispatchers.IO) { measureNow() }

    override suspend fun clearCaches() = withContext(Dispatchers.IO) {
        clearDirectory(imageCache)
        clearDirectory(songCache)
    }

    // no local files mean no saved covers; the folder is still emptied if a build ever writes one
    override suspend fun clearLocalCovers() = withContext(Dispatchers.IO) {
        clearDirectory(appData.resolve("local_covers"))
    }

    override suspend fun removeAllDownloads() = withContext(Dispatchers.IO) {
        clearDirectory(downloadsDir)
    }

    override suspend fun removeDownloads(songIds: Collection<String>) = withContext(Dispatchers.IO) {
        for (file in filesStartingWith(downloadsDir, songIds)) runCatching { file.deleteRecursively() }
    }

    override suspend fun placeChoice(kind: StorageKind): PlaceChoice = withContext(Dispatchers.IO) {
        val current = placeFor(kind)
        PlaceChoice(
            places = listOf(current),
            current = current,
            bytesToMove = sizeOf(File(current.root)),
        )
    }

    /** a folder the desktop folder picker handed back; anything not writable is refused, as SAF is on Android */
    override suspend fun pickedFolder(tree: String): StoragePlaceInfo? = withContext(Dispatchers.IO) {
        val directory = runCatching { File(tree) }.getOrNull() ?: return@withContext null
        if (!directory.isDirectory || !directory.canWrite()) return@withContext null
        place(directory, isPhone = false)
    }

    // a plain filesystem folder needs no grant to read
    override fun needsFolderAccess(place: StoragePlaceInfo): Boolean = false

    override fun move(kind: StorageKind, to: StoragePlaceInfo) {
        // the folders are fixed: the image loader, the database and the instance lock all read
        // DesktopPaths, so a move here would only hide the files the rest of the app keeps writing
        moveStep.value = MoveStep.Failed(kind, FIXED_FOLDER_MESSAGE)
    }

    override fun clearMoveFailure() {
        moveStep.value = MoveStep.Idle
    }

    override fun export(tree: String, choice: ExportChoice) {
        exportStep.value = ExportStep.Failed(EXPORT_MESSAGE)
    }

    override fun clearExport() {
        exportStep.value = ExportStep.Idle
    }

    override suspend fun canvasGifStats(): CanvasGifStats = CanvasGifStats(bytes = 0L, count = 0)

    override suspend fun clearCanvasGifs() = Unit

    private fun measureNow(): StorageUsage {
        val cache = sizeOf(imageCache) + sizeOf(songCache)
        val downloads = sizeOf(downloadsDir)
        val database = databaseFiles().sumOf { it.length() }
        val localCovers = sizeOf(appData.resolve("local_covers"))
        // everything else the app writes: settings, the notifications of this folder, the databases
        // and downloads are named above so they aren't counted twice
        val appDataBytes = (sizeOf(appData) - downloads - database - localCovers).coerceAtLeast(0L)
        val mochy = cache + downloads + database + localCovers + appDataBytes
        return StorageUsage(
            drives = listOf(driveUsage(mochy)),
            cache = cache,
            downloads = downloads,
            database = database,
            localCovers = localCovers,
            appData = appDataBytes,
        )
    }

    private fun driveUsage(mochy: Long): DriveUsage {
        val probe = appData.takeIf { it.exists() } ?: File(System.getProperty("user.home"))
        return DriveUsage(
            label = folderLabel(probe),
            // the desktop's one disk is the phone's internal storage: the page draws it as "overall"
            isPhone = true,
            total = probe.totalSpace,
            free = probe.usableSpace,
            mochy = mochy,
        )
    }

    private fun databaseFiles(): List<File> =
        appData.listFiles().orEmpty().filter { file ->
            file.isFile && DatabasePrefixes.any { file.name.startsWith(it) }
        }

    private fun placeFor(kind: StorageKind): StoragePlaceInfo = when (kind) {
        StorageKind.Downloads -> place(downloadsDir, isPhone = true)
        StorageKind.SongCache -> place(songCache, isPhone = true)
    }

    private fun place(file: File, isPhone: Boolean): StoragePlaceInfo = StoragePlaceInfo(
        root = file.absolutePath,
        label = folderLabel(file),
        isPhone = isPhone,
        total = file.totalSpace,
        free = file.usableSpace,
        isFolder = true,
    )

    private companion object {
        // both databases sit next to the settings, the way Android keeps them in filesDir
        val DatabasePrefixes = listOf("mochy.db", "library.db")
        const val FIXED_FOLDER_MESSAGE = "Mochy keeps this folder where it keeps its settings on this computer."
        const val EXPORT_MESSAGE = "Exporting downloads needs the download engine, which is still in the Android app."
    }
}

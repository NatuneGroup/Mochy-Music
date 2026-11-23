// IosStoragePlatform.kt
// iOS's side of the shared storage page. An iOS app has one sandbox and no memory card, no media3
// downloads and no offline cache, so the page shows what the app really keeps (its covers, its
// database, its settings and the caches iOS may purge) and only offers the jobs this platform has.

package com.mochyy.mochymusic.shared.ios

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
import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURLCache
import platform.UIKit.UIDevice

/**
 * The app's files behind the shared storage page's contract.
 *
 * What iOS can do: measure the sandbox, clear the caches iOS may delete, and say how much room is
 * left on the phone. What it can't: downloads (there is no download engine on iOS), moving folders to
 * another drive (an app can't), and exporting songs out of the app (no SAF, no zipped download
 * folder). Those rows stay disabled or say so instead of pretending to run.
 */
internal class IosStoragePlatform : StoragePlatform {

    private val move = MutableStateFlow<MoveStep>(MoveStep.Idle)
    private val export = MutableStateFlow<ExportStep>(ExportStep.Idle)

    override val moveStep: StateFlow<MoveStep> = move.asStateFlow()
    override val exportStep: StateFlow<ExportStep> = export.asStateFlow()

    /** iOS keeps no downloaded songs yet, so the manage-downloads sheet always has an empty list. */
    override val downloads: StateFlow<List<DownloadedSong>?> =
        MutableStateFlow<List<DownloadedSong>?>(emptyList())

    override suspend fun measure(): StorageUsage = withContext(ioDispatcher) { readUsage() }

    override suspend fun clearCaches() = withContext(ioDispatcher) {
        // covers and every other file iOS may regenerate live under Caches; NSURLCache keeps its
        // responses in memory and on disk, so it is emptied by its own call as well
        iosImageLoader?.let { loader ->
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        clearDirectory(IosPaths.caches)
        NSURLCache.sharedURLCache.removeAllCachedResponses()
    }

    // local cover files are Android's (its importer writes them); iOS reads covers from the image
    // cache the line above already clears, so there is nothing of its own to remove here
    override suspend fun clearLocalCovers() = Unit

    override suspend fun removeAllDownloads() = Unit

    override suspend fun removeDownloads(songIds: Collection<String>) = Unit

    override suspend fun placeChoice(kind: StorageKind): PlaceChoice = withContext(ioDispatcher) {
        // an iOS app keeps everything in its own sandbox, so there is exactly one place to offer
        val place = phonePlace()
        PlaceChoice(places = listOf(place), current = place, bytesToMove = 0L)
    }

    /** There is no system folder picker on iOS and no folder to move into, so a picked tree is never usable. */
    override suspend fun pickedFolder(tree: String): StoragePlaceInfo? = null

    override fun needsFolderAccess(place: StoragePlaceInfo): Boolean = false

    override fun move(kind: StorageKind, to: StoragePlaceInfo) = Unit

    override fun clearMoveFailure() {
        move.value = MoveStep.Idle
    }

    override fun export(tree: String, choice: ExportChoice) {
        // exporting puts the download folder into a zipped document; iOS has neither downloads nor a
        // zipped exporter, so the row reports that instead of leaving a spinner running
        export.value = ExportStep.Failed("Exporting downloads isn't on iOS yet.")
    }

    override fun clearExport() {
        export.value = ExportStep.Idle
    }

    override suspend fun canvasGifStats(): CanvasGifStats = CanvasGifStats(bytes = 0L, count = 0)

    override suspend fun clearCanvasGifs() = Unit

    private fun readUsage(): StorageUsage {
        val support = IosPaths.applicationSupport
        val caches = IosPaths.caches
        val documents = IosPaths.documents

        val cacheBytes = sizeOfDirectory(caches)
        val databaseBytes = databaseSize(support)
        val supportBytes = sizeOfDirectory(support)
        val documentBytes = sizeOfDirectory(documents)
        // the database has its own row, and everything else the app support folder holds (the
        // settings file, the queue's backup) is app data
        val appData = (supportBytes - databaseBytes).coerceAtLeast(0L) + documentBytes
        val phone = phonePlace()
        val mochy = cacheBytes + databaseBytes + appData
        return StorageUsage(
            drives = listOf(
                DriveUsage(
                    label = phone.label,
                    isPhone = true,
                    total = phone.total,
                    free = phone.free,
                    mochy = mochy,
                ),
            ),
            cache = cacheBytes,
            // no download engine on iOS: nothing to report, and the rows that would use it stay disabled
            downloads = 0L,
            database = databaseBytes,
            localCovers = 0L,
            appData = appData,
        )
    }

    /** The music library and the shared database, with the -wal/-shm files SQLite keeps beside them. */
    private fun databaseSize(support: String): Long {
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(support, error = null)
            ?: return 0L
        var total = 0L
        for (entry in names) {
            val name = entry as? String ?: continue
            if (isDatabaseFile(name, "mochy.db") || isDatabaseFile(name, "library.db")) {
                total += sizeOfFile("$support/$name")
            }
        }
        return total
    }

    private fun phonePlace(): StoragePlaceInfo {
        val (total, free) = volumeSize(IosPaths.applicationSupport)
        return StoragePlaceInfo(
            root = IosPaths.applicationSupport,
            label = UIDevice.currentDevice.localizedModel,
            isPhone = true,
            total = total,
            free = free,
        )
    }
}

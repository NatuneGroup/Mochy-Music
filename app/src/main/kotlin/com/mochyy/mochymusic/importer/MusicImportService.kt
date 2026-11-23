// MusicImportService.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.MusicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android's one import service: the background run itself lives in shared MusicImportEngine; this
 * holds the Hilt singleton every screen already got and hands the engine the app's settings,
 * notifications, pending likes and database transaction.
 */
@Singleton
class MusicImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    internalDatabase: InternalDatabase,
) {
    internal val engine = MusicImportEngine(
        database = database,
        settings = AndroidImportSettings(context),
        notifier = AndroidImportNotifier(context),
        pendingLikes = AndroidImportPendingLikes(context),
        matches = ImportMatches(AndroidImportMatchStore(context)),
        transactions = AndroidImportTransactions(internalDatabase),
    )

    val progress: StateFlow<ImportProgress> = engine.progress

    val isRunning: Boolean get() = engine.isRunning

    /** starts an import in the background; false if one is already running */
    fun startImport(parsed: ParsedImport, options: ImportOptions = ImportOptions()): Boolean =
        engine.startImport(parsed, options)

    /** re-reads imported playlists and adds songs new on the service to their end */
    fun startResync(requests: List<ResyncRequest>): Boolean = engine.startResync(requests)

    /** stops after the current song; everything imported so far stays */
    fun cancel() = engine.cancel()

    /** forgets a finished import's summary so the next one starts clean */
    fun clearFinished() = engine.clearFinished()

    /** early imports made a playlist named after the service and never filled the library, file their songs into the library once */
    suspend fun repairEarlierImports() = engine.repairEarlierImports()
}

// ImportPlatform.kt

package com.mochyy.mochymusic.importer

/**
 * What an import needs from the platform it runs on: the app's signed-in state and own settings,
 * the notification it leaves when a run ends, and the one transaction its database writes go in.
 * Android answers all three from the classes it already had, so the import behaviour is unchanged.
 */
interface ImportSettings {
    /** the app's signed-in check for YouTube (a saved cookie and a working connection) */
    fun isUserLoggedIn(): Boolean

    /** the "keep imported music off YouTube" setting */
    fun keepImportsOffYouTube(): Boolean

    /** the saved account name, when the client can't say it */
    fun youTubeAccountName(): String?
}

/** How a finished import or re-sync tells the user, wherever they are by then. */
interface ImportNotifier {
    fun importFinished(matched: Int, total: Int)

    fun resyncFinished(progress: ImportProgress)
}

/** One database transaction over the shared DAOs: every write in [block] lands, or none does. */
fun interface ImportTransactions {
    suspend fun run(block: suspend () -> Unit)
}

/**
 * Runs the writes without opening a transaction, for a caller that has no Room database to open
 * one on. A crash between two inserts leaves only the first, which the readers already treat as an
 * older import (missing keys are paired by title and artist), so nothing is lost.
 */
object NoImportTransaction : ImportTransactions {
    override suspend fun run(block: suspend () -> Unit) = block()
}

/**
 * Likes the import added that YouTube doesn't know about yet, kept by the platform's own file so
 * the next sync sends them up; the import never reads the list back.
 */
interface ImportPendingLikes {
    fun add(ids: Collection<String>)

    fun remove(ids: Collection<String>)
}

// AndroidImportPlatform.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.constants.AccountNameKey
import com.mochyy.mochymusic.constants.YtExcludeImportsKey
import androidx.room.withTransaction
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.extensions.isUserLoggedIn
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get

/** the app's signed-in check, its "keep imports off YouTube" setting and the saved account name */
class AndroidImportSettings(context: Context) : ImportSettings {
    private val appContext = context.applicationContext

    override fun isUserLoggedIn(): Boolean = appContext.isUserLoggedIn()

    override fun keepImportsOffYouTube(): Boolean = appContext.dataStore.get(YtExcludeImportsKey, false)

    override fun youTubeAccountName(): String? = appContext.dataStore.get(AccountNameKey, "").ifBlank { null }
}

/** finished runs tell the user exactly as they always did: a notification, or a toast for a re-sync */
class AndroidImportNotifier(private val context: Context) : ImportNotifier {
    override fun importFinished(matched: Int, total: Int) = ImportNotices.importFinished(context, matched, total)

    override fun resyncFinished(progress: ImportProgress) = ImportNotices.resyncFinished(context, progress)
}

/**
 * The import's writes go in one Room transaction. It is the Room database's own coroutine
 * transaction (not the app database's blocking one) so the suspend DAO calls inside stay on the
 * transaction's connection instead of waiting on it from another thread.
 */
class AndroidImportTransactions(private val database: InternalDatabase) : ImportTransactions {
    override suspend fun run(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }
}

/** likes the app keeps for the next sync, in its own file */
class AndroidImportPendingLikes(private val context: Context) : ImportPendingLikes {
    override fun add(ids: Collection<String>) = PendingYouTubeLikes.add(context, ids)

    override fun remove(ids: Collection<String>) = PendingYouTubeLikes.remove(context, ids)
}

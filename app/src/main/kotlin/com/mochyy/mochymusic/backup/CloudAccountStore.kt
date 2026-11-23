// CloudAccountStore.kt

package com.mochyy.mochymusic.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the account in no_backup, so it never rides along in android's backup, mochy's export or a
 * cloud backup itself. The account itself and the json it is written as live in :shared now
 * (backup/CloudAccount.kt); only the file is Android's.
 */
@Singleton
class CloudAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : CloudAccountReader {
    private val file = File(context.noBackupFilesDir, FileName)
    private val writing = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _account = MutableStateFlow<CloudAccount?>(null)
    val account: StateFlow<CloudAccount?> = _account.asStateFlow()

    init {
        // read off the main thread, the page shows "not connected" for the moment it takes
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            _account.value = read()
            loaded.complete(Unit)
        }
    }

    override suspend fun current(): CloudAccount? {
        loaded.await()
        return _account.value
    }

    /** null signs out */
    suspend fun save(account: CloudAccount?) = withContext(Dispatchers.IO) {
        loaded.await()
        writing.withLock { write(account) }
    }

    /** changes the saved account in place, for new tokens and the last backup's stamp; does nothing once signed out */
    override suspend fun update(change: (CloudAccount) -> CloudAccount) {
        withContext(Dispatchers.IO) {
            loaded.await()
            writing.withLock { _account.value?.let { write(change(it)) } }
        }
    }

    private fun read(): CloudAccount? = runCatching {
        if (!file.exists()) return null
        CloudAccount.fromJson(file.readText())
    }.getOrNull()

    // written beside and renamed over, so a phone dying mid-write never leaves half an account
    private fun write(account: CloudAccount?) {
        if (account == null) {
            file.delete()
        } else {
            val temp = File(file.path + ".tmp")
            temp.writeText(account.toJson())
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
        _account.value = account
    }

    private companion object {
        const val FileName = "cloud_backup_account.json"
    }
}

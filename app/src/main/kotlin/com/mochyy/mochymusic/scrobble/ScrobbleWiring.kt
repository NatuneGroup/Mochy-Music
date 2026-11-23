// ScrobbleWiring.kt
//
// The Android side of the shared scrobbler: its queue file, the WorkManager retry that survives
// the app closing, and the toast a service's sign-out raises. The scrobbling itself is shared.

package com.mochyy.mochymusic.scrobble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mochyy.mochymusic.R
import java.io.File
import java.util.concurrent.TimeUnit

/** The scrobble queue's file, written the way Mochy always wrote it: AtomicFile, so a phone dying mid-write never leaves half a queue. */
internal class AndroidScrobbleQueueStore(context: Context) : ScrobbleQueueStore {
    private val file = AtomicFile(File(context.filesDir, "scrobble_queue.json"))

    override suspend fun read(): ByteArray? {
        if (!file.baseFile.exists()) return null
        return file.readFully()
    }

    override suspend fun write(bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

/** Hands the rest to WorkManager, which waits for a network and survives the app closing. */
internal class WorkManagerScrobbleRetry(private val context: Context) : ScrobbleRetry {
    override fun sendLater() {
        val request = OneTimeWorkRequestBuilder<ScrobbleSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "scrobble_send"
    }
}

/** The long toast a service's sign-out has always shown. */
internal class ToastScrobbleNotices(private val context: Context) : ScrobbleNotices {
    override fun signedOut(label: String) {
        val message = context.getString(R.string.scrobble_signed_out, label)
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}

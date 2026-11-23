package com.mochyy.mochymusic.eq.autoeq

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** the autoeq database: the index is kept on disk for a week so search works offline after the first visit, a headphone's own files are fetched when picked and kept for good */
@Singleton
class AutoEqRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy {
        OkHttpClient.Builder()
            .dispatcher(com.mochyy.mochymusic.utils.AppProxy.dispatcher)
            .connectionPool(com.mochyy.mochymusic.utils.AppProxy.connectionPool)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val dir: File get() = File(context.filesDir, "autoeq").apply { mkdirs() }
    private val indexMutex = Mutex()

    @Volatile private var models: List<AutoEqModel>? = null

    /** the headphones, a to z; throws when there's neither a network nor a saved copy */
    suspend fun models(refresh: Boolean = false): List<AutoEqModel> = indexMutex.withLock {
        if (!refresh) models?.let { return it }
        withContext(Dispatchers.IO) {
            val file = File(dir, "INDEX.md")
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > IndexMaxAgeMs
            val text = if (refresh || stale) {
                runCatching { download("$ResultsBase/INDEX.md") }
                    .onSuccess { file.writeText(it) }
                    .onFailure { Timber.tag(Tag).w(it, "AutoEq index fetch failed") }
                    .getOrNull() ?: file.takeIf { it.exists() }?.readText()
                    ?: throw IOException("Couldn't reach the AutoEq database")
            } else {
                file.readText()
            }
            AutoEqCatalog.parseIndex(text).also { models = it }
        }
    }

    suspend fun measurement(entry: AutoEqEntry): AutoEqMeasurement = withContext(Dispatchers.IO) {
        val folder = entry.path.substringAfterLast('/')
        val base = "$ResultsBase/" + entry.path.split('/').joinToString("/") { Uri.encode(it) }
        val cacheKey = entry.path.hashCode().toUInt().toString(16)
        val csv = cached("$cacheKey.csv") { download("$base/${Uri.encode("$folder.csv")}") }
        val txt = cached("$cacheKey.txt") { download("$base/${Uri.encode("$folder ParametricEQ.txt")}") }
        AutoEqCatalog.parseMeasurement(entry, csv, txt)
    }

    private inline fun cached(name: String, fetch: () -> String): String {
        val file = File(dir, name)
        if (file.exists() && file.length() > 0) return file.readText()
        return fetch().also { file.writeText(it) }
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Mochy").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AutoEq ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty AutoEq response")
        }
    }

    companion object {
        private const val Tag = "AutoEq"
        private const val ResultsBase = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
        private const val IndexMaxAgeMs = 7L * 24 * 60 * 60 * 1000

        /** the list's search, kept here so screens keep calling AutoEqRepository.search */
        fun search(all: List<AutoEqModel>, query: String): List<AutoEqModel> = AutoEqCatalog.search(all, query)
    }
}

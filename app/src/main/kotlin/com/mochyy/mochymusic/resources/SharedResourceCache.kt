package com.mochyy.mochymusic.resources

import android.content.Context
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceReader
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Compose's Android [ResourceReader] opens the asset and, for [readPart], skips from the very start of
 * the stream on every call. The shared string table is one deflated `.cvr` a few hundred KB long, and
 * every string is a slice of that one file, so a screen that reads ten shared strings inflates the whole
 * table ten times. Compose runs those reads with `runBlocking` during composition, on the UI thread.
 *
 * Keeping each asset's decompressed bytes for the process turns all reads after the first into array
 * slices. [SharedResourceCache.prewarm] moves even that first read off the UI thread.
 */
@OptIn(ExperimentalResourceApi::class)
internal class CachingResourceReader(
    private val source: ResourceReader,
    private val cache: MutableMap<String, ByteArray> = ConcurrentHashMap(),
) : ResourceReader {

    override suspend fun read(path: String): ByteArray {
        cache[path]?.let { return it }
        val bytes = source.read(path)
        // two first reads of the same asset may race; both hold identical bytes, so the later one is fine
        cache[path] = bytes
        return bytes
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val bytes = read(path)
        val result = ByteArray(size.toInt())
        val start = offset.toInt()
        if (start in 0 until bytes.size) {
            bytes.copyInto(
                destination = result,
                destinationOffset = 0,
                startIndex = start,
                endIndex = minOf(start + result.size, bytes.size),
            )
        }
        return result
    }

    override fun getUri(path: String): String = source.getUri(path)
}

/**
 * The decompressed shared Compose resources, kept for the process. Warm them from
 * `Application.onCreate` on an IO thread: then the first shared string a cold Settings screen asks for
 * is already in memory, instead of inflating the table inside composition.
 */
internal object SharedResourceCache {
    val bytes: MutableMap<String, ByteArray> = ConcurrentHashMap()

    fun prewarm(context: Context) {
        val assets = context.assets
        // the configuration, not Locale.getDefault(): the app can pin its language, and AppLanguage applies it here
        val locales = context.resources.configuration.locales
        val locale = if (locales.size() > 0) locales[0] else Locale.getDefault()
        val language = locale.language
        val region = locale.country
        // only the default table and this device's language: warming all translations would keep every
        // locale in memory for the life of the process
        val valueDirs = buildList {
            add("values")
            add("values-$language")
            if (region.isNotEmpty()) add("values-$language-r$region")
        }
        val packages = runCatching { assets.list("composeResources").orEmpty() }.getOrDefault(emptyArray<String>())
        for (pkg in packages) {
            for (dir in valueDirs) {
                val relative = "composeResources/$pkg/$dir"
                val files = runCatching { assets.list(relative).orEmpty() }.getOrDefault(emptyArray<String>())
                for (file in files) {
                    if (!file.endsWith(".cvr")) continue
                    val path = "$relative/$file"
                    if (path in bytes) continue
                    runCatching { assets.open(path).use { it.readBytes() } }
                        .onSuccess { bytes[path] = it }
                        .onFailure { Timber.w(it, "could not prewarm shared resource %s", path) }
                }
            }
        }
    }
}

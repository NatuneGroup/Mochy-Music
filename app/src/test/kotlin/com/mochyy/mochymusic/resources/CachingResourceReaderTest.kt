package com.mochyy.mochymusic.resources

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalResourceApi::class)
class CachingResourceReaderTest {

    private class CountingReader(private val content: ByteArray) : ResourceReader {
        var reads = 0
            private set

        override suspend fun read(path: String): ByteArray {
            reads++
            return content
        }

        override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray =
            error("readPart must be served from the cached asset, not from the stream")

        override fun getUri(path: String): String = "file:///android_asset/$path"
    }

    private val path = "composeResources/com.mochyy.mochymusic.shared.resources/values/app_strings.commonMain.cvr"
    private val table = "0|settings|U2V0dGluZ3M=|1|search|U2VhcmNo".encodeToByteArray()
    private val settings = "0|settings|U2V0dGluZ3M=|".encodeToByteArray()
    private val search = "1|search|U2VhcmNo".encodeToByteArray()

    // the shape of a cold Settings screen: Compose reads one record per shared string, all slices of the
    // same .cvr, each through readPart. The asset must be read once, not once per string.
    @Test
    fun everyStringComesFromOneAssetRead() = runBlocking {
        val source = CountingReader(table)
        val reader = CachingResourceReader(source)

        assertArrayEquals(settings, reader.readPart(path, 0, settings.size.toLong()))
        assertArrayEquals(search, reader.readPart(path, settings.size.toLong(), search.size.toLong()))
        assertEquals("0|settings|U2V0dGluZ3M=|1|search|U2VhcmNo", reader.read(path).decodeToString())

        assertEquals(1, source.reads)
    }

    // SharedResourceCache.prewarm fills exactly this kind of map on an IO thread. A read that finds the
    // bytes there must not open or inflate the asset at all.
    @Test
    fun prewarmedBytesNeverTouchTheAsset() = runBlocking {
        val source = CountingReader(table)
        val reader = CachingResourceReader(source, mutableMapOf(path to table))

        assertArrayEquals(settings, reader.readPart(path, 0, settings.size.toLong()))
        assertArrayEquals(search, reader.readPart(path, settings.size.toLong(), search.size.toLong()))

        assertEquals(0, source.reads)
    }

    @Test
    fun eachAssetIsReadOnce() = runBlocking {
        val source = CountingReader(table)
        val reader = CachingResourceReader(source)

        reader.read(path)
        reader.read("composeResources/com.mochyy.mochymusic.shared.resources/drawable/info.xml")
        reader.read(path)

        assertEquals(2, source.reads)
    }
}

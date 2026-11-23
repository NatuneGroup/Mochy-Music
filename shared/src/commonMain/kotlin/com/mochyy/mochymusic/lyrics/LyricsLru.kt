// LyricsLru.kt

package com.mochyy.mochymusic.lyrics

/**
 * android.util.LruCache's part in the moved code: a map that keeps at most [maxSize] entries and
 * lets go of the one read longest ago. Guard it with the owner's monitor (see lyricsLocked); this
 * class does no locking of its own, the same as the LruCache it replaces was used.
 */
internal class LyricsLru<K, V>(private val maxSize: Int) {
    private val entries = LinkedHashMap<K, V>()

    fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        // put back at the young end, so the aged one is the first key
        entries[key] = value
        return value
    }

    fun put(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxSize) {
            val eldest = entries.keys.firstOrNull() ?: break
            entries.remove(eldest)
        }
    }

    fun remove(key: K): V? = entries.remove(key)

    fun evictAll() = entries.clear()

    val size: Int get() = entries.size
}

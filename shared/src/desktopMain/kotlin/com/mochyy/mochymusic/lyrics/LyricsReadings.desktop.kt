package com.mochyy.mochymusic.lyrics

import com.atilika.kuromoji.ipadic.Tokenizer
import java.text.Normalizer
import kotlin.concurrent.Volatile

// the same kuromoji IPADIC tables the Android build ships, built on demand and dropped on trims
@Volatile
private var kuromojiInstance: Tokenizer? = null
private val kuromojiLock = Any()

private fun tokenizerOrNull(): Tokenizer? =
    kuromojiInstance ?: synchronized(kuromojiLock) {
        kuromojiInstance ?: runCatching { Tokenizer() }.getOrNull()?.also { kuromojiInstance = it }
    }

internal actual fun tokenizeJapaneseText(text: String): List<JapaneseToken>? {
    val tokenizer = tokenizerOrNull() ?: return null
    return runCatching {
        tokenizer.tokenize(text).map { JapaneseToken(it.surface, it.reading, it.position) }
    }.getOrNull()
}

internal actual fun releaseJapaneseTokenizer() {
    synchronized(kuromojiLock) { kuromojiInstance = null }
}

// no pinyin table on desktop yet: Han lyrics keep their characters instead of a reading
internal actual fun hanziPinyin(char: Char): String? = null

internal actual fun normalizeNfkc(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)

internal actual fun <T> lyricsLocked(lock: Any, block: () -> T): T = synchronized(lock, block)

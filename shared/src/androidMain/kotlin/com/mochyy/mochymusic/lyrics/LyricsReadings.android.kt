package com.mochyy.mochymusic.lyrics

import com.atilika.kuromoji.ipadic.Tokenizer
import com.github.promeg.pinyinhelper.Pinyin
import java.text.Normalizer
import kotlin.concurrent.Volatile

// built on demand and dropped on memory trims so the japanese ipadic tables (~20 mb) don't stay
// pinned in ram, exactly as LyricsUtils did while it lived in :app
@Volatile
private var kuromojiInstance: Tokenizer? = null
private val kuromojiLock = Any()

private fun tokenizerOrNull(): Tokenizer? =
    kuromojiInstance ?: synchronized(kuromojiLock) {
        kuromojiInstance ?: runCatching { Tokenizer() }.getOrNull()?.also { kuromojiInstance = it }
    }

internal actual fun tokenizeJapaneseText(text: String): List<JapaneseToken>? {
    // a host-supplied dictionary wins, installed or not: compact builds ship no ipadic to fall back to
    JapaneseReadings.tokenizer?.let { installed -> return installed(text) }
    val tokenizer = tokenizerOrNull() ?: return null
    return runCatching {
        tokenizer.tokenize(text).map { JapaneseToken(it.surface, it.reading, it.position) }
    }.getOrNull()
}

internal actual fun releaseJapaneseTokenizer() {
    synchronized(kuromojiLock) { kuromojiInstance = null }
}

internal actual fun hanziPinyin(char: Char): String? =
    runCatching { Pinyin.toPinyin(char) }.getOrNull()

internal actual fun normalizeNfkc(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)

internal actual fun <T> lyricsLocked(lock: Any, block: () -> T): T = synchronized(lock, block)

package com.mochyy.mochymusic.lyrics

// iOS has neither kuromoji nor a pinyin table yet: the kana tables in LyricsUtils still romanise
// kana, and kanji or Han characters are left as they are

internal actual fun tokenizeJapaneseText(text: String): List<JapaneseToken>? = null

internal actual fun releaseJapaneseTokenizer() = Unit

internal actual fun hanziPinyin(char: Char): String? = null

// no unicode normalisation on Kotlin/Native: standard kana are unaffected, half-width stays as typed
internal actual fun normalizeNfkc(text: String): String = text

internal actual fun <T> lyricsLocked(lock: Any, block: () -> T): T = block()

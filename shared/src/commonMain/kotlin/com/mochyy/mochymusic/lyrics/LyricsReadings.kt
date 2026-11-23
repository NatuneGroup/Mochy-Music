// LyricsReadings.kt
//
// The pieces of Japanese and Chinese reading that need a dictionary or a Unicode library, behind
// the platform: kuromoji + IPADIC (or the downloaded pack, compact Android builds) and tinypinyin
// on Android and desktop, kana-only on iOS. LyricsUtils keeps every rule, table and fallback.

package com.mochyy.mochymusic.lyrics

import kotlin.concurrent.Volatile

/** One dictionary token: its text, the katakana reading kuromoji gave it, and where it starts. */
data class JapaneseToken(
    val surface: String,
    val reading: String?,
    val position: Int,
)

/**
 * A dictionary supplied by the host at runtime, for builds that don't ship IPADIC: compact Android
 * builds download it (JapaneseLanguagePackManager) and install it here. Null means the platform's
 * own tokenizer; a function returning null means the host has no dictionary at the moment, and
 * readings fall back to the kana tables.
 */
object JapaneseReadings {
    @Volatile
    var tokenizer: ((String) -> List<JapaneseToken>?)? = null
}

/** The platform's Japanese tokenizer's tokens, or null when it has no dictionary usable right now. */
internal expect fun tokenizeJapaneseText(text: String): List<JapaneseToken>?

/** Drops the dictionary from memory, as a memory trim asks. */
internal expect fun releaseJapaneseTokenizer()

/** Pinyin for one Han character, or null when the platform has no pinyin table. */
internal expect fun hanziPinyin(char: Char): String?

/** NFKC, so half-width katakana reads as the full-width pairs the tables know. */
internal expect fun normalizeNfkc(text: String): String

/** The monitor a cache is guarded with: synchronized on the JVM platforms, straight through on iOS. */
internal expect fun <T> lyricsLocked(lock: Any, block: () -> T): T

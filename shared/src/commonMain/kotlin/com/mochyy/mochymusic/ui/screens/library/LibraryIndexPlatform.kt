// LibraryIndexPlatform.kt
//
// The text work LibraryIndex.kt needs that the Kotlin common library doesn't have: Unicode
// normalization, the script of a character, and the pinyin of a Chinese title. Android has all of it
// (java.text.Normalizer, Character.UnicodeScript, ICU's AlphabeticIndex); desktop has the first two;
// iOS has neither yet, so it gets simpler versions, marked where they are.

package com.mochyy.mochymusic.ui.screens.library

/** A Chinese title's pinyin bucket: its number, and the pinyin label ("" where none is known). */
internal data class PinyinBucket(val bucket: Int, val label: String)

/** NFKC normalization: full-width Latin and half-width kana become the forms everything else uses. */
internal expect fun normalizeNfkc(text: String): String

/** NFD normalization: accents split off their base letters, so "É" leads with "E". */
internal expect fun normalizeNfd(text: String): String

/**
 * The name of [codePoint]'s Unicode script as java.lang.Character.UnicodeScript spells it ("LATIN",
 * "HAN", ...), or null when the platform doesn't know it.
 */
internal expect fun unicodeScriptNameOf(codePoint: Int): String?

/** The pinyin bucket of a Chinese title, by its first character. */
internal expect fun pinyinBucketOf(text: String): PinyinBucket

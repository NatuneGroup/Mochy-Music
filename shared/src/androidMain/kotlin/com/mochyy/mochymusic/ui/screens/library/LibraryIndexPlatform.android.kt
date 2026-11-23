package com.mochyy.mochymusic.ui.screens.library

import java.text.Normalizer

internal actual fun normalizeNfkc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)

internal actual fun normalizeNfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

internal actual fun unicodeScriptNameOf(codePoint: Int): String? =
    runCatching { Character.UnicodeScript.of(codePoint).name }.getOrNull()

/**
 * Chinese titles go by the pinyin of their first character, which only ICU knows. Built the first
 * time a Chinese title shows up, never for a library without one.
 */
private val chineseIndex by lazy {
    runCatching {
        android.icu.text.AlphabeticIndex<Any>(android.icu.util.ULocale.SIMPLIFIED_CHINESE).buildImmutableIndex()
    }.getOrNull()
}

internal actual fun pinyinBucketOf(text: String): PinyinBucket {
    val index = chineseIndex
    val bucket = index?.getBucketIndex(text) ?: 0
    return PinyinBucket(bucket, index?.getBucket(bucket)?.label.orEmpty())
}

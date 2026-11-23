package com.mochyy.mochymusic.ui.screens.library

import java.text.Normalizer

internal actual fun normalizeNfkc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)

internal actual fun normalizeNfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

internal actual fun unicodeScriptNameOf(codePoint: Int): String? =
    runCatching { Character.UnicodeScript.of(codePoint).name }.getOrNull()

// TODO: the desktop JVM has no ICU, so Chinese titles have no pinyin buckets here yet. They all share
// bucket 0 and are labelled by their first character, so the rail shows one CN section instead of
// the Android list's pinyin groups.
internal actual fun pinyinBucketOf(text: String): PinyinBucket = PinyinBucket(0, "")

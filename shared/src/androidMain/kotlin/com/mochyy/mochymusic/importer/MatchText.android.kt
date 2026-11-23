// MatchText.android.kt

package com.mochyy.mochymusic.importer

import java.text.Normalizer

// the app's own normalization, unchanged: NFKD, marks off, letters and numbers only
internal actual fun normalizeMatchText(text: String): String {
    val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(Marks, "")
    return folded.replace(NonWord, " ").trim()
}

private val Marks = Regex("\\p{M}+")
private val NonWord = Regex("[^\\p{L}\\p{N}]+")

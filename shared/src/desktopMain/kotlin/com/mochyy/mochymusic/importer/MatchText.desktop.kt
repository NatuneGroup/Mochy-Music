// MatchText.desktop.kt

package com.mochyy.mochymusic.importer

import java.text.Normalizer

// desktop runs the same JVM normalization as Android, so a match made on one lands on the same song on the other
internal actual fun normalizeMatchText(text: String): String {
    val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(Marks, "")
    return folded.replace(NonWord, " ").trim()
}

private val Marks = Regex("\\p{M}+")
private val NonWord = Regex("[^\\p{L}\\p{N}]+")

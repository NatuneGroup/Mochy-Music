// MatchText.kt

package com.mochyy.mochymusic.importer

/**
 * The folded text a match compares: lower case, accents and other marks taken off, anything that
 * isn't a letter or a number turned into one space, ends trimmed. Android and desktop fold with
 * java.text.Normalizer (NFKD), exactly as the matcher always has; iOS uses Foundation's
 * diacritic-insensitive folding, the same result for the scripts the matcher sees.
 */
internal expect fun normalizeMatchText(text: String): String

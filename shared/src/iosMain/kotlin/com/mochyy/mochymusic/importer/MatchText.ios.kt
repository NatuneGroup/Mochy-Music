// MatchText.ios.kt

package com.mochyy.mochymusic.importer

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString

// Foundation folds "é" to "e" and leaves the rest of the script alone, which is what NFKD plus
// marks-off does on the jvm; letters and numbers are kept, everything else becomes a space.
internal actual fun normalizeMatchText(text: String): String {
    val folded = (text.lowercase() as NSString).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, null)
    val out = StringBuilder()
    for (character in folded) {
        when {
            character.isLetterOrDigit() -> out.append(character)
            out.isNotEmpty() && out.last() != ' ' -> out.append(' ')
        }
    }
    return out.toString().trim()
}

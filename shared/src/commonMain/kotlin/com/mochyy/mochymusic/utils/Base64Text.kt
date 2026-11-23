// Base64Text.kt
//
// Standard base64 (the `+`/`/` alphabet, `=` padded), which the cloud logins' Basic header and the
// Shazam signature need. Kotlin's stdlib base64 is still experimental, and this has to be exactly
// what android.util.Base64 wrote, so it is written once here.

package com.mochyy.mochymusic.utils

/** standard base64 with padding, no line wrapping */
fun base64Standard(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i + 2 < bytes.size) {
        val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or
            ((bytes[i + 1].toInt() and 0xFF) shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        out.append(Alphabet[chunk ushr 18 and 0x3F])
        out.append(Alphabet[chunk ushr 12 and 0x3F])
        out.append(Alphabet[chunk ushr 6 and 0x3F])
        out.append(Alphabet[chunk and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val chunk = (bytes[i].toInt() and 0xFF) shl 16
            out.append(Alphabet[chunk ushr 18 and 0x3F])
            out.append(Alphabet[chunk ushr 12 and 0x3F])
            out.append("==")
        }
        2 -> {
            val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            out.append(Alphabet[chunk ushr 18 and 0x3F])
            out.append(Alphabet[chunk ushr 12 and 0x3F])
            out.append(Alphabet[chunk ushr 6 and 0x3F])
            out.append("=")
        }
    }
    return out.toString()
}

private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

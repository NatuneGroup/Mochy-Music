// Pkce.kt
//
// The two codes a browser sign-in with PKCE needs, so no secret ever has to ship inside mochy.
// Shared now that both sign-ins that use it (Discord, the backup clouds) live in :shared; the
// random bytes come from each platform's own secure generator.

package com.mochyy.mochymusic.utils

import okio.ByteString.Companion.toByteString

/** the platform's secure random bytes, [count] of them */
internal expect fun secureRandomBytes(count: Int): ByteArray

object Pkce {
    /** a random url-safe code, for the verifier and the check value a login is matched against */
    fun randomCode(bytes: Int): String = Base64Url.encode(secureRandomBytes(bytes))

    /** the hash of the verifier, the only part that goes out in the login link */
    fun challengeOf(verifier: String): String =
        Base64Url.encode(verifier.encodeToByteArray().toByteString().sha256().toByteArray())
}

/**
 * base64url without padding, the alphabet a PKCE code needs (and what Android's
 * `Base64.URL_SAFE or NO_PADDING or NO_WRAP` wrote before this moved to :shared).
 */
internal object Base64Url {
    private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i + 2 < bytes.size) {
            val chunk = ((bytes[i].toInt() and 0xff) shl 16) or ((bytes[i + 1].toInt() and 0xff) shl 8) or (bytes[i + 2].toInt() and 0xff)
            out.append(Alphabet[chunk ushr 18 and 0x3f])
            out.append(Alphabet[chunk ushr 12 and 0x3f])
            out.append(Alphabet[chunk ushr 6 and 0x3f])
            out.append(Alphabet[chunk and 0x3f])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val chunk = (bytes[i].toInt() and 0xff) shl 16
                out.append(Alphabet[chunk ushr 18 and 0x3f])
                out.append(Alphabet[chunk ushr 12 and 0x3f])
            }
            2 -> {
                val chunk = ((bytes[i].toInt() and 0xff) shl 16) or ((bytes[i + 1].toInt() and 0xff) shl 8)
                out.append(Alphabet[chunk ushr 18 and 0x3f])
                out.append(Alphabet[chunk ushr 12 and 0x3f])
                out.append(Alphabet[chunk ushr 6 and 0x3f])
            }
        }
        return out.toString()
    }
}

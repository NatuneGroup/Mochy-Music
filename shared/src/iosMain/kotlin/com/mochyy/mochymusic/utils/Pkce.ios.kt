// Pkce.ios.kt

package com.mochyy.mochymusic.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

// Apple's own secure generator (the one behind arc4random and the keychain)
@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    if (count == 0) return bytes
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, count.toULong(), pinned.addressOf(0))
        check(status == 0) { "No random bytes from the system" }
    }
    return bytes
}

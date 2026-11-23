// Pkce.desktop.kt

package com.mochyy.mochymusic.utils

import java.security.SecureRandom

// the JVM's own secure generator, the same call the app made
internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

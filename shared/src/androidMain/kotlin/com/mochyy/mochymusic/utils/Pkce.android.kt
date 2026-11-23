// Pkce.android.kt

package com.mochyy.mochymusic.utils

import java.security.SecureRandom

// the device's own secure generator, exactly what Pkce used while it lived in the app
internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

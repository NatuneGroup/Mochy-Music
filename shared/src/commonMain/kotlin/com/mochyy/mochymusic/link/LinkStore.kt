// LinkStore.kt

package com.mochyy.mochymusic.link

// a small named settings file the link keeps its own values in: on android it is the same
// SharedPreferences file it has always used, so nobody has to pair again after an update.
interface LinkKeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun remove(key: String)
}

expect fun linkKeyValueStore(platformContext: Any, name: String): LinkKeyValueStore

// the one thing shared code can't do itself: sealing a paired device's key with the platform's vault.
// android keeps its SecretVault, the desktop app uses its own, and PlainLinkKeySealer is for a build
// that has no vault at all.
interface LinkKeySealer {
    fun seal(text: String): String

    fun open(stored: String): String?
}

object PlainLinkKeySealer : LinkKeySealer {
    override fun seal(text: String): String = text

    override fun open(stored: String): String? = stored
}

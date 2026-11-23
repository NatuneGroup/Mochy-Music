// LinkStore.ios.kt

package com.mochyy.mochymusic.link

import platform.Foundation.NSUserDefaults

// the iPhone app keeps the same settings in its own defaults, one prefixed key per named store
actual fun linkKeyValueStore(platformContext: Any, name: String): LinkKeyValueStore = DefaultsLinkStore(name)

private class DefaultsLinkStore(private val name: String) : LinkKeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun key(key: String) = "$name.$key"

    override fun getString(key: String): String? = defaults.stringForKey(this.key(key))

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = this.key(key))
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        val stored = getString(key) ?: return default
        return stored.toBooleanStrictOrNull() ?: default
    }

    override fun putBoolean(key: String, value: Boolean) {
        // stored as "true"/"false" text, so an absent switch still falls back to its default
        defaults.setObject(value.toString(), forKey = key(key))
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(this.key(key))
    }
}

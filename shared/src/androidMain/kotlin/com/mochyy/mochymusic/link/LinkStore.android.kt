// LinkStore.android.kt

package com.mochyy.mochymusic.link

import android.content.Context

// the same shared preferences files the app has always kept, so a phone that updates finds its
// paired devices, its switches and its channel exactly where they were
actual fun linkKeyValueStore(platformContext: Any, name: String): LinkKeyValueStore =
    SharedPreferencesLinkStore((platformContext as Context).getSharedPreferences(name, Context.MODE_PRIVATE))

private class SharedPreferencesLinkStore(private val prefs: android.content.SharedPreferences) : LinkKeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

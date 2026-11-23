// the shared module bookkeeping's little store, over the same source_modules SharedPreferences file every switch and pick already lives in

package com.mochyy.mochymusic.modules

import android.content.SharedPreferences
import androidx.core.content.edit

/** [prefs] as the shared ModuleStore: a null string removes the key, as the hand-written edits did */
class SharedPreferencesModuleStore(private val prefs: SharedPreferences) : ModuleStore {

    override fun string(key: String): String? = prefs.getString(key, null)

    override fun long(key: String, fallback: Long): Long = prefs.getLong(key, fallback)

    override fun boolean(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)

    override fun putString(key: String, value: String?) = prefs.edit {
        if (value == null) remove(key) else putString(key, value)
    }

    override fun putLong(key: String, value: Long) = prefs.edit { putLong(key, value) }

    override fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    override fun remove(key: String) = prefs.edit { remove(key) }

    override fun contains(key: String): Boolean = prefs.contains(key)
}

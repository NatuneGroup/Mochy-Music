// LinkStore.desktop.kt

package com.mochyy.mochymusic.link

import java.io.File
import java.util.Properties

// the desktop app keeps the same settings as one properties file under its own data folder; a
// phone's SharedPreferences file has no equivalent on a desktop, but the keys and values match
actual fun linkKeyValueStore(platformContext: Any, name: String): LinkKeyValueStore = FileLinkStore(name)

private class FileLinkStore(name: String) : LinkKeyValueStore {
    private val file: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".mochy/link").apply { mkdirs() }.resolve("$name.properties")
    }

    private val values: Properties by lazy {
        Properties().apply { file.takeIf { it.isFile }?.inputStream()?.use { load(it) } }
    }

    @Synchronized
    private fun read(key: String): String? = values.getProperty(key)

    @Synchronized
    private fun write(key: String, value: String) {
        values.setProperty(key, value)
        file.outputStream().use { values.store(it, "Mochy link") }
    }

    @Synchronized
    private fun delete(key: String) {
        values.remove(key)
        file.outputStream().use { values.store(it, "Mochy link") }
    }

    override fun getString(key: String): String? = read(key)

    override fun putString(key: String, value: String) = write(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean = read(key)?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) = write(key, value.toString())

    override fun remove(key: String) = delete(key)
}

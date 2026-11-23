// the little bit of the web the module bookkeeping needs, so the update and release code can be shared and tested without a socket

package com.mochyy.mochymusic.modules

/** one answer: the status, and the body read up to the limit the caller gave */
class ModuleResponse(val code: Int, val body: ByteArray) {
    val ok: Boolean get() = code in 200..299

    val text: String get() = body.decodeToString()
}

/**
 * Blocking GETs, the way the app's own client makes them: the proxy, DNS and IP-version settings are the platform's
 * business. Android hands its OkHttp client over; tests hand a fake. Blocking, so callers stay off the main thread.
 */
interface ModuleHttp {
    /** GET [url]; null when the call couldn't be made or the body ran past [maxBytes] */
    fun get(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Long = ModuleHttp.MaxTextBytes): ModuleResponse?

    companion object {
        /** a manifest or a release's json is a few kilobytes; anything this big is something else */
        const val MaxTextBytes: Long = 512L * 1024
    }
}

/** the small key/value store module bookkeeping lives in: SharedPreferences on Android, a map in tests */
interface ModuleStore {
    fun string(key: String): String?
    fun long(key: String, fallback: Long = 0L): Long
    fun boolean(key: String, fallback: Boolean = false): Boolean
    fun putString(key: String, value: String?)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun contains(key: String): Boolean
}

// the modules added from a link, from a zip, and from a zip that brought its own code, as the registry keeps them between starts

package com.mochyy.mochymusic.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** a module added from a service's link: its base and the manifest it was added with */
data class SavedWebModule(val base: String, val manifest: String)

/** a module added from a zip that only changes how mochy looks */
data class SavedLocalModule(val id: String, val file: String, val manifest: String)

/** a module added from a zip that brought code of its own, in the folder it was unpacked to */
data class SavedCodeModule(val id: String, val folder: String, val file: String)

/**
 * The three lists the registry keeps beside its switches, under the same `web_modules`, `local_modules` and `code_modules`
 * keys and with the same json shapes it wrote by hand. Adding a row replaces the one with the same base (web) or id (local,
 * code) and puts the new one last; a list that isn't json reads empty, exactly as it did before.
 */
class SavedModuleLists(private val store: ModuleStore) {

    fun web(): List<SavedWebModule> = read(WebKey).map { o -> SavedWebModule(o.str("base"), o.str("manifest")) }

    fun addWeb(base: String, manifest: String) = writeWeb(web().filter { it.base != base } + SavedWebModule(base, manifest))

    fun removeWeb(base: String) = writeWeb(web().filter { it.base != base })

    /** keeps only the rows [keep] takes, writing the list once; the registry drops the websites that got in as modules with it */
    fun retainWeb(keep: (SavedWebModule) -> Boolean) = writeWeb(web().filter(keep))

    fun local(): List<SavedLocalModule> = read(LocalKey).map { o -> SavedLocalModule(o.str("id"), o.str("file"), o.str("manifest")) }

    fun addLocal(id: String, file: String, manifest: String) = writeLocal(local().filter { it.id != id } + SavedLocalModule(id, file, manifest))

    fun removeLocal(id: String) = writeLocal(local().filter { it.id != id })

    fun code(): List<SavedCodeModule> = read(CodeKey).map { o -> SavedCodeModule(o.str("id"), o.str("folder"), o.str("file")) }

    fun addCode(id: String, folder: String, file: String) = writeCode(code().filter { it.id != id } + SavedCodeModule(id, folder, file))

    fun removeCode(id: String) = writeCode(code().filter { it.id != id })

    private fun writeWeb(rows: List<SavedWebModule>) = write(WebKey, rows) { row ->
        buildJsonObject {
            put("base", row.base)
            put("manifest", row.manifest)
        }
    }

    private fun writeLocal(rows: List<SavedLocalModule>) = write(LocalKey, rows) { row ->
        buildJsonObject {
            put("id", row.id)
            put("file", row.file)
            put("manifest", row.manifest)
        }
    }

    private fun writeCode(rows: List<SavedCodeModule>) = write(CodeKey, rows) { row ->
        buildJsonObject {
            put("id", row.id)
            put("folder", row.folder)
            put("file", row.file)
        }
    }

    // a list that isn't json, or holds something that isn't a row, reads empty the way JSONArray's getJSONObject did
    private fun read(key: String): List<JsonObject> = runCatching {
        Json.parseToJsonElement(store.string(key) ?: "[]").jsonArray.map { it.jsonObject }
    }.getOrDefault(emptyList())

    private fun <T> write(key: String, rows: List<T>, toJson: (T) -> JsonObject) {
        store.putString(key, buildJsonArray { rows.forEach { add(toJson(it)) } }.toString())
    }

    // what org.json's optString gave: missing reads as blank
    private fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val WebKey = "web_modules"
        const val LocalKey = "local_modules"
        const val CodeKey = "code_modules"
    }
}

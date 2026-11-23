// the modules people can find from the modules page, read from modules/catalog.json in mochy's repo so the list grows without an app update

package com.mochyy.mochymusic.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** one row of the catalog, its feature names still as written ("audio", "lyrics", "look", "metadata") */
data class CatalogEntry(
    val name: String,
    val description: String,
    /** the module's link, what "add" checks and installs */
    val url: String,
    val adds: List<String>,
    val author: String?,
    /** a link to its icon, shown on its row before it's added */
    val icon: String? = null,
)

/** reads the catalog json: a row without a link is skipped, a name falls back to its link, only https icons are kept */
fun parseModuleCatalog(text: String): List<CatalogEntry> {
    val modules = Json.parseToJsonElement(text).jsonObject["modules"] as? JsonArray ?: return emptyList()
    return modules.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val url = item.text("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CatalogEntry(
            name = item.text("name")?.ifBlank { url } ?: url,
            description = item.text("description").orEmpty(),
            url = url,
            adds = (item["adds"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            author = item.text("author")?.takeIf { it.isNotBlank() },
            icon = item.text("icon")?.takeIf { it.startsWith("https://") },
        )
    }
}

// what org.json's optString gave: missing reads as blank
private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

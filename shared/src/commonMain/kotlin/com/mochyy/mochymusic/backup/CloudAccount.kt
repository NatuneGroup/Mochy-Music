// CloudAccount.kt

package com.mochyy.mochymusic.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** the cloud backups go to, with whatever it needs to let mochy in */
data class CloudAccount(
    val kind: CloudKind,
    // shown on the backup page: the account's email or name, or the webdav server's address
    val name: String,
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastBackupAt: Long = 0L,
    val lastBackupBytes: Long = 0L,
    // why the last scheduled backup didn't go through, so the page can say so; null once one works again
    val lastProblem: CloudProblem? = null,
) {
    fun toJson(): String = buildJsonObject {
        put("kind", kind.name)
        put("name", name)
        put("accessToken", accessToken)
        put("refreshToken", refreshToken)
        put("expiresAt", expiresAt)
        put("serverUrl", serverUrl)
        put("username", username)
        put("password", password)
        put("lastBackupAt", lastBackupAt)
        put("lastBackupBytes", lastBackupBytes)
        put("lastProblem", lastProblem?.name.orEmpty())
    }.toString()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): CloudAccount? {
            val stored = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return null
            val kind = CloudKind.entries.find { it.name == stored.string("kind") } ?: return null
            return CloudAccount(
                kind = kind,
                name = stored.string("name").orEmpty(),
                accessToken = stored.string("accessToken").orEmpty(),
                refreshToken = stored.string("refreshToken").orEmpty(),
                expiresAt = stored.long("expiresAt"),
                serverUrl = stored.string("serverUrl").orEmpty(),
                username = stored.string("username").orEmpty(),
                password = stored.string("password").orEmpty(),
                lastBackupAt = stored.long("lastBackupAt"),
                lastBackupBytes = stored.long("lastBackupBytes"),
                lastProblem = CloudProblem.entries.find { it.name == stored.string("lastProblem") },
            )
        }

        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    }
}

/**
 * Where the signed-in account is kept, as the clouds' token refresher sees it. Android reads and
 * writes its no-backup file; a platform keeps it wherever it trusts.
 */
interface CloudAccountReader {
    suspend fun current(): CloudAccount?

    /** changes the saved account in place, for new tokens and the last backup's stamp; does nothing once signed out */
    suspend fun update(change: (CloudAccount) -> CloudAccount)
}

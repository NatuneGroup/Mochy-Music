// DropboxCloud.kt

package com.mochyy.mochymusic.backup

import io.ktor.http.HttpMethod
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Path

/** backups in mochy's app folder on dropbox (apps/<app name>), the only folder the app can see */
public class DropboxCloud(private val session: OAuthSession) : BackupCloud {
    // dropbox takes at most 150 mb in one request, so every backup goes up in pieces through an upload session
    override suspend fun upload(file: Path, name: String, onProgress: (Float) -> Unit) {
        val size = sizeOf(file)
        val report = progressReporter(size, onProgress)
        var sessionId = ""
        var offset = 0L
        while (offset < size) {
            val length = minOf(Chunk, size - offset)
            val body = readSlice(file, offset, length)
            if (sessionId.isEmpty()) {
                val reply = content("upload_session/start", buildJsonObject { put("close", false) }.toString(), body)
                sessionId = jsonObjectOf(reply).text("session_id") ?: throw CloudException(CloudProblem.Busy)
            } else {
                content("upload_session/append_v2", buildJsonObject { put("cursor", cursor(sessionId, offset)); put("close", false) }.toString(), body)
            }
            offset += length
            report(offset)
        }
        val commit = buildJsonObject { put("path", "/$name"); put("mode", "add"); put("autorename", true); put("mute", true) }
        content("upload_session/finish", buildJsonObject { put("cursor", cursor(sessionId, size)); put("commit", commit) }.toString(), ByteArray(0))
    }

    override suspend fun list(): List<CloudBackupFile> {
        val found = mutableListOf<CloudBackupFile>()
        var page = jsonObjectOf(rpc("files/list_folder", buildJsonObject { put("path", "") }.toString()))
        while (true) {
            val entries = page["entries"] as? JsonArray
            for (i in 0 until (entries?.size ?: 0)) {
                val entry = entries?.get(i)?.jsonObject ?: continue
                if (entry.text(".tag") != "file" || !BackupNames.isBackup(entry.text("name").orEmpty())) continue
                found += CloudBackupFile(
                    id = entry.text("id").orEmpty(),
                    name = entry.text("name").orEmpty(),
                    bytes = entry.text("size")?.toLongOrNull() ?: 0L,
                    modifiedAt = entry.text("server_modified")?.let { parseInstant(it) } ?: 0L,
                )
            }
            if ((page["has_more"] as? kotlinx.serialization.json.JsonPrimitive)?.content != "true") break
            page = jsonObjectOf(rpc("files/list_folder/continue", buildJsonObject { put("cursor", page.text("cursor").orEmpty()) }.toString()))
        }
        return found
    }

    override suspend fun download(backup: CloudBackupFile, into: Path, onProgress: (Float) -> Unit) {
        val response = session.send { token ->
            CloudRequest(
                method = HttpMethod.Post,
                url = "$ContentApi/files/download",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Dropbox-API-Arg" to buildJsonObject { put("path", backup.id) }.toString(),
                ),
                body = ByteArray(0),
            )
        }
        if (response.status.value !in 200..299) {
            val body = CloudHttp.read(response).body
            throw CloudException(problemOf(response.status.value, body))
        }
        response.saveTo(into, backup.bytes, onProgress)
    }

    override suspend fun delete(backup: CloudBackupFile) {
        rpc("files/delete_v2", buildJsonObject { put("path", backup.id) }.toString())
    }

    // the calls that take json and answer json
    private suspend fun rpc(path: String, args: String): String {
        val reply = session.text { token ->
            CloudRequest(
                method = HttpMethod.Post,
                url = "$Api/$path",
                headers = mapOf("Authorization" to "Bearer $token"),
                body = args.encodeToByteArray(),
                contentType = CloudHttp.Json,
            )
        }
        if (!reply.ok) throw CloudException(problemOf(reply.code, reply.body))
        return reply.body
    }

    // the calls that carry file bytes, with their arguments in a header instead
    private suspend fun content(path: String, args: String, body: ByteArray): String {
        val reply = session.text { token ->
            CloudRequest(
                method = HttpMethod.Post,
                url = "$ContentApi/$path",
                headers = mapOf("Authorization" to "Bearer $token", "Dropbox-API-Arg" to args),
                body = body,
                contentType = CloudHttp.Binary,
            )
        }
        if (!reply.ok) throw CloudException(problemOf(reply.code, reply.body))
        return reply.body
    }

    private fun cursor(sessionId: String, offset: Long) =
        buildJsonObject { put("session_id", sessionId); put("offset", offset) }

    // dropbox answers most trouble with 409 and says what it was in the body
    private fun problemOf(code: Int, body: String): CloudProblem = when {
        code == 409 && "insufficient_space" in body -> CloudProblem.Full
        code == 409 && "not_found" in body -> CloudProblem.NotFound
        code == 409 -> CloudProblem.Busy
        else -> CloudHttp.problemOf(code)
    }

    private companion object {
        const val Api = "https://api.dropboxapi.com/2"
        const val ContentApi = "https://content.dropboxapi.com/2"
        const val Chunk = 32L * 1024 * 1024
    }
}

/** a dropbox or onedrive timestamp, as epoch millis; 0 when it isn't one */
internal fun parseInstant(text: String): Long =
    runCatching { Instant.parse(text).toEpochMilliseconds() }.getOrDefault(0L)

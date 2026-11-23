// OneDriveCloud.kt

package com.mochyy.mochymusic.backup

import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.Path

/** backups in mochy's app folder on onedrive (apps/<app name>), the only folder it asked for */
public class OneDriveCloud(private val session: OAuthSession) : BackupCloud {
    // anything past a few mb has to go through an upload session, so every backup does, in pieces
    override suspend fun upload(file: Path, name: String, onProgress: (Float) -> Unit) {
        val size = sizeOf(file)
        val created = session.text { token ->
            CloudRequest(
                method = HttpMethod.Post,
                url = "$AppFolder:/$name:/createUploadSession",
                headers = mapOf("Authorization" to "Bearer $token"),
                body = buildJsonObject {
                    put("item", buildJsonObject { put("@microsoft.graph.conflictBehavior", "rename") })
                }.toString().encodeToByteArray(),
                contentType = CloudHttp.Json,
            )
        }
        if (!created.ok) CloudHttp.fail(created.code)
        val uploadUrl = jsonObjectOf(created.body).text("uploadUrl") ?: throw CloudException(CloudProblem.Busy)
        val report = progressReporter(size, onProgress)
        var offset = 0L
        while (offset < size) {
            val length = minOf(Chunk, size - offset)
            val slice = readSlice(file, offset, length)
            // the upload link carries its own permission, a login header on it gets turned down
            val reply = CloudHttp.read(
                CloudHttp.send(
                    CloudRequest(
                        method = HttpMethod.Put,
                        url = uploadUrl,
                        headers = mapOf("Content-Range" to "bytes $offset-${offset + length - 1}/$size"),
                        body = slice,
                        contentType = CloudHttp.Binary,
                    ),
                ),
            )
            if (!reply.ok) CloudHttp.fail(reply.code)
            offset += length
            report(offset)
        }
    }

    override suspend fun list(): List<CloudBackupFile> {
        val found = mutableListOf<CloudBackupFile>()
        var next: String? = "$AppFolder/children".let { base ->
            // the query is part of the link, so it is added here rather than sent as a header
            val builder = URLBuilder(base)
            builder.parameters.append("\$select", "id,name,size,lastModifiedDateTime,file")
            builder.parameters.append("\$top", "200")
            builder.buildString()
        }
        while (next != null) {
            val url = next
            val reply = session.text { token ->
                CloudRequest(url = url, method = HttpMethod.Get, headers = mapOf("Authorization" to "Bearer $token"))
            }
            // a brand new app folder only appears with the first upload, until then there's simply nothing in it
            if (reply.code == 404) return found
            if (!reply.ok) CloudHttp.fail(reply.code)
            val page = jsonObjectOf(reply.body)
            val items = page["value"] as? JsonArray
            for (i in 0 until (items?.size ?: 0)) {
                val item = items?.get(i)?.jsonObject ?: continue
                if (item["file"] == null || !BackupNames.isBackup(item.text("name").orEmpty())) continue
                found += CloudBackupFile(
                    id = item.text("id").orEmpty(),
                    name = item.text("name").orEmpty(),
                    bytes = item.text("size")?.toLongOrNull() ?: 0L,
                    modifiedAt = item.text("lastModifiedDateTime")?.let { parseInstant(it) } ?: 0L,
                )
            }
            next = page.text("@odata.nextLink").orEmpty().ifEmpty { null }
        }
        return found
    }

    // onedrive answers with a redirect to a short-lived link, the client follows it and drops the login header on the way
    override suspend fun download(backup: CloudBackupFile, into: Path, onProgress: (Float) -> Unit) {
        val response = session.send { token ->
            CloudRequest(
                method = HttpMethod.Get,
                url = "$Drive/items/${backup.id}/content",
                headers = mapOf("Authorization" to "Bearer $token"),
            )
        }
        if (response.status.value !in 200..299) CloudHttp.fail(response.status.value)
        response.saveTo(into, backup.bytes, onProgress)
    }

    override suspend fun delete(backup: CloudBackupFile) {
        val reply = session.text { token ->
            CloudRequest(
                method = HttpMethod.Delete,
                url = "$Drive/items/${backup.id}",
                headers = mapOf("Authorization" to "Bearer $token"),
            )
        }
        if (!reply.ok && reply.code != 404) CloudHttp.fail(reply.code)
    }

    private companion object {
        const val Drive = "https://graph.microsoft.com/v1.0/me/drive"
        const val AppFolder = "$Drive/special/approot"
        // microsoft wants every piece but the last in steps of 320 kib, this is 32 of them
        const val Chunk = 32L * 320 * 1024
    }
}

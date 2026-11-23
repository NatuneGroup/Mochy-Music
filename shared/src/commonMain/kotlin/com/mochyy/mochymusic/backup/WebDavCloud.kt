// WebDavCloud.kt

package com.mochyy.mochymusic.backup

import com.mochyy.mochymusic.utils.base64Standard
import io.ktor.http.Charsets
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLPathPart
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import okio.Path

/** backups in a mochy backups folder on any webdav server: nextcloud, owncloud, pcloud, koofr, yandex disk, a nas at home */
public class WebDavCloud(account: CloudAccount) : BackupCloud {
    private val folder: String = folderOf(requireNotNull(addressOf(account.serverUrl)) { "saved webdav address doesn't parse" })
    private val login = "Basic " + base64Standard("${account.username}:${account.password}".encodeToByteArray())

    override suspend fun upload(file: Path, name: String, onProgress: (Float) -> Unit) {
        // the folder may have been deleted on the server since the account was connected
        makeFolder(folder, login)
        val size = sizeOf(file)
        val reply = CloudHttp.read(
            CloudHttp.send(
                CloudRequest(
                    method = HttpMethod.Put,
                    url = fileUrl(name),
                    headers = mapOf("Authorization" to login),
                    body = readSlice(file, 0L, size),
                    contentType = CloudHttp.Binary,
                ),
            ),
        )
        // the whole file goes in one body, so the bar fills when it's sent
        onProgress(1f)
        if (!reply.ok) throw CloudException(problemOf(reply.code))
    }

    override suspend fun list(): List<CloudBackupFile> {
        val reply = CloudHttp.read(
            CloudHttp.send(
                CloudRequest(
                    method = Propfind,
                    url = folder,
                    headers = mapOf("Authorization" to login, "Depth" to "1"),
                    body = PropfindBody.encodeToByteArray(),
                    contentType = XmlType,
                ),
            ),
        )
        if (reply.code == 404) return emptyList()
        if (!reply.ok) throw CloudException(problemOf(reply.code))
        return parseListing(reply.body).filter { BackupNames.isBackup(it.name) }
    }

    override suspend fun download(backup: CloudBackupFile, into: Path, onProgress: (Float) -> Unit) {
        val response = CloudHttp.send(
            CloudRequest(method = HttpMethod.Get, url = fileUrl(backup.id), headers = mapOf("Authorization" to login)),
        )
        if (response.status.value !in 200..299) throw CloudException(problemOf(response.status.value))
        response.saveTo(into, backup.bytes, onProgress)
    }

    override suspend fun delete(backup: CloudBackupFile) {
        val reply = CloudHttp.read(
            CloudHttp.send(
                CloudRequest(method = HttpMethod.Delete, url = fileUrl(backup.id), headers = mapOf("Authorization" to login)),
            ),
        )
        if (!reply.ok && reply.code != 404) throw CloudException(problemOf(reply.code))
    }

    // the folder ends in a slash, so the name goes after it as its own piece
    private fun fileUrl(name: String): String = folder + encodeURLPathPart(name)

    // each <response> in the folder's listing, the folder itself and anything inside that's a folder left out
    private fun parseListing(xml: String): List<CloudBackupFile> {
        val found = mutableListOf<CloudBackupFile>()
        for (block in Response.findAll(xml)) {
            val body = block.value
            if (Collection.containsMatchIn(body)) continue
            val href = Tag("href").find(body)?.groupValues?.get(1)?.trim().orEmpty()
            val name = percentDecode(href.trimEnd('/').substringAfterLast('/'))
            if (name.isEmpty()) continue
            val bytes = Tag("getcontentlength").find(body)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L
            val modified = Tag("getlastmodified").find(body)?.groupValues?.get(1)?.trim()?.let(::parseHttpDate) ?: 0L
            found += CloudBackupFile(id = name, name = name, bytes = bytes, modifiedAt = modified)
        }
        return found
    }

    private fun parseHttpDate(text: String): Long =
        runCatching {
            // Wed, 21 Oct 2015 07:28:00 GMT: the only form webdav servers send
            val parts = text.removeSuffix("GMT").trim().split(" ")
            val day = parts[1].toInt()
            val month = Months.indexOf(parts[2]) + 1
            val year = parts[3].toInt()
            val time = parts[4].split(":")
            LocalDateTime(year, month, day, time[0].toInt(), time[1].toInt(), time[2].toInt())
                .toInstant(TimeZone.UTC)
                .toEpochMilliseconds()
        }.getOrDefault(0L)

    companion object {
        private const val FolderName = "Mochy Backups"
        private val XmlType = ContentType.Application.Xml.withCharset(Charsets.UTF_8)
        private val Propfind = HttpMethod("PROPFIND")
        private const val PropfindBody = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""

        private val Months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        private val Response = Regex("<(?:\\w+:)?response\\b[\\s\\S]*?</(?:\\w+:)?response>")
        private val Collection = Regex("<(?:\\w+:)?collection\\b")
        private fun Tag(name: String) = Regex("<(?:\\w+:)?$name\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?$name>")

        /** what the user typed as a server address, https added when they left it off and a slash on the end, or null when it isn't one */
        fun addressOf(typed: String): String? {
            val trimmed = typed.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = runCatching { Url(withScheme) }.getOrNull() ?: return null
            if (url.host.isBlank()) return null
            return URLBuilder(url).apply {
                if (!encodedPath.endsWith("/")) appendPathSegments("")
            }.buildString()
        }

        /** the server's name, for the account row: what `HttpUrl.host` gave */
        fun hostOf(url: String): String = runCatching { Url(url).host }.getOrDefault(url)

        /** checks the address and login, then makes the backup folder; fails with what went wrong */
        suspend fun connect(address: String, username: String, password: String) {
            val login = "Basic " + base64Standard("$username:$password".encodeToByteArray())
            val probe = CloudHttp.read(
                CloudHttp.send(
                    CloudRequest(
                        method = Propfind,
                        url = address,
                        headers = mapOf("Authorization" to login, "Depth" to "0"),
                        body = PropfindBody.encodeToByteArray(),
                        contentType = XmlType,
                    ),
                ),
            )
            when {
                probe.code == 401 || probe.code == 403 -> throw CloudException(CloudProblem.WrongLogin)
                probe.code == 404 -> throw CloudException(CloudProblem.NotFound)
                // a plain web page answers propfind with an error or a page instead of a listing
                probe.code != 207 -> throw CloudException(CloudProblem.NotWebDav)
            }
            makeFolder(folderOf(address), login)
        }

        private fun folderOf(address: String): String =
            URLBuilder(address).apply { appendPathSegments(FolderName, "") }.buildString()

        // 405 means it's already there, which is just as good
        private suspend fun makeFolder(folder: String, login: String) {
            val reply = CloudHttp.read(
                CloudHttp.send(
                    CloudRequest(
                        method = HttpMethod("MKCOL"),
                        url = folder,
                        headers = mapOf("Authorization" to login),
                    ),
                ),
            )
            if (!reply.ok && reply.code != 405) throw CloudException(problemOf(reply.code))
        }

        private fun problemOf(code: Int): CloudProblem = when (code) {
            401, 403 -> CloudProblem.WrongLogin
            else -> CloudHttp.problemOf(code)
        }

        private fun percentDecode(value: String): String {
            if ('%' !in value) return value
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val char = value[i]
                if (char == '%' && i + 2 < value.length) {
                    val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (byte == null) {
                        out.append(char)
                        i++
                    } else {
                        out.append(byte.toChar())
                        i += 3
                    }
                } else {
                    out.append(char)
                    i++
                }
            }
            return out.toString()
        }
    }
}

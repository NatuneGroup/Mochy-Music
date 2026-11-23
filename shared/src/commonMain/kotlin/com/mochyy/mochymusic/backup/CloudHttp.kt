// CloudHttp.kt
//
// The one connection the backup clouds share, on Ktor: a request described by the caller, the
// reply as text or streamed straight into a file, and the codes every cloud reads the same way.
// Uploads read their pieces from the zip with okio, so a big backup never sits in memory whole.

package com.mochyy.mochymusic.backup

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.contentLength
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import okio.Buffer
import okio.FileSystem
import okio.Path

expect fun cloudHttpClient(): HttpClient

/** one request to a cloud: the method, the address, the headers it needs and a body, if any. */
class CloudRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: ContentType? = null,
    /** a form body (the token endpoints take one); the other fields are left empty then */
    val form: Parameters? = null,
)

object CloudHttp {
    val Binary: ContentType = ContentType.Application.OctetStream
    val Json: ContentType = ContentType.Application.Json

    class Reply(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    suspend fun send(request: CloudRequest): HttpResponse = cloudHttpClient().request(request.url) {
        method = request.method
        request.headers.forEach { (name, value) -> header(name, value) }
        val form = request.form
        val body = request.body
        when {
            form != null -> setBody(FormDataContent(form))
            body != null -> {
                request.contentType?.let { contentType(it) }
                setBody(body)
            }
        }
    }

    /** reads the whole body; a streaming download must go through [saveTo] instead */
    suspend fun read(response: HttpResponse): Reply = Reply(response.status.value, response.bodyAsText())

    /** throws a response away after a failed attempt, so its connection is free for the retry */
    suspend fun drain(response: HttpResponse) {
        runCatching { response.bodyAsText() }
    }

    /** the codes every cloud shares; anything else means the cloud itself is having a bad moment */
    fun problemOf(code: Int): CloudProblem = when (code) {
        401, 403 -> CloudProblem.SignedOut
        404, 409 -> CloudProblem.NotFound
        507 -> CloudProblem.Full
        else -> CloudProblem.Busy
    }

    fun fail(code: Int): Nothing = throw CloudException(problemOf(code))
}

/** a piece of [path] from [offset], [length] bytes, read for an upload body. */
internal fun readSlice(path: Path, offset: Long, length: Long): ByteArray {
    val handle = FileSystem.SYSTEM.openReadOnly(path)
    try {
        val buffer = Buffer()
        var position = offset
        var remaining = length
        while (remaining > 0) {
            val read = handle.read(position, buffer, remaining)
            if (read == -1L) break
            position += read
            remaining -= read
        }
        return buffer.readByteArray()
    } finally {
        handle.close()
    }
}

/** the size of a file on the phone, or 0 when it isn't there. */
internal fun sizeOf(path: Path): Long = FileSystem.SYSTEM.metadataOrNull(path)?.size ?: 0L

/** progress as a share of [total], the way every cloud reports it */
internal fun progressReporter(total: Long, onProgress: (Float) -> Unit): (Long) -> Unit =
    { sent -> onProgress((sent.toDouble() / total).toFloat().coerceIn(0f, 1f)) }

/** streams a download into [into], with [expected] as the size when the server doesn't say */
internal suspend fun HttpResponse.saveTo(into: Path, expected: Long, onProgress: (Float) -> Unit) {
    val total = contentLength()?.takeIf { it > 0 } ?: expected
    val handle = FileSystem.SYSTEM.openReadWrite(into)
    try {
        handle.resize(0)
        val sink = handle.sink().buffer()
        try {
            val channel = bodyAsChannel()
            val buffer = ByteArray(128 * 1024)
            var copied = 0L
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                sink.write(buffer, 0, read)
                copied += read
                if (total > 0) onProgress((copied.toDouble() / total).toFloat().coerceIn(0f, 1f))
            }
            sink.flush()
        } finally {
            sink.close()
        }
    } finally {
        handle.close()
    }
}

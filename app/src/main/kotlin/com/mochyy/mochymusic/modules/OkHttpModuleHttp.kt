// the shared module update code's web calls, made with the same OkHttp client, proxy and pools the registry always used

package com.mochyy.mochymusic.modules

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

/** blocking GETs through [client]: same dispatcher, connection pool and timeouts as the registry's other calls */
class OkHttpModuleHttp(private val client: OkHttpClient) : ModuleHttp {

    override fun get(url: String, headers: Map<String, String>, maxBytes: Long): ModuleResponse? = runCatching {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var tooBig = false
            response.body.byteStream().use { input ->
                while (!tooBig) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    // a release asset can be anything, so stop reading once it's past what the caller can take
                    if (out.size() > maxBytes) tooBig = true
                }
            }
            if (tooBig) null else ModuleResponse(response.code, out.toByteArray())
        }
    }.getOrNull()
}

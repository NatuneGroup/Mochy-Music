// DiscordDownloads.kt
//
// The OkHttp client the Discord pictures use: the canvas gif's frames, the rotating artwork's
// still and the uploads to the free hosts. They read and write raw bytes and keep using OkHttp's
// own calls, so they keep the client (and proxy) they always had; the Discord API itself now
// goes through the shared Ktor client (DiscordHttp).

package com.mochyy.mochymusic.discord

import com.mochyy.mochymusic.utils.AppProxy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object DiscordDownloads {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(AppProxy.dispatcher)
            .connectionPool(AppProxy.connectionPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

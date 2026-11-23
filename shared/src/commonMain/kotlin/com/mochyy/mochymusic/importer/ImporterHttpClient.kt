// ImporterHttpClient.kt

package com.mochyy.mochymusic.importer

import io.ktor.client.HttpClient

/**
 * The client link and account imports send their requests with: the same 15s connect / 30s read
 * waits, redirects followed by the engine, and no exception on a 4xx (the readers turn the status
 * into a message). Each platform picks the engine it already ships.
 */
internal expect fun createImporterHttpClient(): HttpClient

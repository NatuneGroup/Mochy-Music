// AudioscrobblerHttp.kt
//
// The client every scrobbling request goes through. It used to be built with OkHttp right here,
// which only exists on Android and the desktop; each platform now installs its own engine through
// this one factory (OkHttp there, NSURLSession on iOS), with the same timeouts as before.

package com.music.lastfm

import io.ktor.client.HttpClient

expect fun audioscrobblerHttpClient(): HttpClient

// TrackKeys.kt

package com.mochyy.mochymusic.importer

/** what re-sync knows a service's song by: exact for youtube songs, else cleaned title and first artist, which match whether read through a link or an account */
fun ImportedTrack.trackKey(): String {
    (videoId ?: ytItem?.id)?.let { return youTubeKey(it) }
    val firstArtist = TrackMatcher.splitArtists(artist).firstOrNull().orEmpty()
    return "${TrackMatcher.normalizeTitle(title)}|${TrackMatcher.normalizeText(firstArtist)}"
}

fun youTubeKey(videoId: String) = "yt:$videoId"

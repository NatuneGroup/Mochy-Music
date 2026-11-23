package com.mochyy.mochymusic.eq.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.vorbis.VorbisComment

/**
 * Reads the current song's replaygain out of its track metadata. The Android-only half of
 * [ReplayGainTags]: media3 knows the vorbis comments, the shared class knows what they mean.
 */
@OptIn(UnstableApi::class)
fun ReplayGainTags.readTracks(tracks: Tracks) {
    val comments = ArrayList<Pair<String, String>>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val metadata = group.getTrackFormat(i).metadata ?: continue
            for (e in 0 until metadata.length()) {
                val entry = metadata.get(e) as? VorbisComment ?: continue
                comments += entry.key to entry.value
            }
        }
    }
    update(comments)
}

package com.mochyy.mochymusic.playback

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.constants.QueuePreventDuplicatesKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get

/**
 * With "prevent duplicates" on, takes out the songs still to come that match [items], so the
 * re-added song moves to its new place instead of showing up twice. Songs already played and the
 * song playing now are never touched.
 */
fun MusicService.removeUpcomingDuplicates(items: List<MediaItem>) {
    if (!dataStore.get(QueuePreventDuplicatesKey, false)) return
    val ids = items.mapTo(HashSet()) { it.mediaId }
    // back to front, so the positions still to check don't move while removing
    for (i in player.mediaItemCount - 1 downTo player.currentMediaItemIndex + 1) {
        if (player.getMediaItemAt(i).mediaId in ids) player.removeMediaItem(i)
    }
}

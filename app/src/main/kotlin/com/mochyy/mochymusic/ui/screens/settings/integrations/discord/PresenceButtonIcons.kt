// PresenceButtonIcons.kt
//
// The picture each "opens" choice shows on the Discord page. It stays in the app because a
// drawable id only exists here; the shared PresenceButtonTarget keeps just the labels.

package com.mochyy.mochymusic.ui.screens.settings.integrations.discord

import androidx.annotation.DrawableRes
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.discord.PresenceButtonTarget

@DrawableRes
fun PresenceButtonTarget.iconRes(): Int = when (this) {
    PresenceButtonTarget.YOUTUBE_MUSIC -> R.drawable.provider_youtube_music
    PresenceButtonTarget.SPOTIFY -> R.drawable.provider_spotify
    PresenceButtonTarget.APPLE_MUSIC -> R.drawable.provider_apple_music
    PresenceButtonTarget.TIDAL -> R.drawable.provider_tidal
    PresenceButtonTarget.MOCHY -> R.drawable.github
}

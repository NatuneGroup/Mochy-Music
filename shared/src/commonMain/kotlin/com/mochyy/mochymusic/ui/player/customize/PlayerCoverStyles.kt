// PlayerCoverStyles.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.DiscNameKey
import com.mochyy.mochymusic.constants.DiscRealisticModeKey
import com.mochyy.mochymusic.constants.DiscRotatingAnimationKey
import com.mochyy.mochymusic.constants.PlayVideoBackgroundKey
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.constants.PlayerCoverZoomKey
import com.mochyy.mochymusic.constants.YtVideoBackgroundLyricsSyncKey
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.crop
import com.mochyy.mochymusic.shared.resources.edit
import com.mochyy.mochymusic.shared.resources.lyrics
import com.mochyy.mochymusic.shared.resources.palette
import com.mochyy.mochymusic.shared.resources.ps_cover_animated_canvas
import com.mochyy.mochymusic.shared.resources.ps_cover_animated_canvas_desc
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_album
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_big_full
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_big_label
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_name
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_name_desc
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_name_placeholder
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_small_full
import com.mochyy.mochymusic.shared.resources.ps_cover_disc_small_label
import com.mochyy.mochymusic.shared.resources.ps_cover_edge_to_edge
import com.mochyy.mochymusic.shared.resources.ps_cover_for_disc
import com.mochyy.mochymusic.shared.resources.ps_cover_realistic
import com.mochyy.mochymusic.shared.resources.ps_cover_rotating
import com.mochyy.mochymusic.shared.resources.ps_cover_squared
import com.mochyy.mochymusic.shared.resources.ps_cover_timestamp_matching
import com.mochyy.mochymusic.shared.resources.ps_cover_timestamp_matching_desc
import com.mochyy.mochymusic.shared.resources.ps_cover_turntable
import com.mochyy.mochymusic.shared.resources.ps_cover_yt_video
import com.mochyy.mochymusic.shared.resources.ps_cover_yt_video_desc
import com.mochyy.mochymusic.shared.resources.ps_cover_zoom
import com.mochyy.mochymusic.shared.resources.ps_cover_zoom_desc
import com.mochyy.mochymusic.shared.resources.refresh
import com.mochyy.mochymusic.shared.resources.slow_motion_video
import com.mochyy.mochymusic.shared.resources.sparks
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

val PlayerCoverStyle.isDisc: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> false
        else -> true
    }

val PlayerCoverStyle.usesBoxedStage: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE,
        PlayerCoverStyle.DISC_BIG_FULL,
        PlayerCoverStyle.DISC_BIG_LABEL,
        PlayerCoverStyle.TURNTABLE,
        -> false
        else -> true
    }

val PlayerCoverStyle.isBigDisc: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_FULL || this == PlayerCoverStyle.DISC_BIG_LABEL

val PlayerCoverStyle.hasTonearm: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_LABEL

@get:Composable
val PlayerCoverStyle.displayName: String
    get() = stringResource(
        when (this) {
            PlayerCoverStyle.EDGE_TO_EDGE -> Res.string.ps_cover_edge_to_edge
            PlayerCoverStyle.SQUARED -> Res.string.ps_cover_squared
            PlayerCoverStyle.DISC_SMALL_FULL -> Res.string.ps_cover_disc_small_full
            PlayerCoverStyle.DISC_SMALL_LABEL -> Res.string.ps_cover_disc_small_label
            PlayerCoverStyle.DISC_BIG_FULL -> Res.string.ps_cover_disc_big_full
            PlayerCoverStyle.DISC_BIG_LABEL -> Res.string.ps_cover_disc_big_label
            PlayerCoverStyle.DISC_ALBUM -> Res.string.ps_cover_disc_album
            PlayerCoverStyle.TURNTABLE -> Res.string.ps_cover_turntable
        }
    )

sealed interface CoverOption {
    val title: String
    val description: String
    val icon: DrawableResource

    data class Switch(
        override val title: String,
        override val description: String,
        override val icon: DrawableResource,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        val inverted: Boolean = false,
        val parent: Switch? = null,
    ) : CoverOption

    data class Text(
        override val title: String,
        override val description: String,
        override val icon: DrawableResource,
        val key: Preferences.Key<String>,
        val placeholder: String,
    ) : CoverOption

    data class Slider(
        override val title: String,
        override val description: String,
        override val icon: DrawableResource,
        val key: Preferences.Key<Float>,
        val default: Float,
    ) : CoverOption
}

@Composable
private fun animatedCanvasOption() = CoverOption.Switch(
    title = stringResource(Res.string.ps_cover_animated_canvas),
    description = stringResource(Res.string.ps_cover_animated_canvas_desc),
    icon = Res.drawable.sparks,
    key = CanvasThumbnailAnimationKey,
    default = true,
)

@Composable
private fun ytVideoOption() = CoverOption.Switch(
    title = stringResource(Res.string.ps_cover_yt_video),
    description = stringResource(Res.string.ps_cover_yt_video_desc),
    icon = Res.drawable.slow_motion_video,
    key = PlayVideoBackgroundKey,
    default = false,
)

@Composable
private fun timestampMatchingOption(parent: CoverOption.Switch) = CoverOption.Switch(
    title = stringResource(Res.string.ps_cover_timestamp_matching),
    description = stringResource(Res.string.ps_cover_timestamp_matching_desc),
    icon = Res.drawable.lyrics,
    key = YtVideoBackgroundLyricsSyncKey,
    default = true,
    parent = parent,
)

@Composable
private fun discOptions() = listOf(
    CoverOption.Switch(
        title = stringResource(Res.string.ps_cover_rotating),
        description = stringResource(Res.string.ps_cover_for_disc),
        icon = Res.drawable.refresh,
        key = DiscRotatingAnimationKey,
        default = true,
    ),
    CoverOption.Switch(
        title = stringResource(Res.string.ps_cover_realistic),
        description = stringResource(Res.string.ps_cover_for_disc),
        icon = Res.drawable.palette,
        key = DiscRealisticModeKey,
        default = true,
    ),
    CoverOption.Text(
        title = stringResource(Res.string.ps_cover_disc_name),
        description = stringResource(Res.string.ps_cover_disc_name_desc),
        icon = Res.drawable.edit,
        key = DiscNameKey,
        placeholder = stringResource(Res.string.ps_cover_disc_name_placeholder),
    ),
)

@Composable
private fun coverZoomOption() = CoverOption.Slider(
    title = stringResource(Res.string.ps_cover_zoom),
    description = stringResource(Res.string.ps_cover_zoom_desc),
    icon = Res.drawable.crop,
    key = PlayerCoverZoomKey,
    default = 1f,
)

@get:Composable
val PlayerCoverStyle.options: List<CoverOption>
    get() = when (this) {
        PlayerCoverStyle.SQUARED -> listOf(coverZoomOption(), animatedCanvasOption())
        PlayerCoverStyle.EDGE_TO_EDGE -> {
            val ytVideo = ytVideoOption()
            listOf(coverZoomOption(), ytVideo, timestampMatchingOption(ytVideo), animatedCanvasOption())
        }
        else -> discOptions()
    }

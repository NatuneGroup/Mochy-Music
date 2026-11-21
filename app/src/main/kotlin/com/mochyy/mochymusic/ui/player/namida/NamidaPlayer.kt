// The namida player style: a rounded panel tinted by the cover's colour, the artwork big in the
// middle with the song's words beneath it, a waveform for a seek bar, and the queue as a page
// that slides up over the player. Its look is its own — Lexend type, its own inks, its own
// buttons — none of it borrowed from the player's other looks.

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.StyleFrame
import com.mochyy.mochymusic.ui.player.styles.StyleMenu
import com.mochyy.mochymusic.ui.player.styles.StyleTones
import com.mochyy.mochymusic.ui.player.styles.rememberDurationMs
import com.mochyy.mochymusic.ui.player.styles.rememberLivePosition
import com.mochyy.mochymusic.ui.player.styles.rememberPlayedSeconds
import com.mochyy.mochymusic.ui.player.styles.rememberStyleLyrics
import com.mochyy.mochymusic.ui.player.styles.rememberStyleTrack
import com.mochyy.mochymusic.ui.player.styles.styleInsets
import com.mochyy.mochymusic.ui.player.styles.whileStyleInteractive
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.launch

/** namida's panel: the corner it keeps when the player stands open. */
private val NamidaPanelCorner = 17.dp

/** how much of the width the artwork takes, and the room the words keep at the sides. */
private const val NamidaArtworkWidthFraction = 0.80f
private val NamidaSidePadding = 20.dp

/** the play button's circle and the icon inside it, namida's sizes. */
private val NamidaPlayCircle = 68.dp
private val NamidaPlayIcon = 40.dp
private val NamidaSkipIcon = 32.dp
private val NamidaControlGap = 14.dp

/** how far a tap on the time labels seeks, namida's seek Duration default. */
private const val NamidaTapSeekMs = 5_000L

/** The namida look on the player. [preview] draws it as a selector card, still and untouchable. */
@Composable
fun NamidaPlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val seed = LocalArtworkColor.current
    val palette = remember(seed) { namidaPalette(seed) }
    val connection = LocalPlayerConnection.current
    val track = rememberStyleTrack()
    val position = rememberLivePosition(animated = !preview)
    val durationMs = rememberDurationMs()
    val lyrics = rememberStyleLyrics(readings = !preview)
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val trackInfo = connection?.uiState?.trackInfo?.collectAsState()?.value
    val playing = transport?.isPlaying == true
    val sheet = LocalPlayerBottomSheetState.current
    val interactive = LocalStyleInteractive.current
    var lyricsOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    val tones = StyleTones(
        played = palette.accent,
        remaining = palette.surface,
        ink = palette.onSurface,
        inkSoft = palette.onSurfaceSoft,
        chip = palette.secondaryContainer,
    )

    CompositionLocalProvider(LocalNamidaPalette provides palette) {
        StyleFrame(
            preview, modifier, tones,
            background = {
                // namida's panel: the surface, with its light-to-shade overlay of the song's colour
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(palette.surface)
                            drawRect(
                                Brush.verticalGradient(
                                    0f to palette.onSurface.copy(alpha = 0.39f).compositeOver(palette.accent.copy(alpha = 0.38f)),
                                    1f to palette.onSurface.copy(alpha = 0.16f).compositeOver(palette.accent.copy(alpha = 0.10f)),
                                ),
                            )
                        },
                )
            },
        ) { landscape, menuOpen, setMenuOpen ->
            val insets = styleInsets(landscape)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val artworkSize = (maxWidth * NamidaArtworkWidthFraction).coerceAtMost(maxHeight * 0.46f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = insets.top + 8.dp,
                            start = insets.start + NamidaSidePadding,
                            end = insets.end + NamidaSidePadding,
                            bottom = insets.bottom + 8.dp,
                        ),
                ) {
                    // ── the top bar: collapse chevron, the count and the album, the more button ──
                    val queueInfo = connection?.queueItems?.collectAsState()?.value
                    val currentIndex = connection?.currentWindowIndex?.collectAsState()?.value ?: -1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = null,
                            tint = palette.onSecondaryContainer,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) { sheet?.collapseSoft() }
                                .padding(8.dp),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            if (queueInfo != null && queueInfo.isNotEmpty()) {
                                Text(
                                    text = "${(currentIndex + 1).coerceIn(1, queueInfo.size)}/${queueInfo.size}",
                                    color = palette.onSecondaryContainer.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontFamily = NamidaFontFamily,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (track.album.isNotBlank()) {
                                Text(
                                    text = track.album,
                                    color = palette.onSecondaryContainer.copy(alpha = 0.9f),
                                    fontSize = 16.sp,
                                    fontFamily = NamidaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(palette.secondaryContainer)
                                .clickable(enabled = !preview) { actions.onOtherMenu() },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = null,
                                tint = palette.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(Modifier.weight(0.5f))

                    // ── the artwork, or the lyrics in its place ──
                    AnimatedContent(
                        targetState = lyricsOpen && lyrics.lines.isNotEmpty(),
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "namidaArtwork",
                    ) { showLyrics ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(artworkSize)
                                .clip(RoundedCornerShape(NamidaPanelCorner))
                                .then(if (showLyrics) Modifier.background(palette.surface) else Modifier)
                                .then(
                                    if (preview) Modifier else Modifier.pointerInput(Unit) {
                                        whileStyleInteractive(interactive) {
                                            detectTapGestures(onTap = { lyricsOpen = !lyricsOpen })
                                        }
                                    },
                                ),
                        ) {
                            if (showLyrics && !preview) {
                                NamidaLyrics(
                                    palette = palette,
                                    lines = lyrics.lines,
                                    position = position,
                                    onSeek = { connection?.seekTo(it) },
                                )
                            } else {
                                ArtworkWithGlow(
                                    url = track.artwork,
                                    dim = !playing,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(0.6f))

                    // ── the words: the title big, the artist beneath, the heart at the right ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = palette.onSurface,
                                fontSize = 20.sp,
                                fontFamily = NamidaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = track.artist,
                                color = palette.onSurfaceSoft,
                                fontSize = 15.sp,
                                fontFamily = NamidaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        val heartScale = remember { Animatable(1f) }
                        val scope = rememberCoroutineScope()
                        Icon(
                            painter = painterResource(
                                if (trackInfo?.liked == true) R.drawable.ic_untitled_heart
                                else R.drawable.ic_untitled_heart_unfill,
                            ),
                            contentDescription = null,
                            tint = palette.accent.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer {
                                    scaleX = heartScale.value
                                    scaleY = heartScale.value
                                }
                                .clickable(enabled = !preview) {
                                    scope.launch {
                                        heartScale.animateTo(0.8f, tween(120))
                                        heartScale.animateTo(1f, tween(240))
                                    }
                                    connection?.toggleLike()
                                },
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── the waveform, and the times at its ends ──
                    if (!preview) {
                        NamidaWaveform(
                            palette = palette,
                            position = position,
                            durationMs = durationMs,
                            songKey = track.title + track.artist,
                            onSeek = { connection?.seekTo(it) },
                        )
                        val seconds = rememberPlayedSeconds(position)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            TimeLabel(
                                text = makeTimeString(seconds * 1000L),
                                palette = palette,
                                onSeek = { connection?.seekTo((position.positionMs() - NamidaTapSeekMs).coerceAtLeast(0L)) },
                            )
                            Spacer(Modifier.weight(1f))
                            TimeLabel(
                                text = makeTimeString(durationMs),
                                palette = palette,
                                onSeek = { connection?.seekTo((position.positionMs() + NamidaTapSeekMs).coerceAtMost(durationMs)) },
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // ── the transport: prev, the big play, next ──
                    NamidaTransport(
                        palette = palette,
                        playing = playing,
                        canPrevious = transport?.canSkipPrevious == true,
                        canNext = transport?.canSkipNext == true,
                        ended = transport?.playbackState == androidx.media3.common.Player.STATE_ENDED,
                        onPrevious = { connection?.seekToPrevious() },
                        onToggle = {
                            if (transport?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                connection?.player?.seekTo(0, 0)
                                connection?.player?.playWhenReady = true
                            } else {
                                connection?.togglePlayPause()
                            }
                        },
                        onNext = { connection?.seekToNext() },
                    )

                    Spacer(Modifier.height(12.dp))

                    // ── the bottom row: repeat, lyrics, queue, at the right ──
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val repeatMode = transport?.repeatMode
                        NamidaIconButton(
                            when (repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> R.drawable.repeat
                            },
                            palette,
                            iconSize = 20.dp,
                            active = repeatMode != null && repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                        ) { connection?.player?.toggleRepeatMode() }
                        NamidaIconButton(R.drawable.lyrics, palette, iconSize = 20.dp, active = lyricsOpen) { lyricsOpen = !lyricsOpen }
                        NamidaIconButton(R.drawable.queue_music, palette, iconSize = 20.dp) { queueOpen = true }
                    }
                }

                // the queue over the player, sliding up the way namida's does
                AnimatedContent(
                    targetState = queueOpen,
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = tween(300, easing = CubicBezierEasing(0.150f, 0.96f, 0.28f, 1.04f)),
                            initialOffsetY = { it / 2 },
                        ) + fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "namidaQueue",
                ) { open ->
                    if (open && !preview) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(top = insets.top + 40.dp, bottom = insets.bottom),
                        ) {
                            NamidaQueue(palette = palette, onClose = { queueOpen = false })
                        }
                    }
                }

                // the style's own chevron menu, for everything else (edit, rotate, the song's own menu)
                StyleMenu(
                    open = menuOpen,
                    onOpenChange = setMenuOpen,
                    tones = tones,
                    actions = actions,
                    atBottom = false,
                    end = insets.end + 16.dp,
                    edge = insets.top + 16.dp,
                )
            }
        }
    }
}

/** the artwork with namida's shadow beneath it, its glow dimmed while paused. */
@Composable
private fun ArtworkWithGlow(url: String?, dim: Boolean, modifier: Modifier) {
    val glow = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(dim) {
        glow.animateTo(
            targetValue = if (dim) 0.35f else 1f,
            animationSpec = tween(if (dim) 500 else 800, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
        )
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(NamidaPanelCorner),
                ambientColor = Color(0x28121212),
                spotColor = Color(0x28121212),
            )
            .clip(RoundedCornerShape(NamidaPanelCorner)),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glow.value },
        )
    }
}

/** the transport row: the skips flat, the play a filled gradient circle, namida's shapes. */
@Composable
private fun NamidaTransport(
    palette: NamidaPalette,
    playing: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    ended: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NamidaControlGap),
    ) {
        NamidaIconButton(
            R.drawable.avd_skip_previous,
            palette,
            iconSize = NamidaSkipIcon,
            enabled = canPrevious,
        ) { onPrevious() }
        // the play button: a circle of the song's colour, its gradient to grey at the low corner
        val press = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(NamidaPlayCircle)
                .graphicsLayer {
                    scaleX = press.value
                    scaleY = press.value
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.95f),
                            palette.accent.copy(alpha = 0.78f).compositeOver(Color(0xFF808080)),
                        ),
                    ),
                )
                .clickable {
                    scope.launch {
                        press.animateTo(0.97f, tween(120))
                        press.animateTo(1f, tween(400))
                    }
                    onToggle()
                },
        ) {
            Icon(
                painter = painterResource(if (playing && !ended) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.71f),
                modifier = Modifier.size(NamidaPlayIcon),
            )
        }
        NamidaIconButton(
            R.drawable.avd_skip_next,
            palette,
            iconSize = NamidaSkipIcon,
            enabled = canNext,
        ) { onNext() }
    }
}

/** a flat icon button in namida's inks; [active] takes the song's colour. */
@Composable
private fun NamidaIconButton(
    icon: Int,
    palette: NamidaPalette,
    iconSize: Dp,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (active) palette.accent else palette.onSecondaryContainer,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding((40.dp - iconSize) / 2),
    )
}

/** one of the times under the waveform; a tap seeks five seconds namida's way. */
@Composable
private fun TimeLabel(text: String, palette: NamidaPalette, onSeek: () -> Unit) {
    Text(
        text = text,
        color = palette.onSurfaceSoft,
        fontSize = 13.sp,
        fontFamily = NamidaFontFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onSeek)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

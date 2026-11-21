// The Apple Music player style, ported from Neiro's flamingo design: the whole screen is the
// cover — saturated, darkened and blurred behind everything — the artwork fills the top with a
// slow shrink whenever playback pauses, the title and artist in white over it, and a floating
// control bar at the bottom whose slider grows under the finger. The words are white throughout;
// the song's own colours never reach the type.

package com.mochyy.mochymusic.ui.player.applemusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
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
import com.mochyy.mochymusic.ui.player.styles.withLyricsOffset
import com.mochyy.mochymusic.ui.player.styles.whileStyleInteractive
import com.mochyy.mochymusic.utils.makeTimeString
import kotlin.math.abs

/** how much of the screen's height the artwork block takes, the design's 0.595. */
private const val AppleArtworkHeightFraction = 0.595f

/** the artwork's shrink while paused: the padding that grows around it, and its animation. */
private val ApplePausePadding = 27.dp
private val ApplePlayPadding = 7.dp

/** the type sizes: the title and the artist beneath it. */
private val AppleTitleSize = 19.5.sp
private val AppleArtistSize = 18.5.sp

/** the control bar's buttons and the slider over it. */
private val ApplePlaySize = 58.5.dp
private val AppleSkipSize = 61.dp
private val AppleSliderHeight = 7.dp
private val AppleSliderEngagedHeight = 11.dp
private val AppleBottomIcon = 32.dp

/** The Apple Music look. [preview] draws it as a selector card, still and untouchable. */
@Composable
fun AppleMusicPlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val connection = LocalPlayerConnection.current
    val track = rememberStyleTrack()
    val lyrics = rememberStyleLyrics(readings = !preview)
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val trackInfo = connection?.uiState?.trackInfo?.collectAsState()?.value
    val position = rememberLivePosition(animated = !preview)
    val durationMs = rememberDurationMs()
    val interactive = LocalStyleInteractive.current
    val playing = transport?.isPlaying == true
    var lyricsOpen by remember { mutableStateOf(false) }

    val tones = StyleTones(
        played = Color.White,
        remaining = Color.White.copy(alpha = 0.35f),
        ink = Color.White,
        inkSoft = Color.White.copy(alpha = 0.35f),
        chip = Color.White.copy(alpha = 0.10f),
    )

    StyleFrame(
        preview, modifier, tones,
        background = {
            // the floating light: the cover itself, tiny and upscaled so it reads as a blur, its
            // colour held and darkened so the white type stands on it everywhere
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color(0xFF14100F)) },
            ) {
                if (track.artwork != null) {
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // a tenth the size, drawn back up to the screen: the soft wash
                                scaleX = 10f
                                scaleY = 10f
                                alpha = 0.9f
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(Color(0x33000000))
                                drawRect(Color(0x40000000))
                            },
                    )
                }
            }
        },
    ) { landscape, menuOpen, setMenuOpen ->
        val insets = styleInsets(landscape)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // the artwork's shrink on pause: its padding springs between the two
            val pauseAmount by animateFloatAsState(
                targetValue = if (playing) 0f else 1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 350f),
                label = "applePause",
            )
            val horizontal = (ApplePlayPadding + (ApplePausePadding - ApplePlayPadding) * pauseAmount)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = insets.top + 22.dp,
                        start = insets.start,
                        end = insets.end,
                        bottom = insets.bottom,
                    ),
            ) {
                // ── the artwork, or the lyrics in its place ──
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AppleArtworkHeightFraction)
                        .padding(horizontal = horizontal),
                ) {
                    AnimatedContent(
                        targetState = lyricsOpen && lyrics.lines.isNotEmpty(),
                        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) },
                        label = "appleStage",
                    ) { showLyrics ->
                        if (showLyrics && !preview) {
                            AppleLyrics(
                                lines = lyrics.lines,
                                position = position,
                                onSeek = { connection?.seekTo(it) },
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (preview) Modifier else Modifier.pointerInput(Unit) {
                                            whileStyleInteractive(interactive) {
                                                detectTapGestures(onTap = { connection?.togglePlayPause() })
                                            }
                                        },
                                    ),
                            ) {
                                AsyncImage(
                                    model = track.artwork,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── the words: the title and the artist, white over the wash ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = AppleTitleSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                        Text(
                            text = track.artist,
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = AppleArtistSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    AppleIconButton(
                        if (trackInfo?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                        size = 28.dp,
                    ) { connection?.toggleLike() }
                    Spacer(Modifier.width(12.dp))
                    AppleIconButton(R.drawable.more_horiz, size = 28.dp) { actions.onOtherMenu() }
                }

                Spacer(Modifier.weight(1f))

                // ── the control bar: the slider, the transport, the bottom row ──
                AppleControlBar(
                    position = position,
                    durationMs = durationMs,
                    playing = playing,
                    canPrevious = transport?.canSkipPrevious == true,
                    canNext = transport?.canSkipNext == true,
                    ended = transport?.playbackState == androidx.media3.common.Player.STATE_ENDED,
                    lyricsOpen = lyricsOpen,
                    onSeek = { connection?.seekTo(it) },
                    onPrevious = { connection?.seekToPrevious() },
                    onNext = { connection?.seekToNext() },
                    onToggle = {
                        if (transport?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            connection?.player?.seekTo(0, 0)
                            connection?.player?.playWhenReady = true
                        } else {
                            connection?.togglePlayPause()
                        }
                    },
                    onLyrics = { lyricsOpen = !lyricsOpen },
                    onQueue = { actions.onQueue?.invoke() },
                )
            }

            StyleMenu(
                open = menuOpen,
                onOpenChange = setMenuOpen,
                tones = tones,
                actions = actions,
                atBottom = false,
                end = insets.end + 16.dp,
                edge = insets.top + 10.dp,
            )
        }
    }
}

/** the control bar: the times and the slider above the transport, the toggles beneath. */
@Composable
private fun AppleControlBar(
    position: com.mochyy.mochymusic.ui.player.styles.LivePosition,
    durationMs: Long,
    playing: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    ended: Boolean,
    lyricsOpen: Boolean,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
) {
    var engaging by remember { mutableStateOf(false) }
    val sliderHeight by animateFloatAsState(
        targetValue = if (engaging) AppleSliderEngagedHeight.value else AppleSliderHeight.value,
        animationSpec = tween(180),
        label = "appleSlider",
    )
    val seconds = rememberPlayedSeconds(position)
    Column(modifier = Modifier.fillMaxWidth()) {
        // the slider: rounded bars on a dark backing, the times at its ends
        var fraction by remember { mutableFloatStateOf(Float.NaN) }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(24.dp)
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { engaging = true },
                        onDragEnd = { engaging = false; onSeek((fraction * durationMs).toLong()); fraction = Float.NaN },
                        onDragCancel = { engaging = false; fraction = Float.NaN },
                        onHorizontalDrag = { change, _ ->
                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
                .pointerInput(durationMs) {
                    detectTapGestures { change ->
                        onSeek(((change.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                },
        ) {
            val played = if (fraction.isNaN()) (position.positionMs().toFloat() / durationMs).coerceIn(0f, 1f) else fraction
            val h = sliderHeight.dp.toPx()
            val y = (size.height - h) / 2f
            val radius = CornerRadius(h / 2f)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(0f, y),
                size = Size(size.width, h),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(0f, y),
                size = Size(size.width, h),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, y),
                size = Size(size.width * played, h),
                cornerRadius = radius,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = makeTimeString(seconds * 1000L),
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "−" + makeTimeString(durationMs - seconds * 1000L),
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(8.dp))

        // the transport: the skips wide, the play between them
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppleIconButton(
                R.drawable.apple_skip_previous,
                size = 42.dp,
                enabled = canPrevious,
            ) { onPrevious() }
            val scale = remember { Animatable(1f) }
            LaunchedEffect(playing) { scale.snapTo(0.8f); scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ApplePlaySize)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clickable(onClick = onToggle),
            ) {
                Icon(
                    painter = painterResource(if (playing && !ended) R.drawable.pause_applemusic else R.drawable.play_applemusic),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }
            AppleIconButton(
                R.drawable.apple_skip_next,
                size = 42.dp,
                enabled = canNext,
            ) { onNext() }
        }

        Spacer(Modifier.height(10.dp))

        // the bottom row: lyrics, the queue, and the style's own menu
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppleIconButton(
                R.drawable.apple_lyrics,
                size = AppleBottomIcon,
                active = lyricsOpen,
            ) { onLyrics() }
            AppleIconButton(R.drawable.apple_queue, size = AppleBottomIcon) { onQueue() }
        }
    }
}

/** the lyrics page: the lines big and white, the active one full, the rest dimmed. */
@Composable
private fun AppleLyrics(
    lines: List<com.mochyy.mochymusic.lyrics.LyricsEntry>,
    position: com.mochyy.mochymusic.ui.player.styles.LivePosition,
    onSeek: (Long) -> Unit,
) {
    var active by remember { mutableStateOf(-1) }
    val clock = position.withLyricsOffset()
    LaunchedEffect(lines, clock) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            var now = -1
            for (i in lines.indices) {
                if (lines[i].time <= clock.positionMs()) now = i else break
            }
            if (now != active) active = now
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height.coerceAtLeast(1)
        runCatching { listState.animateScrollToItem(active, -(viewport * 0.35f).toInt()) }
    }
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(lines.size) { index ->
            val isActive = index == active
            val distance = abs(index - active)
            Text(
                text = lines[index].text,
                color = when {
                    isActive -> Color.White
                    distance <= 2 -> Color.White.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.25f)
                },
                fontSize = 22.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSeek(lines[index].time) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** a white icon button; [active] takes the full white, the rest sit at the design's 0.4. */
@Composable
private fun AppleIconButton(icon: Int, size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (active) Color.White else Color.White.copy(alpha = 0.40f),
        modifier = Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clickable(enabled = enabled, onClick = onClick),
    )
}

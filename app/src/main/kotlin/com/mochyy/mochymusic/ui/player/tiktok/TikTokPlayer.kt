// The TikTok player style, ported from Neiro: the queue as a full-screen vertical feed — one
// swipe a song — over a mesh of the artwork's colours, the caption along the bottom left, a rail
// of actions down the right, and a thin progress bar under everything.

package com.mochyy.mochymusic.ui.player.tiktok

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.models.QueueItemData
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.StyleActions
import com.mochyy.mochymusic.ui.player.styles.StyleFrame
import com.mochyy.mochymusic.ui.player.styles.StyleMenu
import com.mochyy.mochymusic.ui.player.styles.StyleTones
import com.mochyy.mochymusic.ui.player.styles.rememberDurationMs
import com.mochyy.mochymusic.ui.player.styles.rememberLivePosition
import com.mochyy.mochymusic.ui.player.styles.rememberPlayedSeconds
import com.mochyy.mochymusic.ui.player.styles.rememberStyleTrack
import com.mochyy.mochymusic.ui.player.styles.styleInsets
import com.mochyy.mochymusic.utils.makeTimeString
import kotlin.math.abs
import kotlin.math.min

/** the feed's backdrop before any colour arrives */
private val TikTokEmptyBackdrop = Color(0xFF0B0B0F)

/** the inactive greys and the rail's red, the design's own colours. */
private val TikTokInactiveGray = Color(0xFFA9A9B2)
private val TikTokRed = Color(0xFFFE2C55)

/** the caption's type sizes. */
private val TikTokTitleSize = 21.sp
private val TikTokSubtitleSize = 14.sp

/** The TikTok look. [preview] draws it as a selector card, still on the playing song. */
@Composable
fun TikTokPlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    val connection = LocalPlayerConnection.current
    val track = rememberStyleTrack()
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val trackInfo = connection?.uiState?.trackInfo?.collectAsState()?.value
    val queue = connection?.queueItems?.collectAsState()?.value.orEmpty()
    val currentIndex = connection?.currentWindowIndex?.collectAsState()?.value ?: -1
    val position = rememberLivePosition(animated = !preview)
    val durationMs = rememberDurationMs()
    val interactive = LocalStyleInteractive.current
    val seed = LocalArtworkColor.current

    // the pager over the queue: settled on the song that plays, a swipe to the neighbours
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentIndex.coerceAtLeast(0),
        pageCount = { if (preview) 1 else queue.size.coerceAtLeast(1) },
    )
    // a settled swipe changes the song; the player's own skips turn the pages under it
    if (!preview && connection != null) {
        LaunchedEffect(pagerState, queue) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                val index = queue.getOrNull(page) ?: return@collect
                if (page != currentIndex && index.mediaId != trackInfo?.mediaId) {
                    connection.playQueueItem(index.uid)
                }
            }
        }
        LaunchedEffect(currentIndex, queue.size) {
            if (currentIndex >= 0 && currentIndex != pagerState.settledPage && currentIndex < pagerState.pageCount) {
                runCatching { pagerState.animateScrollToPage(currentIndex) }
            }
        }
    }

    val tones = StyleTones(
        played = Color.White,
        remaining = Color.White.copy(alpha = 0.28f),
        ink = Color.White,
        inkSoft = TikTokInactiveGray,
        chip = Color.White.copy(alpha = 0.14f),
    )

    StyleFrame(
        preview, modifier, tones,
        background = { Box(Modifier.fillMaxSize().background(TikTokEmptyBackdrop)) },
    ) { landscape, menuOpen, setMenuOpen ->
        val insets = styleInsets(landscape)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = queue.getOrNull(page)
                val isCurrent = !preview && page == pagerState.settledPage && item?.mediaId == trackInfo?.mediaId
                TikTokSongPage(
                    artwork = item?.artworkUri?.toString() ?: track.artwork,
                    title = item?.title ?: track.title,
                    artist = item?.artist ?: track.artist,
                    seed = seed,
                    isCurrent = isCurrent,
                    playing = transport?.isPlaying == true,
                    liked = if (isCurrent) trackInfo?.liked == true else false,
                    position = position,
                    durationMs = durationMs,
                    preview = preview,
                    interactive = interactive,
                    insets = insets,
                    maxHeight = maxHeight,
                    onToggle = { connection?.togglePlayPause() },
                    onLike = { connection?.toggleLike() },
                    onLyrics = { actions.onLyrics?.invoke() },
                    onQueue = { actions.onQueue?.invoke() },
                    onMore = { actions.onOtherMenu() },
                )
            }

            StyleMenu(
                open = menuOpen,
                onOpenChange = setMenuOpen,
                tones = tones,
                actions = actions,
                atBottom = false,
                end = insets.end + 16.dp,
                edge = insets.top + 12.dp,
            )
        }
    }
}

/** one page of the feed: the mesh backdrop, the artwork square, the caption, the rail. */
@Composable
private fun TikTokSongPage(
    artwork: String?,
    title: String,
    artist: String,
    seed: Color,
    isCurrent: Boolean,
    playing: Boolean,
    liked: Boolean,
    position: com.mochyy.mochymusic.ui.player.styles.LivePosition,
    durationMs: Long,
    preview: Boolean,
    interactive: () -> Boolean,
    insets: com.mochyy.mochymusic.ui.player.styles.StyleInsets,
    maxHeight: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onLike: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onMore: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // the mesh of the artwork's colours, drifting
        TikTokMeshBackdrop(seed, Modifier.fillMaxSize())
        // the darkening scrim the feed sits on
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.32f),
                            0.45f to Color.Black.copy(alpha = 0.06f),
                            1f to Color.Black.copy(alpha = 0.52f),
                        ),
                    )
                },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.top + 44.dp, start = 16.dp, end = 16.dp, bottom = insets.bottom),
        ) {
            // the artwork square, centred in the space above the caption; a tap plays or pauses
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val artSize = min(maxWidth.value, maxHeight.value * 0.55f).dp
                var heartBurst by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (preview) Modifier else Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onToggle() },
                                    onDoubleTap = {
                                        onLike()
                                        heartBurst = true
                                    },
                                )
                            },
                        ),
                ) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // the pause veil and its big glyph, the feed's quiet state
                    val pauseAlpha by animateFloatAsState(
                        targetValue = if (playing || preview) 0f else 1f,
                        animationSpec = tween(220),
                        label = "tiktokPause",
                    )
                    if (pauseAlpha > 0.01f) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = pauseAlpha }
                                .background(Color.Black.copy(alpha = 0.22f)),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    }
                    // the heart burst of a double-tap like
                    val burst = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(heartBurst) {
                        if (heartBurst) {
                            burst.snapTo(0f)
                            burst.animateTo(1f, tween(650))
                            heartBurst = false
                        }
                    }
                    if (burst.value > 0.001f && burst.value < 0.999f) {
                        Icon(
                            painter = painterResource(R.drawable.ic_untitled_heart),
                            contentDescription = null,
                            tint = TikTokRed,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(84.dp)
                                .graphicsLayer {
                                    val p = burst.value
                                    scaleX = 0.5f + p * 0.9f
                                    scaleY = 0.5f + p * 0.9f
                                    alpha = (1f - p).coerceIn(0f, 1f)
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // the caption: the title, the artist beneath, away from the rail
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 64.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = TikTokTitleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = TikTokInactiveGray,
                    fontSize = TikTokSubtitleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))

            // the progress row: the times and the thin bar between them, on the page that plays
            if (!preview && isCurrent) {
                TikTokProgressRow(position = position, durationMs = durationMs, onSeek = { })
            }
        }

        // the rail: like, lyrics, queue, more — down the right, over the scrim
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = insets.bottom + 84.dp),
        ) {
            val likeScale = remember { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(liked) {
                if (isCurrent) {
                    likeScale.snapTo(0.6f)
                    likeScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
                }
            }
            TikTokRailButton(
                icon = if (liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                tint = if (liked) TikTokRed else Color.White,
                scale = likeScale.value,
            ) { onLike() }
            TikTokRailButton(R.drawable.lyrics, Color.White) { onLyrics() }
            TikTokRailButton(R.drawable.queue_music, Color.White) { onQueue() }
            TikTokRailButton(R.drawable.more_horiz, Color.White) { onMore() }
        }
    }
}

/** the progress row: the time past, the thin white bar, the length. */
@Composable
private fun TikTokProgressRow(
    position: com.mochyy.mochymusic.ui.player.styles.LivePosition,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val seconds = rememberPlayedSeconds(position)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Text(
            text = makeTimeString(seconds * 1000L),
            color = TikTokInactiveGray,
            fontSize = 12.sp,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(3.dp),
        ) {
            val played = (position.positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                size = size.copy(height = size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.95f),
                size = size.copy(width = size.width * played),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
        Text(
            text = makeTimeString(durationMs),
            color = TikTokInactiveGray,
            fontSize = 12.sp,
        )
    }
}

/** one rail button: a 30dp glyph in a 48dp circle, its own shadow behind it. */
@Composable
private fun TikTokRailButton(icon: Int, tint: Color, scale: Float = 1f, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * The mesh backdrop: four soft blobs of the artwork's colours drifting over the dark base, the
 * design's wash behind the feed. The colours are the artwork seed's hue turned four ways.
 */
@Composable
internal fun TikTokMeshBackdrop(seed: Color, modifier: Modifier) {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), hsl)
    fun tone(hueShift: Float, lightness: Float, alpha: Float) = Color(
        androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf((hsl[0] + hueShift).mod(360f), 0.55f, lightness)),
    ).copy(alpha = alpha)

    var phase by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            phase = (System.nanoTime() / 1_000_000_000f * 0.125f).mod(1f)
        }
    }
    val base = Color(0xFF0B0B0F)
    val blobs = listOf(
        tone(0f, 0.34f, 0.65f),
        tone(40f, 0.38f, 0.55f),
        tone(-30f, 0.30f, 0.60f),
        tone(70f, 0.42f, 0.45f),
    )
    val anchors = listOf(
        Offset(0.20f, 0.25f),
        Offset(0.80f, 0.20f),
        Offset(0.75f, 0.80f),
        Offset(0.25f, 0.75f),
    )
    Canvas(modifier) {
        drawRect(base)
        val t = phase * 2f * Math.PI.toFloat()
        blobs.forEachIndexed { i, color ->
            val anchor = anchors[i]
            val drift = 0.06f
            val cx = size.width * (anchor.x + drift * kotlin.math.sin(t + i * 1.4f))
            val cy = size.height * (anchor.y + drift * kotlin.math.cos(t + i * 1.1f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.maxDimension * 0.65f,
                ),
                radius = size.maxDimension * 0.65f,
                center = Offset(cx, cy),
            )
        }
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                1f to Color.Black.copy(alpha = 0.45f),
            ),
        )
    }
}


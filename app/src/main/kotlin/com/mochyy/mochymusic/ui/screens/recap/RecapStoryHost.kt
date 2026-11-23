// RecapStoryHost.kt
//
// Android's side of the monthly recap. The story itself (the script, the pages, the card's movement)
// is shared in shared's RecapStory.kt; this file holds what only Android can do: the full-screen dialog
// window that covers the nav bar and mini player, the pause of the main player while the story plays,
// the picture loads and palettes (Coil and androidx Palette), the AGSL mesh, the device-tilt ID card,
// and the snippet player. RecapStoryData is the app's account for the story; it keeps java.time for the
// joined date that ProfileScreen passes in.

package com.mochyy.mochymusic.ui.screens.recap

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ui.component.rememberDeviceTilt
import com.mochyy.mochymusic.ui.screens.setup.onboarding.IdCardAspect
import com.mochyy.mochymusic.ui.screens.setup.onboarding.ProfileIdCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---------------------------------------------------------------------------------------------
// What the story is about, and where it grows from

@Immutable
internal class RecapStoryData(
    override val stats: ListeningStats,
    override val monthProgress: Int,
    val username: String,
    val photo: Any?,
    val cardNumber: String,
    val joinedText: String,
    val joinedDate: LocalDate,
    override val allTimeMs: Long,
) : RecapStorySource {
    override val name: String get() = username.trim().ifEmpty { "you" }
    /** "October" - or "2026" when a forced recap had to fall back to the whole year. */
    override val monthName: String get() = stats.range.label
    override val artists: List<RecapArtist> = stats.topArtists.take(10)
    override val songs: List<RecapSong> = stats.topSongs
    override val heroArtist: RecapArtist? = artists.firstOrNull()
    override val heroSong: RecapSong? = songs.firstOrNull()

    /** Monday = 0. */
    override val favouriteDay: Int = stats.weekdayMs.indices.maxByOrNull { stats.weekdayMs[it] } ?: 0
    override val favouriteDayMs: Long = stats.weekdayMs.getOrElse(favouriteDay) { 0L }
    override val runnerUpMs: Long = stats.weekdayMs.filterIndexed { i, _ -> i != favouriteDay }.maxOrNull() ?: 0L

    // the journey page shows the joined date with DateLong, which is a java.time formatter
    override val joinedDateText: String = DateLong.format(joinedDate)
}

// ---------------------------------------------------------------------------------------------
// The window

/**
 * Shows the recap over everything. [onCovered]: the story now hides the page completely, so the
 * page may reset itself underneath. [onReturned]: the story is drawing exactly the collapsed header,
 * so the page should show its own card again. [onClosed]: the window is going away.
 */
@Composable
internal fun RecapStoryHost(
    data: RecapStoryData,
    anchors: RecapAnchors,
    payload: String,
    caption: String,
    onCovered: () -> Unit,
    onReturned: () -> Unit,
    onClosed: () -> Unit,
) {
    val exitRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    Dialog(
        onDismissRequest = { exitRequests.trySend(Unit) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.run {
                setWindowAnimations(0)
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                // without this flag the system paints both bars solid black behind a dialog, whatever
                // their colour is set to; with it (and the translucent flags off) they show the story
                @Suppress("DEPRECATION")
                clearFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                )
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                @Suppress("DEPRECATION")
                statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= 28) {
                    attributes = attributes.also {
                        it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
                // a dialog's window is laid out between the status and navigation bars by default,
                // which left both bars black over the story; this takes it to the screen's edges
                if (Build.VERSION.SDK_INT >= 30) {
                    attributes = attributes.also { it.fitInsetsTypes = 0 }
                } else {
                    addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                }
                WindowCompat.setDecorFitsSystemWindows(this, false)
                WindowCompat.getInsetsController(this, decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }

        // the real queue stops while the recap plays its own snippets, and picks up after
        val playerConnection = LocalPlayerConnection.current
        DisposableEffect(playerConnection) {
            val player = playerConnection?.player
            val wasPlaying = player?.isPlaying == true
            player?.pause()
            onDispose { if (wasPlaying) player?.play() }
        }

        val context = LocalContext.current
        val platform = remember(data, payload, caption) { AndroidRecapStoryPlatform(context, data, payload, caption) }
        RecapStory(data, data.photo, anchors, platform, exitRequests, onCovered, onReturned, onClosed)
    }
}

// what the shared story asks of Android: Coil loads, androidx Palette, the AGSL mesh, the ID card with
// its device tilt, and the ExoPlayer snippets
private class AndroidRecapStoryPlatform(
    private val context: Context,
    private val data: RecapStoryData,
    private val payload: String,
    private val caption: String,
) : RecapStoryPlatform {
    override fun prefetch(url: String) {
        context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
    }

    override suspend fun paletteOf(url: String?): RecapPalette = RecapPalettes.extract(context, url)

    override fun snippets(scope: CoroutineScope): RecapSnippets = RecapSnippetPlayer(context, scope)

    @Composable
    override fun Mesh(palette: () -> RecapPalette, time: RecapTime, alpha: () -> Float, modifier: Modifier) {
        MeshGradient(palette = palette, time = time, alpha = alpha, modifier = modifier)
    }

    @Composable
    override fun CardFace(state: StoryState, time: RecapTime, cardWidth: Float) {
        StoryCard(state, data, payload, caption, time, cardWidth)
    }
}

@Composable
private fun StoryCard(s: StoryState, data: RecapStoryData, payload: String, caption: String, time: RecapTime, cardWidth: Float) {
    val density = LocalDensity.current
    val widthDp = with(density) { cardWidth.toDp() }
    val deviceTilt = rememberDeviceTilt()
    Box(
        modifier = Modifier
            .requiredSize(widthDp, widthDp / IdCardAspect)
            .layoutAtOrigin()
            .graphicsLayer {
                translationX = s.cardX.value
                translationY = s.cardY.value
                rotationZ = s.cardRot.value
                rotationX = deviceTilt.rotationX
                rotationY = deviceTilt.rotationY
                alpha = s.cardAlpha.value
                cameraDistance = 16f * this.density
            },
    ) {
        if (s.cardBack) {
            IdCardBack(
                username = data.username,
                payload = payload,
                caption = caption,
                scratch = null,
                time = time,
                scan = { s.scan.value },
                verdict = { ScanVerdict.Valid },
            )
        } else {
            ProfileIdCard(
                username = data.username,
                photo = data.photo,
                cardNumber = data.cardNumber,
                joinedText = data.joinedText,
                showIdCardOf = true,
            )
        }
    }
}


internal val DateLong: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

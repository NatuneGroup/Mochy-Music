// namida's lyrics: centered lines in the artwork's place, the active line in a rounded pill of
// the song's colour, the lines around it fading by distance, tap a line to seek to it.

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.ui.player.styles.LivePosition
import com.mochyy.mochymusic.ui.player.styles.withLyricsOffset
import androidx.compose.runtime.withFrameMillis

/** the base size of a lyrics line and the padding the list keeps around its ends */
private val NamidaLyricsFontSize = 15.sp
private val NamidaLyricsVerticalPadding = 144.dp
private val NamidaLyricsHorizontalPadding = 8.dp

/** how far off the active line may sit before the list follows it again */
private const val NamidaFollowLow = 0.10f
private const val NamidaFollowHigh = 0.70f

/** the active line's index, or -1 before the words start. */
internal fun namidaActiveLine(lines: List<LyricsEntry>, positionMs: Long): Int {
    var active = -1
    for (i in lines.indices) {
        if (lines[i].time <= positionMs) active = i else break
    }
    return active
}

/**
 * The lyrics pane shown where the artwork was. The active line is followed on the frame clock —
 * recomposition happens only when it changes — and the list parks it at two fifths of the pane
 * once it leaves the band around the middle.
 */
@Composable
internal fun NamidaLyrics(
    palette: NamidaPalette,
    lines: List<LyricsEntry>,
    position: LivePosition,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val clock = position.withLyricsOffset()
    var active by remember { mutableIntStateOf(-1) }
    // the line changes a handful of times a second at most; the frame read stays in the effect
    LaunchedEffect(lines, clock) {
        while (true) {
            withFrameMillis { }
            val now = namidaActiveLine(lines, clock.positionMs())
            if (now != active) active = now
        }
    }
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
        // follow only once the line leaves the comfort band, the way namida's follower does
        if (item != null) {
            val offsetFraction = item.offset.toFloat() / viewport.height.coerceAtLeast(1)
            if (offsetFraction in NamidaFollowLow..NamidaFollowHigh) return@LaunchedEffect
        }
        // park the active line at two fifths of the pane
        runCatching {
            listState.animateScrollToItem(
                index = active,
                scrollOffset = -(viewport.height * 0.4f).toInt(),
            )
        }
    }
    LazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = NamidaLyricsVerticalPadding),
    ) {
        items(lines.size) { index ->
            val isActive = index == active
            val distance = if (active < 0) 3 else absOf(index - active)
            val alpha = when {
                isActive -> 1f
                distance == 1 -> 0.5f
                distance == 2 -> 0.4f
                else -> 0.25f
            }
            val line = lines[index]
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = NamidaLyricsHorizontalPadding, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .fillMaxWidth(0.86f)
                    .then(
                        if (isActive) Modifier
                            .background(palette.accent.copy(alpha = 0.50f).compositeOver(palette.surface))
                            .height(if (line.text.isBlank()) 26.dp else 44.dp)
                        else Modifier,
                    )
                    .clickable { onSeek(line.time) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = line.text.ifBlank { "• • •" },
                    color = palette.onSurface,
                    fontSize = NamidaLyricsFontSize,
                    fontFamily = NamidaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.alpha(alpha),
                )
            }
        }
    }
}

private fun absOf(value: Int): Int = if (value < 0) -value else value

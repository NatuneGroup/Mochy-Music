// DevicesKit.kt
//
// The device sheets' shared look: the colours, the rounded card, the headings, the buttons and
// the kind names. The Hilt entry point and the icon rows stay in app's DevicesLink.kt and
// DevicesKitIcons.kt, since they need Android's DI and Material icon vectors.

package com.mochyy.mochymusic.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.devices_kind_car
import com.mochyy.mochymusic.shared.resources.devices_kind_computer
import com.mochyy.mochymusic.shared.resources.devices_kind_phone
import com.mochyy.mochymusic.shared.resources.devices_kind_tablet
import com.mochyy.mochymusic.shared.resources.devices_kind_tv
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import org.jetbrains.compose.resources.stringResource

val Muted = Color.White.copy(alpha = 0.6f)
val Faint = Color.White.copy(alpha = 0.1f)
val LiveGreen = Color(0xFF5BD68A)
val CardShape = RoundedCornerShape(22.dp)

@Composable
fun DeviceKind.label(): String = stringResource(
    when (this) {
        DeviceKind.Phone -> Res.string.devices_kind_phone
        DeviceKind.Tablet -> Res.string.devices_kind_tablet
        DeviceKind.Tv -> Res.string.devices_kind_tv
        DeviceKind.Car -> Res.string.devices_kind_car
        DeviceKind.Desktop -> Res.string.devices_kind_computer
    },
)

// the big two-line heading the device sheets open with
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.03).em,
        color = Color.White,
        modifier = modifier.padding(start = 2.dp, top = 4.dp),
    )
}

// a small grey label over a group, like "my devices"
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = Muted,
        modifier = modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

// a rounded pill with a word in it: white when it's the one that's on, grey otherwise
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, bright: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = when {
            bright -> Color.Black
            enabled -> Color.White
            else -> Color.White.copy(alpha = 0.35f)
        },
        maxLines = 1,
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.95f)
            .clip(CircleShape)
            .background(if (bright) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = RemoteFocus, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// the wide button at the bottom of a sheet; a grey one sits beside it for the second choice
@Composable
fun SheetButton(text: String, modifier: Modifier = Modifier, bright: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .height(50.dp)
            .clip(CircleShape)
            .background(if (bright) Color.White else Color.White.copy(alpha = 0.26f))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = if (bright) Color.Black else Color.White)
    }
}

// a rounded card on the sheet's row grey, holding rows with no lines between them
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}

// a row of plain text that does one thing when tapped, red when it can't be undone
@Composable
fun ActionRow(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            danger -> Color(0xFFFF8A80)
            else -> Color.White
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

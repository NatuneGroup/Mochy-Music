// DevicesKitIcons.kt
//
// The device rows that show a Material icon. Compose's icon vectors are an Android artifact here
// (shared has only the core set), so the kind icons and the switch row stay in app.

package com.mochyy.mochymusic.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.theme.InterFontFamily

internal val DeviceKind.icon: ImageVector
    get() = when (this) {
        DeviceKind.Phone -> Icons.Rounded.Smartphone
        DeviceKind.Tablet -> Icons.Rounded.TabletAndroid
        DeviceKind.Tv -> Icons.Rounded.Tv
        DeviceKind.Car -> Icons.Rounded.DirectionsCar
        DeviceKind.Desktop -> Icons.Rounded.Computer
    }

// one switch row in a card: a round icon, what it does, and a line on why
@Composable
internal fun ToggleRow(
    icon: ImageVector?,
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Faint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            )
            if (detail != null) {
                Text(detail, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = Muted.copy(alpha = if (enabled) 0.6f else 0.3f))
            }
        }
        Spacer(Modifier.width(10.dp))
        AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

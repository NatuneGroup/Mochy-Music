// LoadingScreen.kt

package com.mochyy.mochymusic.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.progress_percent
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoadingScreen(
    isVisible: Boolean,
    value: Int,
) {
    if (isVisible) {
        Dialog (
            onDismissRequest = {}
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = stringResource(Res.string.progress_percent, value.toString()),
                    color = Color.White,
                    fontSize = 26.sp,
                )

            }
        }
    }
}

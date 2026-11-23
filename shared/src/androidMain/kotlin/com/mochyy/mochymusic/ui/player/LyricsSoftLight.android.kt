package com.mochyy.mochymusic.ui.player

import android.os.Build

// same check as before the move: soft light needs android 10 (API 29) or newer
actual val SoftLightSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

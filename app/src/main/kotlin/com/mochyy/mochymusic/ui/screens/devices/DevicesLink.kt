// DevicesLink.kt
//
// The phone's link to its devices for the device sheets, read through Hilt. The sheets are drawn in
// shared's DevicesKit.kt; this is the Android side that hands them the link.

package com.mochyy.mochymusic.ui.screens.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mochyy.mochymusic.link.phone.PhoneLinkHost
import com.mochyy.mochymusic.link.phone.PhonePairing
import com.mochyy.mochymusic.link.remote.RemoteControl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevicesEntryPoint {
    fun phoneLinkHost(): PhoneLinkHost

    fun remoteControl(): RemoteControl

    fun phonePairing(): PhonePairing
}

// the phone's link to its devices, its remote and its own pairing code, for sheets that live outside hilt's view models
@Immutable
class DevicesLink(val host: PhoneLinkHost, val remote: RemoteControl, val pairing: PhonePairing)

@Composable
fun rememberDevicesLink(): DevicesLink {
    val context = LocalContext.current.applicationContext
    return remember {
        val entry = EntryPointAccessors.fromApplication(context, DevicesEntryPoint::class.java)
        DevicesLink(entry.phoneLinkHost(), entry.remoteControl(), entry.phonePairing())
    }
}

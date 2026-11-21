// Whether the connection in force costs by the byte, and what that means for the streams: the
// Internet settings page lets a listener set one quality ceiling on wifi and a lower one on
// mobile data, and this is where the two meet — the connection in force picks the ceiling.

package com.mochyy.mochymusic.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.constants.AudioQualityCellularKey
import com.mochyy.mochymusic.constants.AudioQualityKey
import com.mochyy.mochymusic.constants.AudioQualityWifiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetMetered {
    /** whether the active network is metered; null before the first answer or offline */
    private val meteredState = MutableStateFlow<Boolean?>(null)
    val metered: StateFlow<Boolean?> = meteredState.asStateFlow()

    private var started = false

    /** starts watching the connection; safe to call again, the first call is the one that counts */
    fun start(context: Context) {
        if (started) return
        started = true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        fun refresh() {
            meteredState.value = runCatching {
                if (manager.activeNetwork == null) null else manager.isActiveNetworkMetered
            }.getOrNull()
        }
        refresh()
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
            })
        }
    }

    /**
     * The quality ceiling the settings ask for on the connection in force. The single
     * audioQuality choice feeds whichever split setting has not been set yet, so an update keeps
     * what was picked; offline reads as wifi, the way a home network reads.
     */
    fun effectiveAudioQuality(prefs: Preferences): AudioQuality {
        val legacy = prefs[AudioQualityKey]
        val stored = when (meteredState.value) {
            true -> prefs[AudioQualityCellularKey] ?: legacy
            else -> prefs[AudioQualityWifiKey] ?: legacy
        }
        return AudioQuality.entries.find { it.name == stored } ?: AudioQuality.AUTO
    }
}

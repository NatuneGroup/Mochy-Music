// StageNative.kt

package com.mochyy.mochymusic.eq.stage

import java.nio.ByteBuffer

/** the jni half of 3d sims (app/src/main/cpp/stage); handles are only ever passed back here, on the playback thread */
internal object StageNative {

    /** loads the library the first time the room is wanted, so nobody else pays for it */
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("mochystage") }.isSuccess }

    // the parameter array's layout is shared with StageSettings (StageLayout.kt), the same as engine.h;
    // these aliases keep the names the UI already uses
    const val Enabled = StageLayout.Enabled
    const val Mode = StageLayout.Mode
    const val RoomWidth = StageLayout.RoomWidth
    const val RoomDepth = StageLayout.RoomDepth
    const val RoomHeight = StageLayout.RoomHeight
    const val Absorption = StageLayout.Absorption
    const val Damping = StageLayout.Damping
    const val RoomAmount = StageLayout.RoomAmount
    const val ListenerX = StageLayout.ListenerX
    const val ListenerY = StageLayout.ListenerY
    const val ListenerZ = StageLayout.ListenerZ
    const val ListenerYaw = StageLayout.ListenerYaw
    const val Master = StageLayout.Master
    const val Objects = StageLayout.Objects
    const val ObjectStride = StageLayout.ObjectStride
    const val Copies = StageLayout.Copies
    const val Kinds = StageLayout.Kinds
    const val ObjectCount = StageLayout.ObjectCount
    const val ParamCount = StageLayout.ParamCount

    const val ModeHeadphones = StageLayout.ModeHeadphones
    const val ModeSpeakers = StageLayout.ModeSpeakers

    external fun loadHrtf(data: ByteArray): Boolean
    external fun create(sampleRate: Int, channels: Int): Long
    external fun setParams(handle: Long, params: FloatArray)
    external fun process(handle: Long, input: ByteBuffer, inputOffset: Int, output: ByteBuffer, frames: Int): Boolean
    external fun levels(handle: Long, out: FloatArray)
    external fun clear(handle: Long)
    external fun destroy(handle: Long)
}

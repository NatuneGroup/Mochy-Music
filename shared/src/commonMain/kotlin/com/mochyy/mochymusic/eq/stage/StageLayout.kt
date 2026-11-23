// StageLayout.kt

package com.mochyy.mochymusic.eq.stage

/**
 * The parameter array the 3D Sims room reads, laid out the same as the native engine's engine.h.
 * It lives in shared code because [StageSettings.toParams] builds the array; the JNI half
 * (`StageNative`, Android only) passes the same array to the library.
 */
object StageLayout {
    const val Enabled = 0
    const val Mode = 1
    const val RoomWidth = 2
    const val RoomDepth = 3
    const val RoomHeight = 4
    const val Absorption = 5
    const val Damping = 6
    const val RoomAmount = 7
    const val ListenerX = 8
    const val ListenerY = 9
    const val ListenerZ = 10
    const val ListenerYaw = 11
    const val Master = 12
    const val Objects = 13
    const val ObjectStride = 5
    // each kind of sound (StageSource, in order) has this many slots; an object's slot is kind * Copies + copy
    const val Copies = 4
    const val Kinds = 6
    const val ObjectCount = Kinds * Copies
    const val ParamCount = Objects + ObjectCount * ObjectStride

    const val ModeHeadphones = 0f
    const val ModeSpeakers = 1f
}

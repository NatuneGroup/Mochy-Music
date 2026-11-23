package com.mochyy.mochymusic.eq.audio

enum class SpatialMode { Crossfeed, Speakers }

data class SpatialParams(
    val enabled: Boolean = false,
    val mode: SpatialMode = SpatialMode.Crossfeed,
    /** crossfeed: where the other side's sound starts being shadowed by the head */
    val cutoffHz: Float = 700f,
    /** crossfeed: how much quieter the other side arrives */
    val feedDb: Float = 4.5f,
    /** virtual speakers: how far each one sits from straight ahead, degrees */
    val speakerAngle: Float = 30f,
    val amount: Float = 1f,
    /** 5.1 / 7.1 (atmos) streams rendered to two ears instead of folded down by the device */
    val surround: Boolean = false,
    /** both ears hear the whole mix (one earbud, hearing in one ear) */
    val mono: Boolean = false,
)

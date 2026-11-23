package com.mochyy.mochymusic.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// which mime types each codec name stands for, and what an unknown one does
class DeviceCodecsTest {

    private class FakeDecoders(private val takes: Set<String>) : DecoderPlatform {
        val asked = mutableListOf<String>()
        override fun canPlay(mime: String, sampleRateHz: Int): Boolean {
            asked += "$mime@$sampleRateHz"
            return mime in takes
        }
    }

    @Test
    fun aCodecNothingAsksAboutIsPlayable() {
        val decoders = FakeDecoders(emptySet())
        val codecs = DeviceCodecs(decoders)
        assertTrue(codecs.canPlay(null))
        assertTrue(codecs.canPlay("AAC"))
        assertTrue(codecs.canPlay("OPUS", 48_000))
        assertEquals(emptyList(), decoders.asked)
    }

    @Test
    fun flacAsksForTheFlacDecoderAtTheStreamsRate() {
        val decoders = FakeDecoders(setOf("audio/flac"))
        assertTrue(DeviceCodecs(decoders).canPlay("FLAC", 96_000))
        assertEquals(listOf("audio/flac@96000"), decoders.asked)
    }

    @Test
    fun alacAndAc3AskForTheirOwnMimeTypes() {
        val alac = FakeDecoders(setOf("audio/alac"))
        assertTrue(DeviceCodecs(alac).canPlay("alac", 0))
        assertEquals(listOf("audio/alac@0"), alac.asked)

        val ac3 = FakeDecoders(setOf("audio/ac3"))
        assertTrue(DeviceCodecs(ac3).canPlay("AC-3", 48_000))
        assertEquals(listOf("audio/ac3@48000"), ac3.asked)
    }

    @Test
    fun atmosTriesTheJocDecoderThenThePlainOneAndEac3OnlyThePlainOne() {
        val plain = FakeDecoders(setOf("audio/eac3"))
        assertTrue(DeviceCodecs(plain).canPlay("DOLBY ATMOS", 48_000))
        assertEquals(listOf("audio/eac3-joc@48000", "audio/eac3@48000"), plain.asked)

        val joc = FakeDecoders(setOf("audio/eac3-joc"))
        assertTrue(DeviceCodecs(joc).canPlay("DOLBY ATMOS", 48_000))
        // the first that takes it is enough
        assertEquals(listOf("audio/eac3-joc@48000"), joc.asked)

        val eac3 = FakeDecoders(setOf("audio/eac3"))
        assertTrue(DeviceCodecs(eac3).canPlay("E-AC-3", 44_100))
        assertEquals(listOf("audio/eac3@44100"), eac3.asked)
    }

    @Test
    fun aCodecThePhoneCannotDecodeSaysSo() {
        val decoders = FakeDecoders(emptySet())
        assertEquals(false, DeviceCodecs(decoders).canPlay("FLAC", 192_000))
        assertEquals(false, DeviceCodecs(decoders).canPlay("DOLBY ATMOS", 0))
    }
}

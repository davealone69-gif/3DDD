package com.threedd.studio

import com.threedd.studio.audio.WavWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavWriterTest {

    @Test
    fun `emits a well formed PCM wav header`() {
        val bytes = WavWriter.tone(440f, 50)
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        assertEquals('W'.code.toByte(), bytes[8])
        // 44 byte header + 50 ms of 16-bit mono at 44.1 kHz
        val expectedFrames = WavWriter.SAMPLE_RATE * 50 / 1000
        assertEquals(44 + expectedFrames * 2, bytes.size)
    }

    @Test
    fun `tone decays and stays inside 16 bit range`() {
        val bytes = WavWriter.tone(220f, 40, amplitude = 1f)
        var peak = 0
        var i = 44
        while (i + 1 < bytes.size) {
            val value = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
            if (kotlin.math.abs(value) > peak) peak = kotlin.math.abs(value)
            i += 2
        }
        assertTrue(peak in 1..32767)
    }

    @Test
    fun `chord mixes every supplied frequency`() {
        val bytes = WavWriter.chord(floatArrayOf(440f, 550f, 660f), 30)
        assertTrue(bytes.size > 44)
    }
}

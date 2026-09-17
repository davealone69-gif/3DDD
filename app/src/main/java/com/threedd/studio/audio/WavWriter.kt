package com.threedd.studio.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Renders short 16-bit mono PCM tones to WAV bytes. Used for interface sounds. */
object WavWriter {

    const val SAMPLE_RATE = 44_100

    fun tone(frequency: Float, durationMs: Int, amplitude: Float = 0.5f, decay: Float = 6f): ByteArray {
        val frames = SAMPLE_RATE * durationMs / 1000
        val pcm = ShortArray(frames)
        for (i in 0 until frames) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = exp(-decay * i / frames.toFloat())
            val value = sin(2.0 * PI * frequency * t).toFloat() * amplitude * envelope
            pcm[i] = (value * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return encode(pcm)
    }

    fun chord(frequencies: FloatArray, durationMs: Int, amplitude: Float = 0.4f): ByteArray {
        val frames = SAMPLE_RATE * durationMs / 1000
        val pcm = ShortArray(frames)
        for (i in 0 until frames) {
            val t = i.toFloat() / SAMPLE_RATE
            var value = 0f
            frequencies.forEach { f -> value += sin(2.0 * PI * f * t).toFloat() }
            value = value / frequencies.size * amplitude * exp(-4f * i / frames)
            pcm[i] = (value * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return encode(pcm)
    }

    private fun encode(pcm: ShortArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size * 2)
        val dataSize = pcm.size * 2
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) = out.write(byteArrayOf(
            (v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(),
            (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte()))
        fun i16(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte()))

        ascii("RIFF"); i32(36 + dataSize); ascii("WAVE")
        ascii("fmt "); i32(16); i16(1); i16(1); i32(SAMPLE_RATE); i32(SAMPLE_RATE * 2)
        i16(2); i16(16)
        ascii("data"); i32(dataSize)
        pcm.forEach { i16(it.toInt()) }
        return out.toByteArray()
    }
}

package com.threedd.studio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A real-time generative ambient pad. Two detuned oscillator banks, a slow filter sweep,
 * a sub oscillator and a feedback delay are synthesised sample-by-sample on a render
 * thread and streamed through AudioTrack. No sample files are required.
 */
class AmbientSynth {

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var volume = 0.7f

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        val sampleRate = 44_100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 4 * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = created
        running = true
        created.setVolume(volume)
        created.play()

        thread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            renderLoop(created, sampleRate)
        }.apply { name = "3dd-ambient"; isDaemon = true; start() }
    }

    private fun renderLoop(track: AudioTrack, sampleRate: Int) {
        val block = 512
        val out = FloatArray(block * 2)
        var phaseA = 0.0
        var phaseB = 0.0
        var phaseC = 0.0
        var phaseSub = 0.0
        var filter = 0f
        var delayIndex = 0
        val delay = FloatArray(sampleRate / 2)
        val root = 110.0
        val rand = Random(3)

        while (running) {
            for (i in 0 until block) {
                val t = i.toDouble() / sampleRate
                val lfo = 0.5 + 0.5 * sin(2.0 * PI * 0.03 * t)
                phaseA += 2.0 * PI * root * 1.0 / sampleRate
                phaseB += 2.0 * PI * root * 1.005 / sampleRate
                phaseC += 2.0 * PI * root * 1.5 / sampleRate
                phaseSub += 2.0 * PI * root * 0.5 / sampleRate

                var sample = (sin(phaseA) * 0.28 + sin(phaseB) * 0.24 + sin(phaseC) * 0.14).toFloat()
                sample += (sin(phaseSub) * 0.16).toFloat()

                // one-pole low-pass whose cutoff breathes with the LFO
                val cutoff = 0.02f + 0.05f * lfo.toFloat()
                filter += (sample - filter) * cutoff
                var value = filter

                // feedback delay for a wide, diffuse tail
                val delayed = delay[delayIndex]
                delay[delayIndex] = value + delayed * 0.42f
                delayIndex = (delayIndex + 1) % delay.size
                value += delayed * 0.35f

                // gentle stereo spread
                val spread = (sin(2.0 * PI * 0.07 * t) * 0.5 + 0.5).toFloat()
                out[i * 2] = value * (0.75f + 0.25f * spread)
                out[i * 2 + 1] = value * (0.75f + 0.25f * (1f - spread))
            }
            if (running) {
                runCatching { track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING) }
            }
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        track?.setVolume(volume)
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}

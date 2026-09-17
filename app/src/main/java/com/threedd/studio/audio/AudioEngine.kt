package com.threedd.studio.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface sounds are synthesised to WAV at first run and played through SoundPool;
 * the ambient bed is generated live by [AmbientSynth]. Nothing is a placeholder sample.
 */
@Singleton
class AudioEngine @Inject constructor(@ApplicationContext private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ambient = AmbientSynth()
    private val soundIds = mutableMapOf<Cue, Int>()
    private var loaded = false
    var sfxEnabled: Boolean = true
    var volume: Float = 0.7f

    enum class Cue { TAP, SELECT, CONFIRM, ERROR, UNLOCK }

    private fun ensureLoaded() {
        if (loaded) return
        val dir = File(context.cacheDir, "cues").apply { mkdirs() }
        val definitions = mapOf(
            Cue.TAP to WavWriter.tone(880f, 60, 0.35f, 24f),
            Cue.SELECT to WavWriter.tone(660f, 90, 0.4f, 14f),
            Cue.CONFIRM to WavWriter.chord(floatArrayOf(523.25f, 659.25f, 783.99f), 320, 0.32f),
            Cue.ERROR to WavWriter.tone(196f, 260, 0.45f, 5f),
            Cue.UNLOCK to WavWriter.chord(floatArrayOf(392f, 523.25f, 659.25f, 1046.5f), 520, 0.3f)
        )
        definitions.forEach { (cue, bytes) ->
            val file = File(dir, "${cue.name.lowercase()}.wav")
            if (!file.exists() || file.length() == 0L) file.writeBytes(bytes)
            soundIds[cue] = soundPool.load(file.absolutePath, 1)
        }
        loaded = true
    }

    fun play(cue: Cue) {
        if (!sfxEnabled) return
        ensureLoaded()
        val id = soundIds[cue] ?: return
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun startAmbient() {
        ambient.setVolume(volume)
        ambient.start()
    }

    fun stopAmbient() = ambient.stop()

    val isAmbientRunning: Boolean get() = ambient.isRunning

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        ambient.setVolume(volume)
    }

    fun release() {
        ambient.stop()
        soundPool.release()
    }
}

package com.threedd.studio.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records and plays back vocal samples attached to an avatar. Playback supports rate and
 * pitch shifting through [MediaPlayer.setPlaybackParams], which is how a single recorded
 * line is retuned into a small vocal bank.
 */
@Singleton
class VocalBank @Inject constructor(@ApplicationContext private val context: Context) {

    data class Sample(val id: String, val label: String, val file: File, val durationMs: Long)

    private val dir: File get() = File(context.filesDir, "audio/vocals").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var activeFile: File? = null

    val samples: List<Sample>
        get() = dir.listFiles { f -> f.extension.equals("m4a", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Sample(it.nameWithoutExtension, it.nameWithoutExtension, it, 0L) }
            ?: emptyList()

    fun startRecording(label: String): Boolean {
        stopPlayback()
        val target = File(dir, "vocal-${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(target.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            activeFile = target
            Unit
        }.isSuccess
    }

    fun stopRecording(): Sample? {
        val rec = recorder ?: return null
        val file = activeFile
        runCatching { rec.stop() }
        rec.release()
        recorder = null
        activeFile = null
        if (file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        val duration = probeDuration(file)
        return Sample(file.nameWithoutExtension, file.nameWithoutExtension, file, duration)
    }

    fun cancelRecording() {
        val rec = recorder ?: return
        runCatching { rec.stop() }
        rec.release()
        recorder = null
        activeFile?.delete()
        activeFile = null
    }

    /** Plays [sample] transposed by [semitones] at [speed]x. */
    fun play(sample: Sample, semitones: Float = 0f, speed: Float = 1f, volume: Float = 0.8f) {
        stopPlayback()
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(sample.file.absolutePath)
            mp.prepare()
        }.onFailure {
            mp.release()
            return
        }
        val pitch = Math.pow(2.0, semitones / 12.0).toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mp.playbackParams = mp.playbackParams.setSpeed(speed).setPitch(pitch)
            }
        }
        mp.setVolume(volume, volume)
        mp.start()
        player = mp
    }

    fun stopPlayback() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    fun delete(sample: Sample): Boolean = sample.file.delete()

    private fun probeDuration(file: File): Long = runCatching {
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.prepare()
        val ms = mp.duration.toLong()
        mp.release()
        ms
    }.getOrDefault(0L)
}

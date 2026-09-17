package com.threedd.studio.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.render.OffscreenCapture
import com.threedd.studio.render.StudioRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * All four export paths: PNG still, baked GLB, turntable MP4 (MediaCodec -> MediaMuxer)
 * and turntable GIF (in-house encoder). Everything renders the real scene.
 */
class ExportManager(private val context: Context) {

    fun exportsDir(): File = File(context.filesDir, "exports").apply { mkdirs() }

    /** PNG still from the current camera, written to app storage and to the gallery. */
    suspend fun exportPng(
        renderer: StudioRenderer,
        width: Int = 1440,
        height: Int = 1920,
        name: String = "3doubled-${System.currentTimeMillis()}"
    ): File? = withContext(Dispatchers.Default) {
        val bitmap = OffscreenCapture(renderer).capture(width, height, System.nanoTime()) ?: return@withContext null
        val file = File(exportsDir(), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        publishImage(file, name)
        bitmap.recycle()
        file
    }

    /** Bakes morph weights into the loaded model and writes a new GLB. */
    suspend fun exportGlb(
        model: AvatarModel,
        weights: Map<String, Float>,
        name: String = "3doubled-${System.currentTimeMillis()}"
    ): File? = withContext(Dispatchers.IO) {
        val source = sourceFileFor(model) ?: return@withContext null
        val destination = File(exportsDir(), "$name.glb")
        if (GlbMorphWriter.bake(source, weights, destination)) destination else null
    }

    /** Renders a 360-degree turntable straight into an H.264 encoder surface. */
    suspend fun exportMp4(
        renderer: StudioRenderer,
        width: Int = 1080,
        height: Int = 1920,
        fps: Int = 30,
        seconds: Int = 8,
        onProgress: (Float) -> Unit = {},
        name: String = "3doubled-${System.currentTimeMillis()}"
    ): File? = withContext(Dispatchers.Default) {
        val file = File(exportsDir(), "$name.mp4")
        val frameCount = fps * seconds
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val swapChain = renderer.createEncoderSwapChain(surface)
        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        val frameDurationNs = 1_000_000_000L / fps
        val savedAzimuth = renderer.orbit.azimuth

        try {
            for (frame in 0 until frameCount) {
                renderer.orbit.azimuth = (savedAzimuth + (2.0 * PI * frame / frameCount)).toFloat()
                renderer.renderEncoderFrame(swapChain, frame * frameDurationNs)
                onProgress(frame.toFloat() / frameCount)

                var index = codec.dequeueOutputBuffer(bufferInfo, 0)
                while (index >= 0) {
                    val output = codec.getOutputBuffer(index) ?: break
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, output, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    index = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            codec.signalEndOfInputStream()
            var draining = true
            while (draining) {
                val index = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    index >= 0 -> {
                        val output = codec.getOutputBuffer(index)
                        if (output != null && bufferInfo.size > 0 && muxerStarted) {
                            output.position(bufferInfo.offset)
                            output.limit(bufferInfo.offset + bufferInfo.size)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                muxer.writeSampleData(trackIndex, output, bufferInfo)
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) draining = false
                    }
                }
            }
        } finally {
            renderer.orbit.azimuth = savedAzimuth
            renderer.destroyEncoderSwapChain(swapChain)
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
        }

        onProgress(1f)
        if (!muxerStarted) {
            file.delete()
            null
        } else {
            publishVideo(file, name)
            file
        }
    }

    /** Renders a short turntable into an animated GIF. */
    suspend fun exportGif(
        renderer: StudioRenderer,
        size: Int = 512,
        frames: Int = 48,
        onProgress: (Float) -> Unit = {},
        name: String = "3doubled-${System.currentTimeMillis()}"
    ): File? = withContext(Dispatchers.Default) {
        val encoder = GifEncoder(size, size, delayCentiseconds = 6, loop = true)
        val capture = OffscreenCapture(renderer)
        val savedAzimuth = renderer.orbit.azimuth
        try {
            for (frame in 0 until frames) {
                renderer.orbit.azimuth = (savedAzimuth + (2.0 * PI * frame / frames)).toFloat()
                val bitmap = capture.capture(size, size, frame * 33_000_000L) ?: continue
                encoder.addFrame(bitmap)
                bitmap.recycle()
                onProgress(frame.toFloat() / frames)
            }
        } finally {
            renderer.orbit.azimuth = savedAzimuth
        }
        val file = File(exportsDir(), "$name.gif")
        file.outputStream().use { encoder.encode(it) }
        onProgress(1f)
        file
    }

    /**
     * Resolves a bakeable source file. Packaged assets are copied out of the APK first, so
     * built-in rigs can be re-exported with their morph weights applied just like imports.
     */
    private fun sourceFileFor(model: AvatarModel): File? {
        if (model.isAsset) {
            val assetPath = model.location.removePrefix("models/")
            val target = File(context.cacheDir, "bake-" + assetPath.replace('/', '_'))
            return runCatching {
                context.assets.open("models/$assetPath").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.takeIf { it.length() > 0 }
            }.getOrNull()
        }
        val path = model.toUri().path ?: return null
        return File(path).takeIf { it.exists() }
    }

    private fun bitrateFor(width: Int, height: Int): Int {
        val pixels = width.toLong() * height
        return (pixels * 4).coerceAtMost(20_000_000L).toInt()
    }

    private fun publishImage(file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/3DoubleD")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun publishVideo(file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/3DoubleD")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    fun clearExports() {
        exportsDir().listFiles()?.forEach { it.delete() }
    }
}

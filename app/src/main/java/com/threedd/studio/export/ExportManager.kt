package com.threedd.studio.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
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

    /** Renders, finalizes, validates, and verifies a gallery copy of an H.264 MP4. */
    suspend fun exportMp4(
        renderer: StudioRenderer,
        width: Int = 1080,
        height: Int = 1920,
        fps: Int = 30,
        seconds: Int = 8,
        onProgress: (Float) -> Unit = {},
        name: String = "3doubled-${System.currentTimeMillis()}"
    ): File? = withContext(Dispatchers.Default) {
        require(width >= 16 && height >= 16 && width % 2 == 0 && height % 2 == 0) {
            "MP4 dimensions must be even and at least 16 pixels"
        }
        require(fps in 1..60) { "MP4 frame rate must be between 1 and 60 FPS" }
        require(seconds in 1..120) { "MP4 duration must be between 1 and 120 seconds" }

        val file = File(exportsDir(), "$name.mp4")
        file.delete()
        var muxer: MediaMuxer? = null
        var codec: MediaCodec? = null
        var swapChain: com.google.android.filament.SwapChain? = null
        var muxerStarted = false
        var finalized = false
        val savedAzimuth = renderer.orbit.azimuth
        try {
            val frameCount = Math.multiplyExact(fps, seconds)
            val activeMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(width, height))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
            val activeCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = activeCodec
            activeCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = activeCodec.createInputSurface()
            activeCodec.start()
            val activeSwapChain = renderer.createEncoderSwapChain(surface)
            swapChain = activeSwapChain

            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            val frameDurationNs = 1_000_000_000L / fps
            var outputEos = false

            for (frame in 0 until frameCount) {
                renderer.orbit.azimuth = (savedAzimuth + (2.0 * PI * frame / frameCount)).toFloat()
                renderer.renderEncoderFrame(activeSwapChain, frame * frameDurationNs)
                onProgress(frame.toFloat() / frameCount)

                var index = activeCodec.dequeueOutputBuffer(bufferInfo, 10_000)
                while (index >= 0) {
                    val output = activeCodec.getOutputBuffer(index)
                        ?: throw IllegalStateException("Encoder returned a null output buffer")
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            trackIndex = activeMuxer.addTrack(activeCodec.outputFormat)
                            activeMuxer.start()
                            muxerStarted = true
                        }
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        activeMuxer.writeSampleData(trackIndex, output, bufferInfo)
                    }
                    activeCodec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                    index = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
                if (outputEos) throw IllegalStateException("Encoder ended before all requested frames were submitted")
            }

            activeCodec.signalEndOfInputStream()
            var draining = true
            var drainDeadline = android.os.SystemClock.elapsedRealtime() + 30_000L
            while (draining) {
                if (android.os.SystemClock.elapsedRealtime() > drainDeadline) {
                    throw IllegalStateException("Timed out waiting for encoder end-of-stream")
                }
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
                            ?: throw IllegalStateException("Encoder returned a null output buffer while draining")
                        if (bufferInfo.size > 0 && muxerStarted &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            output.position(bufferInfo.offset)
                            output.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, output, bufferInfo)
                        }
                        val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                        if (eos) draining = false
                    }
                }
            }

            if (!muxerStarted) throw IllegalStateException("Encoder produced no muxable video track")
            // Finalization errors must escape. A partial MP4 is never reported as an export.
            activeCodec.stop()
            activeMuxer.stop()
            finalized = true
            onProgress(0.98f)

            validateMp4(file)
            publishVideo(file, name)
            onProgress(1f)
            file
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        } finally {
            renderer.orbit.azimuth = savedAzimuth
            swapChain?.let { runCatching { renderer.destroyEncoderSwapChain(it) } }
            codec?.let {
                if (!finalized) runCatching { it.stop() }
                runCatching { it.release() }
            }
            muxer?.let {
                if (muxerStarted && !finalized) runCatching { it.stop() }
                runCatching { it.release() }
            }
            // A failed export must not leave a partial file that looks successful.
            if (!finalized) file.delete()
        }
    }

    /** Validate the completed container and require a real, readable video track. */
    internal fun validateMp4(file: File): Mp4Validation {
        if (!file.isFile || file.length() < 1024L) {
            throw IllegalStateException("MP4 output is missing or too small to be a real video")
        }
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTrack = -1
            var width = 0
            var height = 0
            for (index in 0 until extractor.trackCount) {
                val track = extractor.getTrackFormat(index)
                if (track.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrack = index
                    width = track.getInteger(MediaFormat.KEY_WIDTH)
                    height = track.getInteger(MediaFormat.KEY_HEIGHT)
                    break
                }
            }
            if (videoTrack < 0 || width <= 0 || height <= 0) {
                throw IllegalStateException("MP4 has no valid video track or dimensions")
            }
            extractor.selectTrack(videoTrack)
            val sampleBuffer = ByteBuffer.allocate(1024 * 1024)
            var samples = 0
            while (true) {
                sampleBuffer.clear()
                val size = extractor.readSampleData(sampleBuffer, 0)
                if (size < 0) break
                if (size > 0) samples++
                extractor.advance()
            }
            if (samples < 1) throw IllegalStateException("MP4 video track contains no readable samples")

            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (durationMs <= 0L || frame == null) {
                frame?.recycle()
                throw IllegalStateException("MP4 has no positive duration or decodable video frame")
            }
            frame.recycle()
            return Mp4Validation(file.length(), durationMs, width, height, samples)
        } catch (error: IllegalStateException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("MP4 validation failed: ${error.message ?: error.javaClass.simpleName}", error)
        } finally {
            runCatching { extractor.release() }
            runCatching { retriever.release() }
        }
    }

    internal data class Mp4Validation(
        val bytes: Long,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val videoSamples: Int
    )

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
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the MP4 entry in the device gallery")
        try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open the gallery MP4 for writing")
            output.use { out -> file.inputStream().use { input -> input.copyTo(out) } }

            val galleryCopy = File(context.cacheDir, "gallery-verify-${System.nanoTime()}.mp4")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Gallery MP4 cannot be read back after saving")
                input.use { source -> galleryCopy.outputStream().use { target -> source.copyTo(target) } }
                if (galleryCopy.length() != file.length()) {
                    throw IllegalStateException("Gallery MP4 size differs from the validated export")
                }
                val sourceDigest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val galleryDigest = java.security.MessageDigest.getInstance("SHA-256").digest(galleryCopy.readBytes())
                if (!sourceDigest.contentEquals(galleryDigest)) {
                    throw IllegalStateException("Gallery MP4 bytes do not match the validated export")
                }
                validateMp4(galleryCopy)
            } finally {
                galleryCopy.delete()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                if (context.contentResolver.update(uri, published, null, null) != 1) {
                    throw IllegalStateException("Gallery MP4 could not be finalized")
                }
            }
        } catch (failure: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw failure
        }
    }

    fun clearExports() {
        exportsDir().listFiles()?.forEach { it.delete() }
    }
}

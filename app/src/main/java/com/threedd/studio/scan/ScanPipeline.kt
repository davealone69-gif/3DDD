package com.threedd.studio.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI

/**
 * End-to-end shape-from-silhouette pipeline: captured frames -> masks -> carved voxel
 * volume -> surfaced mesh -> textured GLB on disk.
 */
@Singleton
class ScanPipeline @Inject constructor(@ApplicationContext private val context: Context) {

    data class Progress(val stage: String, val fraction: Float)

    private val sessionsDir: File get() = File(context.filesDir, "scans").apply { mkdirs() }

    fun newSession(): File = File(sessionsDir, "session-${System.currentTimeMillis()}").apply { mkdirs() }

    fun frameFile(session: File, index: Int): File = File(session, "frame-%03d.jpg".format(index))

    /**
     * Reconstructs an avatar from [frames]. Frames are assumed to have been captured in a
     * turntable order, so azimuth is distributed evenly across the capture.
     */
    fun reconstruct(
        frames: List<File>,
        outputName: String,
        onProgress: (Progress) -> Unit = {}
    ): File? {
        if (frames.size < MIN_FRAMES) return null
        onProgress(Progress("Extracting silhouettes", 0.05f))

        val views = ArrayList<VisualHull.View>(frames.size)
        var firstBitmap: Bitmap? = null
        frames.forEachIndexed { index, file ->
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
            if (firstBitmap == null) firstBitmap = bitmap
            val silhouette = SilhouetteExtractor.extract(bitmap)
            if (bitmap != firstBitmap) bitmap.recycle()
            val azimuth = (2.0 * PI * index / frames.size).toFloat()
            views.add(VisualHull.View(silhouette.mask, silhouette.width, silhouette.height, azimuth))
            onProgress(Progress("Extracting silhouettes", 0.05f + 0.35f * (index + 1) / frames.size))
        }
        if (views.size < MIN_FRAMES) return null

        onProgress(Progress("Carving volume", 0.45f))
        val hull = VisualHull(resolution = VOXEL_RESOLUTION)
        hull.carve(views)
        if (hull.occupiedCount() < 64) return null

        onProgress(Progress("Surfacing mesh", 0.7f))
        val mesh = hull.buildMesh()
        if (mesh.triangleCount == 0) return null

        onProgress(Progress("Baking texture", 0.85f))
        val texture = firstBitmap?.let { encodePng(it) }
        firstBitmap?.recycle()

        val destination = File(sessionsDir, "$outputName.glb")
        GltfMeshWriter.write(mesh, texture, outputName, destination)
        onProgress(Progress("Done", 1f))
        return destination.takeIf { it.exists() && it.length() > 0 }
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
        return out.toByteArray()
    }

    fun clearSession(session: File) {
        session.listFiles()?.forEach { it.delete() }
        session.delete()
    }

    companion object {
        const val MIN_FRAMES = 8
        const val MAX_FRAMES = 24
        const val VOXEL_RESOLUTION = 56
    }
}

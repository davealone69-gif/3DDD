package com.threedd.studio.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.threedd.studio.data.avatar.PhotoMeshBuilder
import com.threedd.studio.data.avatar.TriangleMesh
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a 3D avatar from a single uploaded image (JPEG, PNG, WebP, HEIC or BMP).
 *
 * The subject is cut out with the same silhouette extractor the multi-frame scanner uses,
 * inflated into a closed mesh by [PhotoMeshBuilder], and the image itself becomes the
 * texture. Photos without a separable background still work: the builder falls back to a
 * centred oval so every upload produces an avatar.
 */
@Singleton
class PhotoAvatarBuilder @Inject constructor(@ApplicationContext private val context: Context) {

    data class Progress(val stage: String, val fraction: Float)

    data class Built(
        val file: File,
        val triangleCount: Int,
        val usedSilhouette: Boolean
    )

    private val avatarsDir: File get() = File(context.filesDir, "avatars").apply { mkdirs() }

    fun build(
        uri: Uri,
        name: String,
        depth: Float = 0.22f,
        onProgress: (Progress) -> Unit = {}
    ): Built? {
        onProgress(Progress("Reading image", 0.05f))
        val decoded = decode(uri) ?: return null

        onProgress(Progress("Finding the subject", 0.25f))
        val silhouette = runCatching { SilhouetteExtractor.extract(decoded) }.getOrNull()
        val coverage = silhouette?.coverage ?: 0f
        val usableSilhouette = silhouette != null && coverage in 0.02f..0.985f

        val mask: BooleanArray
        val maskWidth: Int
        val maskHeight: Int
        if (usableSilhouette) {
            mask = silhouette!!.mask
            maskWidth = silhouette.width
            maskHeight = silhouette.height
        } else {
            // Fall back to a centred oval so a photo without a plain background still
            // produces a sensible avatar instead of failing.
            maskWidth = 128
            maskHeight = 192
            mask = ovalMask(maskWidth, maskHeight)
        }

        onProgress(Progress("Inflating geometry", 0.5f))
        val mesh = PhotoMeshBuilder.build(
            mask, maskWidth, maskHeight,
            PhotoMeshBuilder.Options(depth = depth.coerceIn(0.05f, 0.5f))
        ) ?: return null

        onProgress(Progress("Baking texture", 0.75f))
        val texture = encodePng(decoded)
        decoded.recycle()

        onProgress(Progress("Writing model", 0.9f))
        val safeName = name.ifBlank { "photo-avatar" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(avatarsDir, "$safeName.glb")
        GltfMeshWriter.write(mesh, texture, safeName, destination)
        if (!destination.exists() || destination.length() == 0L) return null

        onProgress(Progress("Done", 1f))
        return Built(destination, mesh.triangleCount, usableSilhouette)
    }

    private fun decode(uri: Uri): Bitmap? {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull() ?: return null

        // Honour camera orientation so portrait shots are not laid on their side.
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        return if (matrix.isIdentity) bitmap else {
            val rotated = runCatching {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }.getOrNull()
            if (rotated == null) bitmap else {
                if (rotated != bitmap) bitmap.recycle()
                rotated
            }
        }
    }

    /** Downscales to a sane texture size and encodes PNG for the GLB. */
    private fun encodePng(source: Bitmap): ByteArray {
        val longest = maxOf(source.width, source.height)
        val scaled = if (longest > MAX_TEXTURE) {
            val ratio = MAX_TEXTURE.toFloat() / longest
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).toInt().coerceAtLeast(1),
                (source.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else source

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 92, out)
        if (scaled != source) scaled.recycle()
        return out.toByteArray()
    }

    private fun ovalMask(width: Int, height: Int): BooleanArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = true }
        canvas.drawOval(RectF(width * 0.08f, height * 0.03f, width * 0.92f, height * 0.97f), paint)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return BooleanArray(width * height) { (pixels[it] ushr 24) > 128 }
    }

    companion object {
        private const val MAX_TEXTURE = 1024
    }
}

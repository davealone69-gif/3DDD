package com.threedd.studio.render

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.android.filament.RenderTarget
import com.google.android.filament.Texture
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renders the studio scene into an offscreen colour target and reads it back as a Bitmap.
 * This is the same code path used by image and turntable exports.
 */
class OffscreenCapture(private val renderer: StudioRenderer) {

    fun capture(width: Int, height: Int, frameTimeNanos: Long): Bitmap? {
        val engine = renderer.engine
        val texture = Texture.Builder()
            .width(width)
            .height(height)
            .levels(1)
            .format(Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE)
            .build(engine)

        val target = RenderTarget.Builder()
            .texture(RenderTarget.AttachmentPoint.COLOR, texture)
            .build(engine)

        val previousTarget = renderer.view.renderTarget
        val previousViewport = renderer.view.viewport
        renderer.view.renderTarget = target
        renderer.view.viewport = Viewport(0, 0, width, height)
        renderer.orbit.apply(width.toDouble() / height.toDouble())

        // beginFrame needs a swap chain; render into the target and then discard the frame.
        val rendered = renderer.renderOffscreen(frameTimeNanos)
        if (!rendered) {
            renderer.view.renderTarget = previousTarget
            renderer.view.viewport = previousViewport
            engine.destroyRenderTarget(target)
            engine.destroyTexture(texture)
            return null
        }

        val bytes = ByteBuffer.allocateDirect(width * height * 4)
        val latch = CountDownLatch(1)
        val descriptor = Texture.PixelBufferDescriptor(
            bytes,
            Texture.Format.RGBA,
            Texture.Type.UBYTE,
            0, 0, 0, 0, 0, 0,
            Handler(Looper.getMainLooper())
        ) { latch.countDown() }

        renderer.renderer.readPixels(0, 0, width, height, descriptor)
        renderer.engine.flushAndWait()
        latch.await(5, TimeUnit.SECONDS)

        renderer.view.renderTarget = previousTarget
        renderer.view.viewport = previousViewport
        engine.destroyRenderTarget(target)
        engine.destroyTexture(texture)

        bytes.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(bytes)
        // Filament writes bottom-up; flip to top-down for standard image consumers.
        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
        if (flipped != bitmap) bitmap.recycle()
        return flipped
    }
}

package com.threedd.studio.render

import android.content.Context
import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.util.Log

/**
 * A SurfaceView that drives Filament from its own Choreographer callback.
 *
 * The render loop deliberately does NOT use Compose's frame clock: `withFrameNanos` is driven
 * by recomposition, so on a static screen the clock goes idle and the loop stops after a
 * single frame - which shows up as an empty viewport. Choreographer is tied to the display's
 * vsync and keeps producing frames regardless of what Compose is doing.
 */
class FilamentSurfaceView(
    context: Context,
    private val renderer: StudioRenderer
) : SurfaceView(context), SurfaceHolder.Callback, Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()
    @Volatile private var running = false
    private var lastFrameNanos = 0L

    /** Called once per rendered frame with the elapsed seconds, for animation stepping. */
    var onFrame: ((Float) -> Unit)? = null

    init {
        holder.setFormat(PixelFormat.RGBA_8888)
        holder.addCallback(this)
        renderer.attach(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stop()
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val delta = if (lastFrameNanos == 0L) 0f
        else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = frameTimeNanos

        try {
            onFrame?.invoke(delta)
            renderer.frame(frameTimeNanos)
        } catch (t: Throwable) {
            running = false
            Log.e(StudioRenderer.TAG, "render frame failed", t)
            return
        }
        choreographer.postFrameCallback(this)
    }

    fun dispose() {
        stop()
        holder.removeCallback(this)
        renderer.detach()
    }
}

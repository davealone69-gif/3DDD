package com.threedd.studio.ui.components

import android.graphics.PixelFormat
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.threedd.studio.render.StudioRenderer

/**
 * Hosts the Filament surface, drives the render loop from the Compose frame clock and
 * translates touch gestures into camera orbit / dolly.
 *
 * @param onFrame called once per rendered frame with the elapsed seconds, used to advance
 *                glTF animation on the same thread that renders.
 */
@Composable
fun FilamentViewport(
    renderer: StudioRenderer,
    modifier: Modifier = Modifier,
    onFrame: (Float) -> Unit = {}
) {
    val currentOnFrame = rememberUpdatedState(onFrame)

    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.setFormat(PixelFormat.RGBA_8888)
                setZOrderOnTop(false)
                renderer.attach(this)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    renderer.orbit.orbit(-pan.x * 0.008f, -pan.y * 0.008f)
                    if (zoom != 1f) renderer.orbit.dolly(1f / zoom)
                }
            }
    )

    DisposableEffect(renderer) {
        onDispose { renderer.detach() }
    }

    LaunchedEffect(renderer) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val delta = if (last == 0L) 0f else ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                currentOnFrame.value(delta)
                renderer.frame(now)
            }
        }
    }

    Box(modifier.fillMaxSize())
}

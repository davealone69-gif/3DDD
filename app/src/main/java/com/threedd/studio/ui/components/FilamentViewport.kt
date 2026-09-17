package com.threedd.studio.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.threedd.studio.render.FilamentSurfaceView
import com.threedd.studio.render.StudioRenderer

/**
 * Hosts the Filament surface and translates touch gestures into camera orbit / dolly.
 *
 * Frames come from the surface view's own Choreographer loop, not from Compose, so a static
 * screen still renders continuously.
 */
@Composable
fun FilamentViewport(
    renderer: StudioRenderer,
    modifier: Modifier = Modifier,
    onFrame: (Float) -> Unit = {}
) {
    val currentOnFrame = rememberUpdatedState(onFrame)
    val view = remember(renderer) {
        FilamentSurfaceView(renderer.applicationContext, renderer)
    }

    DisposableEffect(renderer) {
        view.onFrame = { delta -> currentOnFrame.value(delta) }
        view.start()
        onDispose {
            view.onFrame = null
            view.dispose()
        }
    }

    AndroidView(
        factory = { view },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(renderer) {
                detectTransformGestures { _, pan, zoom, _ ->
                    renderer.orbit.orbit(-pan.x * 0.008f, -pan.y * 0.008f)
                    if (zoom != 1f) renderer.orbit.dolly(1f / zoom)
                }
            }
    )

    Box(modifier.fillMaxSize())
}

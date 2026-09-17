package com.threedd.studio.render

import com.google.android.filament.Camera
import kotlin.math.cos
import kotlin.math.sin

/** Turntable / orbit rig for the viewport camera. */
class OrbitCamera(private val camera: Camera) {

    var targetX = 0.0f
    var targetY = 0.35f
    var targetZ = 0.0f
    var distance = 2.1f
    var azimuth = 0.0f
    var elevation = 0.08f
    var fovDegrees = 45.0f

    fun orbit(deltaAzimuth: Float, deltaElevation: Float) {
        azimuth += deltaAzimuth
        elevation = (elevation + deltaElevation).coerceIn(-1.25f, 1.25f)
    }

    fun dolly(scale: Float) {
        distance = (distance * scale).coerceIn(0.4f, 9.0f)
    }

    /** Frames a subject whose vertical extent is [height] and vertical centre is [centerY]. */
    fun frame(centerY: Float, height: Float) {
        targetY = centerY
        distance = (height * 2.35f).coerceIn(0.7f, 9.0f)
    }

    fun reset() {
        azimuth = 0f
        elevation = 0.08f
    }

    fun apply(aspect: Double) {
        camera.setProjection(fovDegrees.toDouble(), aspect, 0.05, 100.0, Camera.Fov.VERTICAL)
        val ce = cos(elevation)
        val x = targetX + distance * ce * sin(azimuth)
        val y = targetY + distance * sin(elevation)
        val z = targetZ + distance * ce * cos(azimuth)
        camera.lookAt(
            x.toDouble(), y.toDouble(), z.toDouble(),
            targetX.toDouble(), targetY.toDouble(), targetZ.toDouble(),
            0.0, 1.0, 0.0
        )
    }
}

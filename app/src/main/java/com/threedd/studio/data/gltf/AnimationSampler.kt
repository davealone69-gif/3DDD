package com.threedd.studio.data.gltf

import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Samples a glTF animation channel at a point in time.
 *
 * Pure Kotlin with no engine dependency, so the interpolation rules - including the
 * CUBICSPLINE key layout (in-tangent, value, out-tangent) and shortest-arc quaternion
 * interpolation - are directly unit testable.
 */
object AnimationSampler {

    /** Returns the channel value at [time], or null when the channel carries no usable data. */
    fun evaluate(channel: GltfDocument.Channel, time: Float): FloatArray? {
        val times = channel.times
        val values = channel.values
        val components = channel.componentCount
        if (times.isEmpty() || components <= 0) return null
        if (values.size < components) return null

        val stride = if (channel.interpolation == GltfDocument.INTERP_CUBICSPLINE) components * 3 else components
        val keyCount = minOf(times.size, values.size / stride)
        if (keyCount <= 0) return null

        if (keyCount == 1 || time <= times[0]) {
            return key(values, 0, stride, components, channel.interpolation)
        }
        if (time >= times[keyCount - 1]) {
            return key(values, keyCount - 1, stride, components, channel.interpolation)
        }

        var low = 0
        var high = keyCount - 1
        while (low + 1 < high) {
            val mid = (low + high) / 2
            if (times[mid] <= time) low = mid else high = mid
        }

        if (channel.interpolation == GltfDocument.INTERP_STEP) {
            return key(values, low, stride, components, channel.interpolation)
        }

        val t0 = times[low]
        val t1 = times[high]
        val span = t1 - t0
        if (span <= 1e-6f) return key(values, low, stride, components, channel.interpolation)
        val u = ((time - t0) / span).coerceIn(0f, 1f)

        if (channel.interpolation == GltfDocument.INTERP_CUBICSPLINE) {
            val out = FloatArray(components)
            for (c in 0 until components) {
                // Each key stores (in-tangent, value, out-tangent). Segment [low, high] uses
                // the OUT tangent of `low` and the IN tangent of `high`.
                val p0 = values.getOrElse(low * stride + components + c) { 0f }
                val m0 = values.getOrElse(low * stride + 2 * components + c) { 0f } * span
                val p1 = values.getOrElse(high * stride + components + c) { 0f }
                val m1 = values.getOrElse(high * stride + c) { 0f } * span
                val u2 = u * u
                val u3 = u2 * u
                out[c] = (2f * u3 - 3f * u2 + 1f) * p0 +
                    (u3 - 2f * u2 + u) * m0 +
                    (-2f * u3 + 3f * u2) * p1 +
                    (u3 - u2) * m1
            }
            return out
        }

        val a = key(values, low, stride, components, channel.interpolation) ?: return null
        val b = key(values, high, stride, components, channel.interpolation) ?: return null
        return if (channel.path == GltfDocument.PATH_ROTATION) {
            slerp(a, b, u)
        } else {
            FloatArray(components) { c -> a[c] + (b[c] - a[c]) * u }
        }
    }

    private fun key(values: FloatArray, index: Int, stride: Int, components: Int, interpolation: Int): FloatArray? {
        val base = if (interpolation == GltfDocument.INTERP_CUBICSPLINE) {
            index * stride + components
        } else {
            index * stride
        }
        if (base + components > values.size) return null
        return FloatArray(components) { values[base + it] }
    }

    /** Shortest-arc spherical interpolation; falls back to normalised lerp when nearly parallel. */
    fun slerp(a: FloatArray, b: FloatArray, u: Float): FloatArray {
        if (a.size < 4 || b.size < 4) return FloatArray(minOf(a.size, b.size)) { a[it] + (b[it] - a[it]) * u }
        var dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        var bx = b[0]
        var by = b[1]
        var bz = b[2]
        var bw = b[3]
        if (dot < 0f) {
            dot = -dot; bx = -bx; by = -by; bz = -bz; bw = -bw
        }
        if (dot > 0.9995f) {
            return normalize(floatArrayOf(a[0] + (bx - a[0]) * u, a[1] + (by - a[1]) * u, a[2] + (bz - a[2]) * u, a[3] + (bw - a[3]) * u))
        }
        val theta = acos(dot.coerceIn(-1f, 1f))
        val sinTheta = sin(theta)
        if (sinTheta < 1e-6f) return normalize(floatArrayOf(bx, by, bz, bw))
        val wa = sin((1f - u) * theta) / sinTheta
        val wb = sin(u * theta) / sinTheta
        return normalize(floatArrayOf(wa * a[0] + wb * bx, wa * a[1] + wb * by, wa * a[2] + wb * bz, wa * a[3] + wb * bw))
    }

    private fun normalize(q: FloatArray): FloatArray {
        val len = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (len < 1e-6f) return floatArrayOf(0f, 0f, 0f, 1f)
        return FloatArray(4) { q[it] / len }
    }
}

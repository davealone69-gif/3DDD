package com.threedd.studio

import com.threedd.studio.data.gltf.AnimationSampler
import com.threedd.studio.data.gltf.GltfDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class AnimationSamplerTest {

    private fun channel(
        path: Int,
        interpolation: Int,
        times: FloatArray,
        values: FloatArray,
        components: Int
    ) = GltfDocument.Channel(0, path, interpolation, times, values, components)

    @Test
    fun `step holds the previous key`() {
        val c = channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_STEP,
            floatArrayOf(0f, 1f, 2f),
            floatArrayOf(0f, 0f, 0f, 5f, 0f, 0f, 9f, 0f, 0f), 3)
        assertEquals(0f, AnimationSampler.evaluate(c, 0.9f)!![0], 1e-6f)
        assertEquals(5f, AnimationSampler.evaluate(c, 1.0f)!![0], 1e-6f)
        assertEquals(5f, AnimationSampler.evaluate(c, 1.99f)!![0], 1e-6f)
    }

    @Test
    fun `linear interpolates between keys`() {
        val c = channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_LINEAR,
            floatArrayOf(0f, 2f),
            floatArrayOf(0f, 0f, 0f, 10f, 20f, 30f), 3)
        val mid = AnimationSampler.evaluate(c, 1f)!!
        assertEquals(5f, mid[0], 1e-5f)
        assertEquals(10f, mid[1], 1e-5f)
        assertEquals(15f, mid[2], 1e-5f)
    }

    @Test
    fun `clamps outside the key range`() {
        val c = channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_LINEAR,
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 0f, 0f, 2f, 0f, 0f), 3)
        assertEquals(1f, AnimationSampler.evaluate(c, -5f)!![0], 1e-6f)
        assertEquals(2f, AnimationSampler.evaluate(c, 5f)!![0], 1e-6f)
    }

    @Test
    fun `single key channel returns that key`() {
        val c = channel(GltfDocument.PATH_SCALE, GltfDocument.INTERP_LINEAR,
            floatArrayOf(0f), floatArrayOf(3f, 3f, 3f), 3)
        assertEquals(3f, AnimationSampler.evaluate(c, 0.5f)!![0], 1e-6f)
    }

    @Test
    fun `cubicspline reads the middle value of each triple`() {
        // zero tangents: u=0 -> p0, u=1 -> p1, u=0.5 -> average
        val values = floatArrayOf(
            0f, 0f, 0f,     0f, 0f, 0f,     0f, 0f, 0f,
            0f, 0f, 0f,     10f, 0f, 0f,    0f, 0f, 0f
        )
        val c = channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_CUBICSPLINE,
            floatArrayOf(0f, 1f), values, 3)
        assertEquals(0f, AnimationSampler.evaluate(c, 0f)!![0], 1e-5f)
        assertEquals(10f, AnimationSampler.evaluate(c, 1f)!![0], 1e-5f)
        assertEquals(5f, AnimationSampler.evaluate(c, 0.5f)!![0], 1e-5f)
    }

    @Test
    fun `cubicspline honours tangents`() {
        // out-tangent of key0 pushes the curve above the straight line early on
        val values = floatArrayOf(
            0f, 0f, 0f,     0f, 0f, 0f,     8f, 0f, 0f,
            0f, 0f, 0f,     10f, 0f, 0f,    0f, 0f, 0f
        )
        val c = channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_CUBICSPLINE,
            floatArrayOf(0f, 1f), values, 3)
        assertEquals(2.5f, AnimationSampler.evaluate(c, 0.5f)!![0], 1e-4f)
    }

    @Test
    fun `rotation uses shortest arc slerp`() {
        // identity -> 90 degrees about Z, at the halfway point this is 45 degrees
        val half = 0.70710678f
        val c = channel(GltfDocument.PATH_ROTATION, GltfDocument.INTERP_LINEAR,
            floatArrayOf(0f, 1f),
            floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, half, half), 4)
        val mid = AnimationSampler.evaluate(c, 0.5f)!!
        val expectedHalf = kotlin.math.sin(Math.PI / 8).toFloat()
        val expectedW = kotlin.math.cos(Math.PI / 8).toFloat()
        assertEquals(0f, mid[0], 1e-5f)
        assertEquals(0f, mid[1], 1e-5f)
        assertEquals(expectedHalf, mid[2], 1e-5f)
        assertEquals(expectedW, mid[3], 1e-5f)
        val length = sqrt(mid.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, length, 1e-5f)
    }

    @Test
    fun `degenerate channels return null instead of throwing`() {
        assertEquals(null, AnimationSampler.evaluate(
            channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_LINEAR, FloatArray(0), FloatArray(0), 3), 0f))
        assertEquals(null, AnimationSampler.evaluate(
            channel(GltfDocument.PATH_TRANSLATION, GltfDocument.INTERP_LINEAR, floatArrayOf(0f), floatArrayOf(1f), 3), 0f))
    }
}

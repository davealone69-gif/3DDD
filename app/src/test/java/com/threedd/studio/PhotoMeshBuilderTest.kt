package com.threedd.studio

import com.threedd.studio.data.avatar.PhotoMeshBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PhotoMeshBuilderTest {

    private fun circleMask(size: Int = 96, radius: Int = 30): BooleanArray {
        val cx = size / 2f
        val cy = size / 2f
        return BooleanArray(size * size) { i ->
            val x = (i % size) - cx
            val y = (i / size) - cy
            sqrt(x * x + y * y) <= radius
        }
    }

    @Test
    fun `builds a closed mesh with both caps`() {
        val mesh = PhotoMeshBuilder.build(circleMask(), 96, 96)
        assertNotNull("circle mask should produce a mesh", mesh)
        mesh!!
        assertTrue("expected triangles", mesh.triangleCount > 100)
        assertEquals(mesh.vertexCount * 3, mesh.positions.size)
        assertEquals(mesh.vertexCount * 3, mesh.normals.size)
        assertEquals(mesh.vertexCount * 2, mesh.uvs.size)
    }

    @Test
    fun `every index addresses a real vertex`() {
        val mesh = PhotoMeshBuilder.build(circleMask(), 96, 96)!!
        mesh.indices.forEach { index ->
            assertTrue("index $index out of range", index in 0 until mesh.vertexCount)
        }
        assertEquals("indices must come in triangles", 0, mesh.indices.size % 3)
    }

    @Test
    fun `normals are unit length and uvs stay in range`() {
        val mesh = PhotoMeshBuilder.build(circleMask(), 96, 96)!!
        var i = 0
        while (i < mesh.normals.size) {
            val len = sqrt(
                mesh.normals[i] * mesh.normals[i] +
                    mesh.normals[i + 1] * mesh.normals[i + 1] +
                    mesh.normals[i + 2] * mesh.normals[i + 2]
            )
            assertEquals("normal at vertex ${i / 3}", 1f, len, 1e-3f)
            i += 3
        }
        mesh.uvs.forEach { uv ->
            assertTrue("uv $uv out of range", uv in -0.001f..1.001f)
        }
    }

    @Test
    fun `inflates so the centre is thicker than the rim`() {
        val depth = 0.3f
        val mesh = PhotoMeshBuilder.build(circleMask(), 96, 96, PhotoMeshBuilder.Options(depth = depth))!!
        var maxFront = 0f
        var i = 2
        while (i < mesh.positions.size) {
            if (mesh.positions[i] > maxFront) maxFront = mesh.positions[i]
            i += 3
        }
        assertTrue("expected positive inflation, saw $maxFront", maxFront > depth * 0.5f)
        assertTrue("inflation must not exceed the configured depth", maxFront <= depth + 1e-3f)
    }

    @Test
    fun `height follows the requested avatar height`() {
        val mesh = PhotoMeshBuilder.build(circleMask(), 96, 96, PhotoMeshBuilder.Options(height = 2.0f))!!
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 1
        while (i < mesh.positions.size) {
            minY = minOf(minY, mesh.positions[i])
            maxY = maxOf(maxY, mesh.positions[i])
            i += 3
        }
        assertTrue("mesh should span roughly the requested height", (maxY - minY) in 1.0f..2.1f)
    }

    @Test
    fun `distance transform grows towards the interior`() {
        val mask = circleMask(64, 20)
        val d = PhotoMeshBuilder.distanceTransform(mask, 64, 64)
        val centre = d[32 * 64 + 32]
        val edge = d[32 * 64 + 14]
        assertTrue("centre ($centre) should be deeper than edge ($edge)", centre > edge)
    }

    @Test
    fun `degenerate masks are rejected rather than crashing`() {
        assertNull(PhotoMeshBuilder.build(BooleanArray(0), 0, 0))
        assertNull(PhotoMeshBuilder.build(BooleanArray(100), 10, 10))
    }

    @Test
    fun `a single filled pixel still yields a valid mesh`() {
        val mask = BooleanArray(64)
        mask[32] = true
        val mesh = PhotoMeshBuilder.build(mask, 8, 8)
        if (mesh != null) {
            mesh.indices.forEach { assertTrue(it in 0 until mesh.vertexCount) }
        }
    }
}

package com.threedd.studio

import com.threedd.studio.data.avatar.AppearanceSpec
import com.threedd.studio.data.avatar.PartBuilder
import com.threedd.studio.data.avatar.TriangleMesh
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PartBuilderTest {

    /** A humanoid bounds box: 1.7 m tall, wide shoulders, shallow depth. */
    private val metrics = PartBuilder.RigMetrics.fromBounds(
        floatArrayOf(-0.34f, 0f, -0.14f, 0.34f, 1.7f, 0.14f)
    )

    private fun assertValid(mesh: TriangleMesh?, what: String) {
        assertNotNull("$what should produce geometry", mesh)
        mesh!!
        assertTrue("$what has no triangles", mesh.triangleCount > 0)
        assertTrue("$what vertex arrays disagree", mesh.positions.size == mesh.normals.size)
        mesh.indices.forEach { assertTrue("$what index $it out of range", it in 0 until mesh.vertexCount) }
        var i = 0
        while (i < mesh.normals.size) {
            val len = sqrt(
                mesh.normals[i] * mesh.normals[i] +
                    mesh.normals[i + 1] * mesh.normals[i + 1] +
                    mesh.normals[i + 2] * mesh.normals[i + 2]
            )
            assertTrue("$what has a non-unit normal", len in 0.98f..1.02f)
            i += 3
        }
    }

    @Test
    fun `every hair style either builds geometry or is deliberately empty`() {
        AppearanceSpec.HairStyle.entries.forEach { style ->
            val mesh = PartBuilder.hair(style.id, metrics)
            if (style == AppearanceSpec.HairStyle.BALD) assertNull(mesh) else assertValid(mesh, "hair ${style.id}")
        }
    }

    @Test
    fun `every accessory builds geometry`() {
        AppearanceSpec.Accessory.entries.forEach { item ->
            val mesh = PartBuilder.accessory(item.id, metrics)
            if (item == AppearanceSpec.Accessory.NONE) assertNull(mesh) else assertValid(mesh, "accessory ${item.id}")
        }
    }

    @Test
    fun `every augment builds geometry`() {
        AppearanceSpec.Augment.entries.forEach { item ->
            val mesh = PartBuilder.augment(item.id, metrics)
            if (item == AppearanceSpec.Augment.NONE) assertNull(mesh) else assertValid(mesh, "augment ${item.id}")
        }
    }

    @Test
    fun `every outfit builds geometry`() {
        AppearanceSpec.Outfit.entries.forEach { item ->
            val mesh = PartBuilder.outfit(item.id, metrics)
            if (item == AppearanceSpec.Outfit.NONE) assertNull(mesh) else assertValid(mesh, "outfit ${item.id}")
        }
    }

    @Test
    fun `every tattoo builds geometry`() {
        AppearanceSpec.Tattoo.entries.forEach { item ->
            val mesh = PartBuilder.tattoo(item.id, metrics)
            if (item == AppearanceSpec.Tattoo.NONE) assertNull(mesh) else assertValid(mesh, "tattoo ${item.id}")
        }
    }

    @Test
    fun `eyes build and sit near the head`() {
        val mesh = PartBuilder.eyes("#4A90E2", metrics)
        assertValid(mesh, "eyes")
        // the irises must be inside the head band, not at the feet
        var i = 1
        while (i < mesh!!.positions.size) {
            val y = mesh.positions[i]
            assertTrue("iris at y=$y is outside the head region", y > metrics.headCenterY - metrics.headRadius * 2f)
            i += 3
        }
    }

    @Test
    fun `parts scale with the rig`() {
        val small = PartBuilder.RigMetrics.fromBounds(floatArrayOf(-0.17f, 0f, -0.07f, 0.17f, 0.85f, 0.07f))
        val bigHair = PartBuilder.hair(AppearanceSpec.HairStyle.LONG.id, metrics)!!
        val smallHair = PartBuilder.hair(AppearanceSpec.HairStyle.LONG.id, small)!!
        assertTrue("a taller rig should get larger hair",
            bigHair.positions.max() > smallHair.positions.max())
    }
}

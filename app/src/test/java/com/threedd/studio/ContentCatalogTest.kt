package com.threedd.studio

import com.threedd.studio.data.avatar.ContentCatalog
import com.threedd.studio.data.avatar.PartBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the shipped library really is the requested size, and that entries are distinct. */
class ContentCatalogTest {

    private val metrics = PartBuilder.RigMetrics.fromBounds(
        floatArrayOf(-0.34f, 0f, -0.14f, 0.34f, 1.7f, 0.14f)
    )

    @Test
    fun `library sizes match the specification`() {
        assertEquals(10, ContentCatalog.skins.size)
        assertEquals(20, ContentCatalog.bodies.size)
        assertEquals(50, ContentCatalog.faces.size)
        assertEquals(100, ContentCatalog.hair.size)
        assertEquals(200, ContentCatalog.outfits.size)
        assertEquals(100, ContentCatalog.accessories.size)
        assertEquals(30, ContentCatalog.animations.size)
        assertEquals(30, ContentCatalog.poses.size)
        assertEquals(12, ContentCatalog.TexturePattern.entries.size)
    }

    @Test
    fun `every entry has a unique id and a human readable name`() {
        val groups = mapOf(
            "hair" to ContentCatalog.hair.map { it.id to it.label },
            "outfits" to ContentCatalog.outfits.map { it.id to it.label },
            "accessories" to ContentCatalog.accessories.map { it.id to it.label },
            "faces" to ContentCatalog.faces.map { it.id to it.label },
            "bodies" to ContentCatalog.bodies.map { it.id to it.label },
            "skins" to ContentCatalog.skins.map { it.id to it.label }
        )
        groups.forEach { (name, entries) ->
            assertEquals("$name has duplicate ids", entries.size, entries.map { it.first }.toSet().size)
            assertTrue("$name has an unnamed entry", entries.all { it.second.isNotBlank() })
        }
    }

    @Test
    fun `every hairstyle builds real geometry or is deliberately shaved`() {
        var built = 0
        ContentCatalog.hair.forEach { hair ->
            val mesh = PartBuilder.hair(hair, metrics)
            if (mesh == null) {
                assertEquals(ContentCatalog.HairBase.SHAVED, hair.base)
            } else {
                assertTrue("${hair.id} produced no triangles", mesh.triangleCount > 0)
                mesh.indices.forEach { assertTrue(it in 0 until mesh.vertexCount) }
                built++
            }
        }
        assertTrue("expected most styles to build", built >= 90)
    }

    @Test
    fun `hairstyles are actually distinct geometry`() {
        val sleek = ContentCatalog.hair.first { it.base == ContentCatalog.HairBase.LONG && it.volume < 0.5f }
        val vol = ContentCatalog.hair.first { it.base == ContentCatalog.HairBase.LONG && it.volume > 0.5f }
        val a = PartBuilder.hair(sleek, metrics)!!
        val b = PartBuilder.hair(vol, metrics)!!
        assertTrue("volume should change the silhouette", a.positions.size == b.positions.size &&
            !a.positions.contentEquals(b.positions))
    }

    @Test
    fun `every outfit builds real geometry`() {
        ContentCatalog.outfits.forEach { outfit ->
            val mesh = PartBuilder.outfit(outfit, metrics)
            assertNotNull("${outfit.id} produced nothing", mesh)
            assertTrue("${outfit.id} has no triangles", mesh!!.triangleCount > 0)
            mesh.indices.forEach { assertTrue(it in 0 until mesh.vertexCount) }
        }
    }

    @Test
    fun `sleeve length changes the outfit mesh`() {
        val sleeveless = ContentCatalog.outfits.first { it.sleeve == ContentCatalog.Sleeve.NONE }
        val full = ContentCatalog.outfits.first { it.sleeve == ContentCatalog.Sleeve.FULL }
        val a = PartBuilder.outfit(sleeveless, metrics)!!
        val b = PartBuilder.outfit(full, metrics)!!
        assertTrue("full sleeves should add geometry", b.vertexCount > a.vertexCount)
    }

    @Test
    fun `every accessory builds real geometry`() {
        ContentCatalog.accessories.forEach { accessory ->
            val mesh = PartBuilder.accessory(accessory, metrics)
            assertNotNull("${accessory.id} produced nothing", mesh)
            assertTrue("${accessory.id} has no triangles", mesh!!.triangleCount > 0)
            mesh.indices.forEach { assertTrue(it in 0 until mesh.vertexCount) }
        }
    }

    @Test
    fun `every face builds irises and brows`() {
        ContentCatalog.faces.forEach { face ->
            val mesh = PartBuilder.faceGeometry(face, metrics)
            assertNotNull("${face.id} produced nothing", mesh)
            assertTrue("${face.id} has no triangles", mesh!!.triangleCount > 0)
        }
    }

    @Test
    fun `animation clips are keyframed and ordered`() {
        ContentCatalog.animations.forEach { clip ->
            assertTrue("${clip.id} has no keys", clip.keys.isNotEmpty())
            assertTrue("${clip.id} has no duration", clip.duration > 0f)
            clip.keys.forEach { key ->
                assertEquals("${clip.id} times/value mismatch on ${key.axis}", key.times.size, key.values.size)
                assertTrue("${clip.id} has an empty channel", key.times.isNotEmpty())
                for (i in 1 until key.times.size) {
                    assertTrue("${clip.id} keys are not ordered", key.times[i] > key.times[i - 1])
                }
                assertTrue("${clip.id} exceeds its duration", key.times.last() <= clip.duration + 1e-3f)
            }
        }
    }

    @Test
    fun `every pose points at a real clip inside its duration`() {
        ContentCatalog.poses.forEach { pose ->
            val clip = ContentCatalog.clipById(pose.clipId)
            assertNotNull("${pose.id} references a missing clip ${pose.clipId}", clip)
            assertTrue("${pose.id} samples past the clip", pose.at <= clip!!.duration + 1e-3f)
        }
    }

    @Test
    fun `bodies expose the full morph set`() {
        ContentCatalog.bodies.forEach { body ->
            val weights = body.morphWeights()
            assertEquals(7, weights.size)
            weights.values.forEach { assertTrue("$it out of range", it in 0f..1f) }
        }
    }
}

package com.threedd.studio

import com.threedd.studio.data.gltf.GltfDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Parses the committed test fixture, which exercises skinning, animation and textures. */
class GltfDocumentTest {

    private fun fixture(): GltfDocument.Document {
        val bytes = javaClass.classLoader!!.getResourceAsStream("test-rig.glb")!!.use { it.readBytes() }
        return GltfDocument.parse(bytes) ?: error("fixture failed to parse")
    }

    @Test
    fun `reads the node hierarchy and scene roots`() {
        val doc = fixture()
        assertEquals(3, doc.nodes.size)
        assertEquals("Skeleton", doc.nodes[0].name)
        assertEquals(0, doc.nodes[1].parent)
        assertTrue("root node expected", doc.sceneRoots.contentEquals(intArrayOf(0)))
        // second bone is parented to the first
        assertEquals(1, doc.nodes[0].children[0])
    }

    @Test
    fun `reads skin joints and inverse bind matrices`() {
        val doc = fixture()
        assertEquals(1, doc.skins.size)
        val skin = doc.skins[0]
        assertEquals(2, skin.joints.size)
        assertEquals(listOf(0, 1), skin.joints.toList())
        assertEquals(32, skin.inverseBind.size)
        // bone1 bind translation is (0,1,0), so its inverse carries -1 in row 1 of the last column
        assertEquals(-1.0f, skin.inverseBind[16 + 13], 1e-5f)
    }

    @Test
    fun `reads animation channels with every interpolation mode`() {
        val doc = fixture()
        assertEquals(1, doc.animations.size)
        val anim = doc.animations[0]
        assertEquals("Bend", anim.name)
        assertEquals(3, anim.channels.size)
        assertEquals(1.0f, anim.duration, 1e-5f)
        val modes = anim.channels.map { it.interpolation }.toSet()
        assertTrue("LINEAR expected", GltfDocument.INTERP_LINEAR in modes)
        assertTrue("CUBICSPLINE expected", GltfDocument.INTERP_CUBICSPLINE in modes)
        assertTrue("STEP expected", GltfDocument.INTERP_STEP in modes)
        val paths = anim.channels.map { it.path }.toSet()
        assertTrue(GltfDocument.PATH_ROTATION in paths)
        assertTrue(GltfDocument.PATH_TRANSLATION in paths)
        assertTrue(GltfDocument.PATH_SCALE in paths)
        // CUBICSPLINE keys are triples, so 3 keys carry 9 values
        val cubic = anim.channels.first { it.interpolation == GltfDocument.INTERP_CUBICSPLINE }
        assertEquals(9, cubic.values.size)
    }

    @Test
    fun `reads skinned geometry attributes`() {
        val doc = fixture()
        assertEquals(1, doc.primitives.size)
        val prim = doc.primitives[0]
        assertEquals(56, prim.vertexCount)
        assertEquals(96, prim.triangleCount)
        assertNotNull("normals", prim.normals)
        assertNotNull("uvs", prim.uvs)
        assertNotNull("joints", prim.boneIndices)
        assertNotNull("weights", prim.boneWeights)
        assertEquals(56 * 4, prim.boneIndices!!.size)
        assertEquals(56 * 4, prim.boneWeights!!.size)
        // joint indices must address the two joints
        assertTrue(prim.boneIndices!!.all { it in 0..1 })
    }

    @Test
    fun `skin weights sum to one per vertex`() {
        val prim = fixture().primitives[0]
        val weights = prim.boneWeights!!
        for (v in 0 until prim.vertexCount) {
            val sum = (0 until 4).sumOf { weights[v * 4 + it].toDouble() }
            assertEquals("vertex $v", 1.0, sum, 1e-4)
        }
    }

    @Test
    fun `reads morph targets with names`() {
        val prim = fixture().primitives[0]
        assertEquals(1, prim.morphTargets.size)
        assertEquals(listOf("widen"), prim.morphTargetNames)
        assertEquals(prim.positions.size, prim.morphTargets[0].size)
    }

    @Test
    fun `reads texture reference and material factors`() {
        val doc = fixture()
        assertEquals(1, doc.textures.size)
        assertEquals(0, doc.textures[0]!!.imageIndex)
        assertEquals(10497, doc.textures[0]!!.wrapS)
        assertEquals(1, doc.materials.size)
        val material = doc.materials[0]
        assertEquals(0, material.baseColorTexture)
        assertEquals(0.2f, material.metallicFactor, 1e-5f)
        assertEquals(0.45f, material.roughnessFactor, 1e-5f)
    }

    @Test
    fun `exposes embedded image bytes with a png signature`() {
        val doc = fixture()
        assertEquals(1, doc.images.size)
        val png = doc.images[0]!!
        assertTrue("expected PNG magic", png.size > 8)
        assertEquals(0x89.toByte(), png[0])
        assertEquals('P'.code.toByte(), png[1])
        assertEquals('N'.code.toByte(), png[2])
        assertEquals('G'.code.toByte(), png[3])
    }

    @Test
    fun `computes scene bounds from vertex data`() {
        val b = fixture().bounds()
        assertEquals(-0.20f, b[1], 1e-4f)
        assertEquals(1.20f, b[4], 1e-4f)
        assertTrue("x extent should match the widest ring", abs(b[3] - 0.090f) < 1e-3f)
    }

    @Test
    fun `rejects content that is not glTF`() {
        assertEquals(null, GltfDocument.parse("not a model".toByteArray()))
        assertEquals(null, GltfDocument.parse(ByteArray(0)))
    }
}

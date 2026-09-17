package com.threedd.studio

import com.threedd.studio.data.gltf.GltfDocument
import com.threedd.studio.export.GlbMorphWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GlbMorphWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun fixtureBytes(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("test-rig.glb")!!.use { it.readBytes() }

    @Test
    fun `bakes morph weights into the exported geometry`() {
        val source = temp.newFile("src.glb").apply { writeBytes(fixtureBytes()) }
        val target = temp.newFile("out.glb")

        assertTrue("bake should report a change", GlbMorphWriter.bake(source, mapOf("widen" to 1f), target))

        val original = GltfDocument.parse(fixtureBytes())!!.primitives[0]
        val baked = GltfDocument.parse(target.readBytes())!!.primitives[0]
        assertEquals(original.vertexCount, baked.vertexCount)
        // the fixture's target scales x by 0.35, so baked x == 1.35 * original x
        assertEquals(1.35f * original.positions[0], baked.positions[0], 1e-4f)
        assertEquals(original.positions[1], baked.positions[1], 1e-5f)
    }

    @Test
    fun `zero weight leaves geometry untouched`() {
        val source = temp.newFile("src0.glb").apply { writeBytes(fixtureBytes()) }
        val target = temp.newFile("out0.glb")
        GlbMorphWriter.bake(source, mapOf("widen" to 0f), target)
        val original = GltfDocument.parse(fixtureBytes())!!.primitives[0]
        val baked = GltfDocument.parse(target.readBytes())!!.primitives[0]
        assertEquals(original.positions[0], baked.positions[0], 1e-6f)
    }

    @Test
    fun `rejects a file that is not glTF`() {
        val source = temp.newFile("bad.bin").apply { writeBytes(ByteArray(64) { 7 }) }
        val target = temp.newFile("bad-out.glb")
        assertEquals(false, GlbMorphWriter.bake(source, mapOf("widen" to 1f), target))
    }
}

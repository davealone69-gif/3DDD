package com.threedd.studio.render

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.threedd.studio.data.avatar.TriangleMesh
import com.threedd.studio.data.model.MaterialState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders the appearance parts - hair, eyewear, jewellery, augments, outfits and tattoos -
 * as their own renderables on top of the avatar.
 *
 * Parts are open shells, so they are drawn two-sided; the avatar itself keeps back-face
 * culling.
 */
class PartLayer(
    private val engine: Engine,
    private val scene: Scene,
    private val materialFactory: MaterialFactory
) {

    data class Spec(
        val mesh: TriangleMesh?,
        val state: MaterialState,
        val castShadows: Boolean = false
    )

    private class Part(
        val entity: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
        val instance: MaterialInstance
    )

    private val em = engine.entityManager
    private val parts = ArrayList<Part>()

    val partCount: Int get() = parts.size

    fun rebuild(specs: List<Spec>) {
        clear()
        specs.forEach { spec -> spec.mesh?.let { add(it, spec.state, spec.castShadows) } }
    }

    private fun add(mesh: TriangleMesh, state: MaterialState, castShadows: Boolean) {
        if (mesh.vertexCount < 3 || mesh.indices.isEmpty()) return
        val uvs = if (mesh.uvs.size >= mesh.vertexCount * 2) mesh.uvs else FloatArray(mesh.vertexCount * 2)
        val tangents = GeometryTools.packTangents(mesh.normals, mesh.positions, uvs, mesh.indices, null)

        val vertexBuffer = VertexBuffer.Builder()
            .vertexCount(mesh.vertexCount)
            .bufferCount(3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 0)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, floats(mesh.positions))
        vertexBuffer.setBufferAt(engine, 1, floats(tangents))
        vertexBuffer.setBufferAt(engine, 2, floats(uvs))

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(mesh.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, ints(mesh.indices))

        val instance = materialFactory.createInstance(state)
        val box = mesh.positions.bounds()
        val entity = em.create()
        RenderableManager.Builder(1)
            .boundingBox(Box(box[0], box[1], box[2], box[3], box[4], box[5]))
            .geometry(
                0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer,
                0, 0, mesh.vertexCount - 1, mesh.indices.size
            )
            .material(0, instance)
            .culling(false)
            .castShadows(castShadows)
            .receiveShadows(true)
            .build(engine, entity)

        scene.addEntity(entity)
        parts.add(Part(entity, vertexBuffer, indexBuffer, instance))
    }

    fun clear() {
        parts.forEach { part ->
            scene.removeEntity(part.entity)
            runCatching { engine.renderableManager.destroy(part.entity) }
            em.destroy(part.entity)
            engine.destroyVertexBuffer(part.vertexBuffer)
            engine.destroyIndexBuffer(part.indexBuffer)
            engine.destroyMaterialInstance(part.instance)
        }
        parts.clear()
    }

    fun destroy() = clear()

    private fun FloatArray.bounds(): FloatArray {
        if (isEmpty()) return floatArrayOf(0f, 0f, 0f, 0.5f, 0.5f, 0.5f)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i + 2 < size) {
            if (this[i] < minX) minX = this[i]
            if (this[i + 1] < minY) minY = this[i + 1]
            if (this[i + 2] < minZ) minZ = this[i + 2]
            if (this[i] > maxX) maxX = this[i]
            if (this[i + 1] > maxY) maxY = this[i + 1]
            if (this[i + 2] > maxZ) maxZ = this[i + 2]
            i += 3
        }
        return floatArrayOf(
            (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f,
            ((maxX - minX) / 2f).coerceAtLeast(0.01f),
            ((maxY - minY) / 2f).coerceAtLeast(0.01f),
            ((maxZ - minZ) / 2f).coerceAtLeast(0.01f)
        )
    }

    private fun floats(values: FloatArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        values.forEach { buffer.putFloat(it) }
        buffer.flip()
        return buffer
    }

    private fun ints(values: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        values.forEach { buffer.putInt(it) }
        buffer.flip()
        return buffer
    }
}

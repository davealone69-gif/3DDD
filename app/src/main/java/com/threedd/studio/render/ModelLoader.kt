package com.threedd.studio.render

import android.content.Context
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.threedd.studio.data.gltf.GltfDocument
import com.threedd.studio.data.model.AnimationClip
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.MaterialState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns parsed glTF geometry into Filament renderables.
 *
 * Every primitive becomes one entity with its own vertex/index buffers and its own instance
 * of the studio PBR material, so the Material Editor can drive glTF imports and the built-in
 * rigs through exactly the same code path. Morph targets are evaluated on the CPU and the
 * position buffer is re-uploaded, because Filament's MorphTargetBuffer JNI binding caps the
 * update count far below a real character mesh.
 */
class ModelLoader(
    private val context: Context,
    private val engine: Engine,
    private val scene: Scene,
    private val materialFactory: MaterialFactory
) {

    private class Piece(
        val entity: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
        val basePositions: FloatArray,
        val morphTargets: List<FloatArray>,
        val morphNames: List<String>,
        val materialInstance: MaterialInstance,
        val gltfState: MaterialState
    )

    private val em = engine.entityManager
    private val pieces = mutableListOf<Piece>()
    private var bounds = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)

    val triangleCount: Int get() = triangleCountInternal
    private var triangleCountInternal = 0

    val boundingHeight: Float
        get() = (bounds[4] - bounds[1]).takeIf { it > 0.01f } ?: 1.8f

    val boundingBoxCenterY: Float
        get() = (bounds[1] + bounds[4]) / 2f

    val morphTargetNames: List<String>
        get() = pieces.firstOrNull()?.morphNames ?: emptyList()

    /** No skeletal animation is applied: glTF nodes are baked into a single static mesh. */
    fun animations(): List<AnimationClip> = emptyList()

    fun load(model: AvatarModel): Boolean {
        unload()
        val bytes = readModel(model) ?: return false
        val document = GltfDocument.parse(bytes) ?: return false

        var created = 0
        document.primitives.forEach { primitive ->
            if (primitive.vertexCount < 3 || primitive.indices.isEmpty()) return@forEach
            val piece = buildPiece(primitive) ?: return@forEach
            pieces.add(piece)
            scene.addEntity(piece.entity)
            created++
        }
        if (created == 0) {
            unload()
            return false
        }
        bounds = document.bounds
        return true
    }

    private fun buildPiece(primitive: GltfDocument.Primitive): Piece? {
        val vertexCount = primitive.vertexCount
        val bufferCount = 1 +
            (if (primitive.normals != null) 1 else 0) +
            (if (primitive.uvs != null) 1 else 0) +
            (if (primitive.colors != null) 1 else 0)

        val builder = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(bufferCount)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 0
            )

        var index = 1
        var normalIndex = -1
        var uvIndex = -1
        var colorIndex = -1
        if (primitive.normals != null) {
            normalIndex = index
            builder.attribute(
                VertexBuffer.VertexAttribute.NORMAL, index,
                VertexBuffer.AttributeType.FLOAT3, 0, 0
            )
            index++
        }
        if (primitive.uvs != null) {
            uvIndex = index
            builder.attribute(
                VertexBuffer.VertexAttribute.UV0, index,
                VertexBuffer.AttributeType.FLOAT2, 0, 0
            )
            index++
        }
        if (primitive.colors != null) {
            colorIndex = index
            builder.attribute(
                VertexBuffer.VertexAttribute.COLOR, index,
                VertexBuffer.AttributeType.FLOAT4, 0, 0
            )
        }

        val vertexBuffer = builder.build(engine)
        vertexBuffer.setBufferAt(engine, 0, floats(primitive.positions))
        if (normalIndex >= 0) {
            vertexBuffer.setBufferAt(engine, normalIndex, floats(primitive.normals!!))
        }
        if (uvIndex >= 0) {
            vertexBuffer.setBufferAt(engine, uvIndex, floats(primitive.uvs!!))
        }
        if (colorIndex >= 0) {
            vertexBuffer.setBufferAt(engine, colorIndex, floats(primitive.colors!!))
        }

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(primitive.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, ints(primitive.indices))

        val gltfState = MaterialState(
            baseColorHex = primitive.material.baseColorFactor.toHex(),
            metallic = primitive.material.metallicFactor,
            roughness = primitive.material.roughnessFactor,
            reflectance = 0.5f,
            emissiveHex = "#000000",
            emissiveIntensity = 0f
        )
        val materialInstance = materialFactory.createInstance(gltfState)

        val centerX = (boundsOf(primitive.positions, 0, 3))
        val halfExtent = primitive.positions.halfExtents()
        val entity = em.create()
        RenderableManager.Builder(1)
            .boundingBox(Box(centerX[0], centerX[1], centerX[2], halfExtent[0], halfExtent[1], halfExtent[2]))
            .geometry(
                0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer,
                0, 0, vertexCount - 1, primitive.indices.size
            )
            .material(0, materialInstance)
            .culling(!primitive.material.doubleSided)
            .castShadows(true)
            .receiveShadows(true)
            .build(engine, entity)

        triangleCountInternal += primitive.triangleCount
        return Piece(
            entity = entity,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            basePositions = primitive.positions,
            morphTargets = primitive.morphTargets,
            morphNames = primitive.morphTargetNames,
            materialInstance = materialInstance,
            gltfState = gltfState
        )
    }

    /** Re-applies the studio material to every primitive, replacing the glTF factors. */
    fun overrideMaterials(state: MaterialState) {
        pieces.forEach { materialFactory.apply(it.materialInstance, state) }
    }

    /** Restores each primitive to the factors its glTF material declared. */
    fun clearMaterialOverride() {
        pieces.forEach { materialFactory.apply(it.materialInstance, it.gltfState) }
    }

    fun setMorphWeights(weights: Map<String, Float>) {
        pieces.forEach { piece ->
            if (piece.morphTargets.isEmpty() || piece.morphNames.isEmpty()) return@forEach
            var touched = false
            val out = piece.basePositions.copyOf()
            piece.morphTargets.forEachIndexed { targetIndex, delta ->
                val name = piece.morphNames.getOrNull(targetIndex) ?: return@forEachIndexed
                val weight = weights[name] ?: return@forEachIndexed
                if (weight == 0f) return@forEachIndexed
                val limit = minOf(out.size, delta.size)
                for (i in 0 until limit) out[i] += delta[i] * weight
                touched = true
            }
            if (touched) piece.vertexBuffer.setBufferAt(engine, 0, floats(out))
        }
    }

    fun unload() {
        pieces.forEach { piece ->
            scene.removeEntity(piece.entity)
            em.destroy(piece.entity)
            engine.destroyVertexBuffer(piece.vertexBuffer)
            engine.destroyIndexBuffer(piece.indexBuffer)
            engine.destroyMaterialInstance(piece.materialInstance)
        }
        pieces.clear()
        triangleCountInternal = 0
        bounds = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
    }

    fun destroy() = unload()

    // ---- helpers ----

    private fun boundsOf(values: FloatArray, componentOffset: Int, stride: Int): FloatArray {
        if (values.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = componentOffset
        while (i + 2 < values.size) {
            if (values[i] < minX) minX = values[i]
            if (values[i + 1] < minY) minY = values[i + 1]
            if (values[i + 2] < minZ) minZ = values[i + 2]
            if (values[i] > maxX) maxX = values[i]
            if (values[i + 1] > maxY) maxY = values[i + 1]
            if (values[i + 2] > maxZ) maxZ = values[i + 2]
            i += stride
        }
        return floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
    }

    private fun FloatArray.halfExtents(): FloatArray {
        if (isEmpty()) return floatArrayOf(0.5f, 0.5f, 0.5f)
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
            ((maxX - minX) / 2f).coerceAtLeast(0.01f),
            ((maxY - minY) / 2f).coerceAtLeast(0.01f),
            ((maxZ - minZ) / 2f).coerceAtLeast(0.01f)
        )
    }

    private fun FloatArray.toHex(): String {
        fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt()
        val r = channel(getOrElse(0) { 1f })
        val g = channel(getOrElse(1) { 1f })
        val b = channel(getOrElse(2) { 1f })
        return "#%02X%02X%02X".format(r, g, b)
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

    private fun readModel(model: AvatarModel): ByteArray? = if (model.isAsset) {
        val path = model.location.removePrefix("models/")
        runCatching { context.assets.open("models/$path").use { it.readBytes() } }.getOrNull()
    } else {
        val file = model.toUri().path?.let { File(it) }
        if (file == null || !file.exists()) null else runCatching { file.readBytes() }.getOrNull()
    }
}

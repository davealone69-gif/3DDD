package com.threedd.studio.render

import android.content.Context
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.threedd.studio.data.gltf.AnimationSampler
import com.threedd.studio.data.gltf.GltfDocument
import com.threedd.studio.data.model.AnimationClip
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.MaterialState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Builds Filament scene objects from a parsed glTF document.
 *
 * One entity per glTF node carries the node's transform, so rigid node animation works; mesh
 * primitives hang off those entities, or off the scene root when the mesh is skinned (the
 * glTF spec says a skinned mesh node's own transform is ignored). Skins are driven through
 * RenderableManager bone matrices, and morph targets are evaluated on the CPU and re-uploaded
 * because Filament's MorphTargetBuffer JNI binding caps the update count below a real mesh.
 */
class ModelLoader(
    private val context: Context,
    private val engine: Engine,
    private val scene: Scene,
    private val materialFactory: MaterialFactory
) {

    private class Piece(
        val entity: Int,
        val renderableInstance: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
        val basePositions: FloatArray,
        val morphTargets: List<FloatArray>,
        val morphNames: List<String>,
        val materialInstance: MaterialInstance,
        val gltfState: MaterialState,
        val boneTransformInstances: IntArray?,
        val inverseBind: FloatArray?,
        val boneBuffer: FloatBuffer?
    ) {
        var uploadedWeights: FloatArray? = null
    }

    private val em = engine.entityManager
    private val tm = engine.transformManager
    private val rm = engine.renderableManager

    private val pieces = ArrayList<Piece>()
    private val nodeEntities = ArrayList<Int>()
    private val nodeTransformInstances = ArrayList<Int>()
    private val nodeChildren = ArrayList<IntArray>()
    private val nodeBaseTrs = ArrayList<FloatArray>()
    private var document: GltfDocument.Document? = null
    private var bounds = floatArrayOf(-1f, -1f, -1f, 1f, 1f, 1f)
    private var triangleTotal = 0

    private val skins = ArrayList<GltfDocument.Skin?>()
    private var animationList: List<GltfDocument.Animation> = emptyList()
    private var activeAnimation = -1
    private var currentWeights: Map<String, Float> = emptyMap()
    private val scratch16 = FloatArray(16)
    private val scratch16b = FloatArray(16)

    val boundingHeight: Float
        get() = (bounds[4] - bounds[1]).takeIf { it > 0.01f } ?: 1.8f

    val boundingBoxCenterY: Float get() = (bounds[1] + bounds[4]) / 2f

    val triangleCount: Int get() = triangleTotal

    val morphTargetNames: List<String>
        get() = pieces.firstOrNull()?.morphNames ?: emptyList()

    fun animations(): List<AnimationClip> =
        animationList.mapIndexed { index, a -> AnimationClip(index, a.name, a.duration) }

    fun load(model: AvatarModel): Boolean {
        unload()
        val bytes = readModel(model) ?: return false
        val parsed = GltfDocument.parse(bytes) ?: return false
        document = parsed

        buildNodeGraph(parsed)

        var created = 0
        parsed.primitives.forEach { primitive ->
            if (primitive.vertexCount < 3 || primitive.indices.isEmpty()) return@forEach
            if (buildPiece(parsed, primitive) != null) created++
        }
        if (created == 0) {
            unload()
            return false
        }

        bounds = parsed.bounds()
        animationList = parsed.animations
        applyAnimation(0, 0f)
        return true
    }

    // ---- scene graph ----

    private fun buildNodeGraph(parsed: GltfDocument.Document) {
        parsed.nodes.forEach { node ->
            val entity = em.create()
            nodeEntities.add(entity)
            val parentInstance = if (node.parent >= 0) nodeTransformInstances.getOrNull(node.parent) ?: 0 else 0
            val local = node.matrix ?: compose(node.translation, node.rotation, node.scale)
            val instance = if (node.parent >= 0 && parentInstance != 0) {
                tm.create(entity, parentInstance, local)
            } else {
                val root = tm.create(entity)
                tm.setTransform(root, local)
                root
            }
            nodeTransformInstances.add(instance)
            nodeChildren.add(node.children)
            nodeBaseTrs.add(
                floatArrayOf(
                    node.translation[0], node.translation[1], node.translation[2],
                    node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3],
                    node.scale[0], node.scale[1], node.scale[2],
                    if (node.matrix != null) 1f else 0f
                )
            )
        }
    }

    private fun buildPiece(parsed: GltfDocument.Document, primitive: GltfDocument.Primitive): Piece? {
        val vertexCount = primitive.vertexCount
        val normals = primitive.normals ?: computeNormals(primitive.positions, primitive.indices)
        val tangents = packTangents(normals, primitive.positions, primitive.uvs, primitive.indices, primitive.tangents)
        val uvs = primitive.uvs ?: FloatArray(vertexCount * 2)

        val boneIndices = primitive.boneIndices
        val boneWeights = primitive.boneWeights
        val skinned = boneIndices != null && boneWeights != null

        var bufferCount = 3 // position, tangents, uv0
        var colorIndex = -1
        if (primitive.colors != null) { colorIndex = bufferCount; bufferCount++ }
        var jointIndex = -1
        var weightIndex = -1
        if (skinned) { jointIndex = bufferCount; bufferCount++; weightIndex = bufferCount; bufferCount++ }

        val builder = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(bufferCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 0)

        if (colorIndex >= 0) {
            builder.attribute(VertexBuffer.VertexAttribute.COLOR, colorIndex, VertexBuffer.AttributeType.FLOAT4, 0, 0)
        }
        if (jointIndex >= 0) {
            builder.attribute(VertexBuffer.VertexAttribute.BONE_INDICES, jointIndex, VertexBuffer.AttributeType.UBYTE4, 0, 0)
            builder.attribute(VertexBuffer.VertexAttribute.BONE_WEIGHTS, weightIndex, VertexBuffer.AttributeType.FLOAT4, 0, 0)
        }

        val vertexBuffer = builder.build(engine)
        vertexBuffer.setBufferAt(engine, 0, floats(primitive.positions))
        vertexBuffer.setBufferAt(engine, 1, floats(tangents))
        vertexBuffer.setBufferAt(engine, 2, floats(uvs))
        if (colorIndex >= 0) vertexBuffer.setBufferAt(engine, colorIndex, floats(primitive.colors!!))
        if (jointIndex >= 0) {
            vertexBuffer.setBufferAt(engine, jointIndex, bytesOf(boneIndices!!))
            vertexBuffer.setBufferAt(engine, weightIndex, floats(boneWeights!!))
        }

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(primitive.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, ints(primitive.indices))

        val material = parsed.materials.getOrNull(primitive.materialIndex)
            ?: GltfDocument.Material()
        val gltfState = MaterialState(
            baseColorHex = material.baseColorFactor.toHex(),
            metallic = material.metallicFactor,
            roughness = material.roughnessFactor,
            reflectance = 0.5f,
            emissiveHex = material.emissiveFactor.toHex(),
            emissiveIntensity = if (material.emissiveFactor.any { it > 0f }) 1f else 0f,
            normalScale = material.normalScale,
            occlusionStrength = material.occlusionStrength
        )
        val textures = materialFactory.texturesFor(parsed, material)
        val materialInstance = materialFactory.createInstance(gltfState, textures, material.alphaMode)

        val half = primitive.positions.halfExtents()
        val centre = primitive.positions.centre()
        val entity = em.create()

        val skin = parsed.skins.getOrNull(
            if (primitive.nodeIndex in parsed.nodes.indices) parsed.nodes[primitive.nodeIndex].skinIndex else -1
        )

        val renderableBuilder = RenderableManager.Builder(1)
            .boundingBox(Box(centre[0], centre[1], centre[2], half[0], half[1], half[2]))
            .geometry(
                0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer,
                0, 0, vertexCount - 1, primitive.indices.size
            )
            .material(0, materialInstance)
            .culling(!material.doubleSided)
            .castShadows(true)
            .receiveShadows(true)

        var boneTransformInstances: IntArray? = null
        var inverseBind: FloatArray? = null
        var boneBuffer: FloatBuffer? = null

        if (skinned && skin != null && skin.joints.isNotEmpty()) {
            val boneCount = minOf(skin.joints.size, MAX_BONES)
            renderableBuilder.skinning(boneCount)
            boneTransformInstances = IntArray(boneCount) { j ->
                nodeTransformInstances.getOrElse(skin.joints[j]) { 0 }
            }
            inverseBind = skin.inverseBind
            boneBuffer = ByteBuffer.allocateDirect(boneCount * 16 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        renderableBuilder.build(engine, entity)
        val renderableInstance = rm.getInstance(entity)

        // A skinned mesh is placed at the scene root: the glTF spec ignores the node transform
        // because the skin matrices already produce world-space positions.
        val attachToNode = !(skinned && skin != null)
        if (attachToNode) {
            val parentInstance = nodeTransformInstances.getOrElse(primitive.nodeIndex) { 0 }
            val childTransform = tm.create(entity)
            if (parentInstance != 0) tm.setParent(childTransform, parentInstance)
        } else {
            tm.create(entity)
        }

        scene.addEntity(entity)
        triangleTotal += primitive.triangleCount

        return Piece(
            entity = entity,
            renderableInstance = renderableInstance,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            basePositions = primitive.positions,
            morphTargets = primitive.morphTargets,
            morphNames = primitive.morphTargetNames,
            materialInstance = materialInstance,
            gltfState = gltfState,
            boneTransformInstances = boneTransformInstances,
            inverseBind = inverseBind,
            boneBuffer = boneBuffer
        ).also { pieces.add(it) }
    }

    // ---- material overrides ----

    fun overrideMaterials(state: MaterialState) {
        pieces.forEach { materialFactory.apply(it.materialInstance, state) }
    }

    fun clearMaterialOverride() {
        pieces.forEach { materialFactory.apply(it.materialInstance, it.gltfState) }
    }

    // ---- morph targets ----

    fun setMorphWeights(weights: Map<String, Float>) {
        currentWeights = weights
        pieces.forEach { piece ->
            if (piece.morphTargets.isEmpty() || piece.morphNames.isEmpty()) return@forEach
            val values = FloatArray(piece.morphNames.size) { i -> weights[piece.morphNames[i]] ?: 0f }
            val previous = piece.uploadedWeights
            if (previous != null && previous.contentEquals(values)) return@forEach
            piece.uploadedWeights = values
            val out = piece.basePositions.copyOf()
            piece.morphTargets.forEachIndexed { targetIndex, delta ->
                val weight = values.getOrElse(targetIndex) { 0f }
                if (weight == 0f) return@forEachIndexed
                val limit = minOf(out.size, delta.size)
                for (i in 0 until limit) out[i] += delta[i] * weight
            }
            piece.vertexBuffer.setBufferAt(engine, 0, floats(out))
        }
    }

    // ---- animation ----

    /**
     * Evaluates [time] seconds of clip [index]: node TRS channels are written straight to the
     * TransformManager, weight channels drive the morph targets, and any skinned primitive then
     * gets fresh bone matrices.
     */
    fun applyAnimation(index: Int, time: Float) {
        val animation = animationList.getOrNull(index) ?: run {
            uploadBones()
            return
        }
        activeAnimation = index

        val animatedNodes = HashSet<Int>()
        var animatedWeights: MutableMap<String, Float>? = null

        animation.channels.forEach { channel ->
            val value = AnimationSampler.evaluate(channel, time) ?: return@forEach
            animatedNodes.add(channel.nodeIndex)
            when (channel.path) {
                GltfDocument.PATH_TRANSLATION -> nodeBaseTrs.getOrNull(channel.nodeIndex)?.let {
                    it[0] = value[0]; it[1] = value[1]; it[2] = value[2]
                }
                GltfDocument.PATH_ROTATION -> nodeBaseTrs.getOrNull(channel.nodeIndex)?.let {
                    it[3] = value[0]; it[4] = value[1]; it[5] = value[2]; it[6] = value[3]
                }
                GltfDocument.PATH_SCALE -> nodeBaseTrs.getOrNull(channel.nodeIndex)?.let {
                    it[7] = value[0]; it[8] = value[1]; it[9] = value[2]
                }
                GltfDocument.PATH_WEIGHTS -> {
                    val names = pieces.firstOrNull()?.morphNames ?: emptyList()
                    val map = animatedWeights ?: LinkedHashMap<String, Float>().also { animatedWeights = it }
                    names.forEachIndexed { i, name ->
                        if (i < value.size) map[name] = value[i]
                    }
                }
            }
        }

        animatedNodes.forEach { nodeIndex ->
            val instance = nodeTransformInstances.getOrNull(nodeIndex) ?: return@forEach
            val trs = nodeBaseTrs.getOrNull(nodeIndex) ?: return@forEach
            val matrix = if (trs.getOrElse(10) { 0f } == 1f) {
                // Node declared an explicit matrix and has no TRS to recompose.
                compose(
                    floatArrayOf(trs[0], trs[1], trs[2]),
                    floatArrayOf(trs[3], trs[4], trs[5], trs[6]),
                    floatArrayOf(trs[7], trs[8], trs[9])
                )
            } else {
                compose(
                    floatArrayOf(trs[0], trs[1], trs[2]),
                    floatArrayOf(trs[3], trs[4], trs[5], trs[6]),
                    floatArrayOf(trs[7], trs[8], trs[9])
                )
            }
            tm.setTransform(instance, matrix)
        }

        animatedWeights?.let { setMorphWeights(it) }
        uploadBones()
    }

    private fun uploadBones() {
        pieces.forEach { piece ->
            val transformInstances = piece.boneTransformInstances ?: return@forEach
            val inverseBind = piece.inverseBind ?: return@forEach
            val buffer = piece.boneBuffer ?: return@forEach
            buffer.clear()
            transformInstances.forEachIndexed { j, instance ->
                if (instance == 0) {
                    repeat(16) { buffer.put(if (it % 5 == 0) 1f else 0f) }
                    return@forEachIndexed
                }
                val world = tm.getWorldTransform(instance, scratch16)
                val inverse = inverseBind
                val offset = j * 16
                if (offset + 16 <= inverse.size) {
                    multiply(world, inverse, offset, scratch16b)
                    buffer.put(scratch16b)
                } else {
                    repeat(16) { buffer.put(if (it % 5 == 0) 1f else 0f) }
                }
            }
            buffer.flip()
            rm.setBonesAsMatrices(piece.renderableInstance, buffer, transformInstances.size, 0)
        }
    }

    fun unload() {
        pieces.forEach { piece ->
            scene.removeEntity(piece.entity)
            runCatching { rm.destroy(piece.entity) }
            runCatching { tm.destroy(piece.entity) }
            em.destroy(piece.entity)
            engine.destroyVertexBuffer(piece.vertexBuffer)
            engine.destroyIndexBuffer(piece.indexBuffer)
            engine.destroyMaterialInstance(piece.materialInstance)
        }
        pieces.clear()
        nodeEntities.forEach { entity ->
            runCatching { tm.destroy(entity) }
            em.destroy(entity)
        }
        nodeEntities.clear()
        nodeTransformInstances.clear()
        nodeChildren.clear()
        nodeBaseTrs.clear()
        skins.clear()
        animationList = emptyList()
        activeAnimation = -1
        triangleTotal = 0
        document = null
        bounds = floatArrayOf(-1f, -1f, -1f, 1f, 1f, 1f)
    }

    fun destroy() = unload()

    // ---- maths helpers (column-major, matching glTF and Filament) ----

    private fun compose(t: FloatArray, r: FloatArray, s: FloatArray): FloatArray {
        val x = r[0]; val y = r[1]; val z = r[2]; val w = r[3]
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        val m = FloatArray(16)
        m[0] = (1 - 2 * (yy + zz)) * s[0]
        m[1] = (2 * (xy + wz)) * s[0]
        m[2] = (2 * (xz - wy)) * s[0]
        m[4] = (2 * (xy - wz)) * s[1]
        m[5] = (1 - 2 * (xx + zz)) * s[1]
        m[6] = (2 * (yz + wx)) * s[1]
        m[8] = (2 * (xz + wy)) * s[2]
        m[9] = (2 * (yz - wx)) * s[2]
        m[10] = (1 - 2 * (xx + yy)) * s[2]
        m[12] = t[0]; m[13] = t[1]; m[14] = t[2]; m[15] = 1f
        return m
    }

    /** result = a * b, where b starts at inverseBind[offset]. */
    private fun multiply(a: FloatArray, b: FloatArray, offset: Int, out: FloatArray) {
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[offset + c * 4 + k]
                out[c * 4 + row] = sum
            }
        }
    }

    /** Area-weighted vertex normals, used when an asset does not carry NORMAL. */
    private fun computeNormals(positions: FloatArray, indices: IntArray): FloatArray {
        val out = FloatArray(positions.size)
        var i = 0
        while (i + 2 < indices.size) {
            val a = indices[i] * 3
            val b = indices[i + 1] * 3
            val c = indices[i + 2] * 3
            if (a + 2 < positions.size && b + 2 < positions.size && c + 2 < positions.size) {
                val e1x = positions[b] - positions[a]
                val e1y = positions[b + 1] - positions[a + 1]
                val e1z = positions[b + 2] - positions[a + 2]
                val e2x = positions[c] - positions[a]
                val e2y = positions[c + 1] - positions[a + 1]
                val e2z = positions[c + 2] - positions[a + 2]
                val nx = e1y * e2z - e1z * e2y
                val ny = e1z * e2x - e1x * e2z
                val nz = e1x * e2y - e1y * e2x
                out[a] += nx; out[a + 1] += ny; out[a + 2] += nz
                out[b] += nx; out[b + 1] += ny; out[b + 2] += nz
                out[c] += nx; out[c + 1] += ny; out[c + 2] += nz
            }
            i += 3
        }
        return out
    }

    /**
     * Encodes vertex normals as the quaternion Filament decodes in common_math.glsl:
     *   n = (0,0,1) + (2,-2,-2)qx(qz,qw,qx) + (2,2,-2)qy(qw,qz,qy)
     * which is the third column of the rotation matrix built from the quaternion. When the
     * asset supplies UVs we derive a real mikktspace-style tangent so normal maps are oriented
     * correctly; otherwise an arbitrary orthonormal basis is used, which is fine because only
     * the normal affects shading unless a normal map is bound.
     */
    private fun packTangents(
        normals: FloatArray,
        positions: FloatArray,
        uvs: FloatArray?,
        indices: IntArray,
        gltfTangents: FloatArray?
    ): FloatArray {
        val count = normals.size / 3
        val out = FloatArray(count * 4)
        val derived = if (uvs != null && uvs.size >= count * 2) deriveTangents(positions, normals, uvs, indices) else null

        for (i in 0 until count) {
            var nx = normals[i * 3]
            var ny = normals[i * 3 + 1]
            var nz = normals[i * 3 + 2]
            val nlen = sqrt(nx * nx + ny * ny + nz * nz)
            if (nlen < 1e-6f) { nx = 0f; ny = 0f; nz = 1f } else { nx /= nlen; ny /= nlen; nz /= nlen }

            var tx: Float
            var ty: Float
            var tz: Float
            var handedness = 1f
            when {
                gltfTangents != null && gltfTangents.size >= count * 4 -> {
                    tx = gltfTangents[i * 4]; ty = gltfTangents[i * 4 + 1]; tz = gltfTangents[i * 4 + 2]
                    handedness = if (gltfTangents[i * 4 + 3] < 0f) -1f else 1f
                    val len = sqrt(tx * tx + ty * ty + tz * tz)
                    if (len < 1e-6f) { tx = 1f; ty = 0f; tz = 0f } else { tx /= len; ty /= len; tz /= len }
                }
                derived != null -> {
                    tx = derived[i * 3]; ty = derived[i * 3 + 1]; tz = derived[i * 3 + 2]
                    val len = sqrt(tx * tx + ty * ty + tz * tz)
                    if (len < 1e-6f) { tx = 1f; ty = 0f; tz = 0f } else { tx /= len; ty /= len; tz /= len }
                }
                else -> {
                    val useX = abs(nx) < 0.9f
                    val rx = if (useX) 1f else 0f
                    val ry = if (useX) 0f else 1f
                    tx = ry * nz; ty = -rx * nz; tz = rx * ny - ry * nx
                    val len = sqrt(tx * tx + ty * ty + tz * tz)
                    if (len < 1e-6f) { tx = 1f; ty = 0f; tz = 0f } else { tx /= len; ty /= len; tz /= len }
                }
            }

            // Gram-Schmidt: make the tangent perpendicular to the normal.
            val d = tx * nx + ty * ny + tz * nz
            tx -= nx * d; ty -= ny * d; tz -= nz * d
            val tl = sqrt(tx * tx + ty * ty + tz * tz)
            if (tl < 1e-6f) {
                val useX = abs(nx) < 0.9f
                val rx = if (useX) 1f else 0f
                val ry = if (useX) 0f else 1f
                tx = ry * nz; ty = -rx * nz; tz = rx * ny - ry * nx
                val l2 = sqrt(tx * tx + ty * ty + tz * tz)
                if (l2 < 1e-6f) { tx = 1f; ty = 0f; tz = 0f } else { tx /= l2; ty /= l2; tz /= l2 }
            } else {
                tx /= tl; ty /= tl; tz /= tl
            }

            val bx = ny * tz - nz * ty
            val by = nz * tx - nx * tz
            val bz = nx * ty - ny * tx

            val m00 = tx; val m10 = ty; val m20 = tz
            val m01 = bx * handedness; val m11 = by * handedness; val m21 = bz * handedness
            val m02 = nx; val m12 = ny; val m22 = nz

            val trace = m00 + m11 + m22
            var qx: Float
            var qy: Float
            var qz: Float
            var qw: Float
            when {
                trace > 0f -> {
                    val s = sqrt(trace + 1f) * 2f
                    qw = 0.25f * s
                    qx = (m21 - m12) / s
                    qy = (m02 - m20) / s
                    qz = (m10 - m01) / s
                }
                m00 > m11 && m00 > m22 -> {
                    val s = sqrt(1f + m00 - m11 - m22) * 2f
                    qw = (m21 - m12) / s
                    qx = 0.25f * s
                    qy = (m01 + m10) / s
                    qz = (m02 + m20) / s
                }
                m11 > m22 -> {
                    val s = sqrt(1f + m11 - m00 - m22) * 2f
                    qw = (m02 - m20) / s
                    qx = (m01 + m10) / s
                    qy = 0.25f * s
                    qz = (m12 + m21) / s
                }
                else -> {
                    val s = sqrt(1f + m22 - m00 - m11) * 2f
                    qw = (m10 - m01) / s
                    qx = (m02 + m20) / s
                    qy = (m12 + m21) / s
                    qz = 0.25f * s
                }
            }
            if (qw < 0f) { qx = -qx; qy = -qy; qz = -qz; qw = -qw }
            out[i * 4] = qx; out[i * 4 + 1] = qy; out[i * 4 + 2] = qz; out[i * 4 + 3] = qw
        }
        return out
    }

    /** Kenney/mikktspace style per-vertex tangents accumulated from UV derivatives. */
    private fun deriveTangents(
        positions: FloatArray, normals: FloatArray, uvs: FloatArray, indices: IntArray
    ): FloatArray {
        val count = positions.size / 3
        val tan = FloatArray(count * 3)
        var i = 0
        while (i + 2 < indices.size) {
            val i0 = indices[i]; val i1 = indices[i + 1]; val i2 = indices[i + 2]
            if (i0 < count && i1 < count && i2 < count) {
                val x0 = positions[i0 * 3]; val y0 = positions[i0 * 3 + 1]; val z0 = positions[i0 * 3 + 2]
                val x1 = positions[i1 * 3]; val y1 = positions[i1 * 3 + 1]; val z1 = positions[i1 * 3 + 2]
                val x2 = positions[i2 * 3]; val y2 = positions[i2 * 3 + 1]; val z2 = positions[i2 * 3 + 2]
                val u0 = uvs[i0 * 2]; val v0 = uvs[i0 * 2 + 1]
                val u1 = uvs[i1 * 2]; val v1 = uvs[i1 * 2 + 1]
                val u2 = uvs[i2 * 2]; val v2 = uvs[i2 * 2 + 1]
                val e1x = x1 - x0; val e1y = y1 - y0; val e1z = z1 - z0
                val e2x = x2 - x0; val e2y = y2 - y0; val e2z = z2 - z0
                val du1 = u1 - u0; val dv1 = v1 - v0
                val du2 = u2 - u0; val dv2 = v2 - v0
                val det = du1 * dv2 - du2 * dv1
                if (abs(det) > 1e-9f) {
                    val r = 1f / det
                    val tx = (e1x * dv2 - e2x * dv1) * r
                    val ty = (e1y * dv2 - e2y * dv1) * r
                    val tz = (e1z * dv2 - e2z * dv1) * r
                    tan[i0 * 3] += tx; tan[i0 * 3 + 1] += ty; tan[i0 * 3 + 2] += tz
                    tan[i1 * 3] += tx; tan[i1 * 3 + 1] += ty; tan[i1 * 3 + 2] += tz
                    tan[i2 * 3] += tx; tan[i2 * 3 + 1] += ty; tan[i2 * 3 + 2] += tz
                }
            }
            i += 3
        }
        return tan
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

    private fun FloatArray.centre(): FloatArray {
        if (isEmpty()) return floatArrayOf(0f, 0f, 0f)
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
        return floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
    }

    private fun FloatArray.toHex(): String {
        fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt()
        val r = channel(getOrElse(0) { 1f })
        val g = channel(getOrElse(1) { 1f })
        val b = channel(getOrElse(2) { 1f })
        val a = channel(getOrElse(3) { 1f })
        return if (a >= 255) "#%02X%02X%02X".format(r, g, b)
        else "#%02X%02X%02X%02X".format(r, g, b, a)
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

    private fun bytesOf(values: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size).order(ByteOrder.nativeOrder())
        values.forEach { buffer.put((it and 0xFF).toByte()) }
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

    companion object {
        /** Filament's setBonesAsMatrices accepts at most 255 bones. */
        const val MAX_BONES = 255
    }
}

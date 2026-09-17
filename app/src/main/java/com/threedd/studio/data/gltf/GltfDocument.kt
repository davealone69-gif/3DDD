package com.threedd.studio.data.gltf

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Self-contained glTF 2.0 / GLB reader.
 *
 * Produces flat float arrays ready to hand to Filament vertex buffers, with node transforms
 * baked into the geometry. Supports .glb containers, JSON .gltf with embedded or sibling
 * buffers, float and integer-normalised accessors, per-primitive morph targets, and the
 * material factors needed to shade a primitive.
 */
object GltfDocument {

    private const val MAGIC_GLB = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    data class Material(
        val baseColorFactor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
        val metallicFactor: Float = 1f,
        val roughnessFactor: Float = 1f,
        val doubleSided: Boolean = false,
        val unlit: Boolean = false
    )

    data class Primitive(
        val positions: FloatArray,
        val normals: FloatArray?,
        val uvs: FloatArray?,
        val colors: FloatArray?,
        val indices: IntArray,
        val morphTargets: List<FloatArray>,
        val morphTargetNames: List<String>,
        val material: Material
    ) {
        val vertexCount: Int get() = positions.size / 3
        val triangleCount: Int get() = indices.size / 3
    }

    data class Document(
        val primitives: List<Primitive>,
        val morphTargetNames: List<String>
    ) {
        val bounds: FloatArray
            get() {
                if (primitives.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
                var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
                primitives.forEach { p ->
                    var i = 0
                    while (i < p.positions.size) {
                        val x = p.positions[i]; val y = p.positions[i + 1]; val z = p.positions[i + 2]
                        if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
                        if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
                        i += 3
                    }
                }
                return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
            }
    }

    /** Returns null when [bytes] is not a usable glTF asset. */
    fun parse(bytes: ByteArray): Document? {
        val container = readContainer(bytes) ?: return null
        val json = container.json
        val gltf = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val buffers = resolveBuffers(gltf, container.bin)
        val accessors = gltf.optJSONArray("accessors") ?: JSONArray()
        val bufferViews = gltf.optJSONArray("bufferViews") ?: JSONArray()
        val meshes = gltf.optJSONArray("meshes") ?: return null
        val nodes = gltf.optJSONArray("nodes") ?: JSONArray()
        val materials = readMaterials(gltf)

        val primitives = ArrayList<Primitive>()

        fun emitMesh(meshIndex: Int, world: FloatArray, meshTargetNames: List<String>) {
            val mesh = meshes.optJSONObject(meshIndex) ?: return
            val prims = mesh.optJSONArray("primitives") ?: return
            for (i in 0 until prims.length()) {
                val prim = prims.getJSONObject(i)
                val attributes = prim.optJSONObject("attributes") ?: continue

                val positions = readVec3(accessors, bufferViews, buffers, attributes.optInt("POSITION", -1))
                    ?: continue
                val normals = readVec3(accessors, bufferViews, buffers, attributes.optInt("NORMAL", -1))
                val uvs = readVec2(accessors, bufferViews, buffers, attributes.optInt("TEXCOORD_0", -1))
                val colors = readVec4(accessors, bufferViews, buffers, attributes.optInt("COLOR_0", -1))
                val indices = readIndices(accessors, bufferViews, buffers, prim.optInt("indices", -1))
                    ?: IntArray(positions.size / 3) { it }

                transformPositions(positions, world)
                normals?.let { transformNormals(it, world) }

                val targets = ArrayList<FloatArray>()
                prim.optJSONArray("targets")?.let { array ->
                    for (t in 0 until array.length()) {
                        val acc = array.getJSONObject(t).optInt("POSITION", -1)
                        val delta = readVec3(accessors, bufferViews, buffers, acc) ?: continue
                        transformDirections(delta, world)
                        targets.add(delta)
                    }
                }

                val materialIndex = if (prim.has("material")) prim.optInt("material", -1) else -1
                val material = materials.getOrNull(materialIndex) ?: Material()

                primitives.add(
                    Primitive(
                        positions = positions,
                        normals = normals,
                        uvs = uvs,
                        colors = colors,
                        indices = indices,
                        morphTargets = targets,
                        morphTargetNames = meshTargetNames,
                        material = material
                    )
                )
            }
        }

        val scenes = gltf.optJSONArray("scenes")
        val sceneIndex = gltf.optInt("scene", 0)
        val roots = scenes?.optJSONObject(sceneIndex)?.optJSONArray("nodes")

        if (roots != null) {
            for (r in 0 until roots.length()) {
                walk(nodes, roots.optInt(r, -1), identity(), 0) { meshIndex, world ->
                    emitMesh(meshIndex, world, meshTargetNames(meshes, meshIndex))
                }
            }
        } else {
            // No scene graph: treat every node with a mesh as a root.
            walk(nodes, -1, identity(), 0) { meshIndex, world ->
                emitMesh(meshIndex, world, meshTargetNames(meshes, meshIndex))
            }
        }

        if (primitives.isEmpty()) return null
        val names = primitives.firstOrNull()?.morphTargetNames ?: emptyList()
        return Document(primitives, names)
    }

    private fun meshTargetNames(meshes: JSONArray, meshIndex: Int): List<String> {
        val extras = meshes.optJSONObject(meshIndex)?.optJSONObject("extras") ?: return emptyList()
        val arr = extras.optJSONArray("targetNames") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it, "target$it") }
    }

    private fun readMaterials(gltf: JSONObject): List<Material> {
        val array = gltf.optJSONArray("materials") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val m = array.getJSONObject(i)
            val pbr = m.optJSONObject("pbrMetallicRoughness")
            val base = pbr?.optJSONArray("baseColorFactor")
            val factor = if (base != null) floatArrayOf(
                base.optDouble(0, 1.0).toFloat(), base.optDouble(1, 1.0).toFloat(),
                base.optDouble(2, 1.0).toFloat(), base.optDouble(3, 1.0).toFloat()
            ) else floatArrayOf(1f, 1f, 1f, 1f)
            Material(
                baseColorFactor = factor,
                metallicFactor = pbr?.optDouble("metallicFactor", 1.0)?.toFloat() ?: 1f,
                roughnessFactor = pbr?.optDouble("roughnessFactor", 1.0)?.toFloat() ?: 1f,
                doubleSided = m.optBoolean("doubleSided", false),
                unlit = m.has("extensions") &&
                    m.optJSONObject("extensions")?.has("KHR_materials_unlit") == true
            )
        }
    }

    // ---- scene graph ----

    private fun walk(
        nodes: JSONArray,
        start: Int,
        parent: FloatArray,
        depth: Int,
        visit: (meshIndex: Int, world: FloatArray) -> Unit
    ) {
        if (depth > 32) return
        val indices = if (start >= 0) intArrayOf(start) else IntArray(nodes.length()) { it }
        indices.forEach { index ->
            val node = nodes.optJSONObject(index) ?: return@forEach
            val world = multiply(parent, nodeMatrix(node))
            if (node.has("mesh")) visit(node.optInt("mesh", -1), world)
            node.optJSONArray("children")?.let { children ->
                for (c in 0 until children.length()) {
                    walk(nodes, children.optInt(c, -1), world, depth + 1, visit)
                }
            }
        }
    }

    private fun nodeMatrix(node: JSONObject): FloatArray {
        node.optJSONArray("matrix")?.let { m ->
            if (m.length() == 16) {
                return FloatArray(16) { m.optDouble(it, 0.0).toFloat() }
            }
        }
        val t = node.optJSONArray("translation")
        val r = node.optJSONArray("rotation")
        val s = node.optJSONArray("scale")
        val tx = t?.optDouble(0, 0.0)?.toFloat() ?: 0f
        val ty = t?.optDouble(1, 0.0)?.toFloat() ?: 0f
        val tz = t?.optDouble(2, 0.0)?.toFloat() ?: 0f
        val qx = r?.optDouble(0, 0.0)?.toFloat() ?: 0f
        val qy = r?.optDouble(1, 0.0)?.toFloat() ?: 0f
        val qz = r?.optDouble(2, 0.0)?.toFloat() ?: 0f
        val qw = r?.optDouble(3, 1.0)?.toFloat() ?: 1f
        val sx = s?.optDouble(0, 1.0)?.toFloat() ?: 1f
        val sy = s?.optDouble(1, 1.0)?.toFloat() ?: 1f
        val sz = s?.optDouble(2, 1.0)?.toFloat() ?: 1f

        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
        val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
        val wx = qw * qx; val wy = qw * qy; val wz = qw * qz

        val m = FloatArray(16)
        m[0] = (1 - 2 * (yy + zz)) * sx
        m[1] = (2 * (xy + wz)) * sx
        m[2] = (2 * (xz - wy)) * sx
        m[4] = (2 * (xy - wz)) * sy
        m[5] = (1 - 2 * (xx + zz)) * sy
        m[6] = (2 * (yz + wx)) * sy
        m[8] = (2 * (xz + wy)) * sz
        m[9] = (2 * (yz - wx)) * sz
        m[10] = (1 - 2 * (xx + yy)) * sz
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
        return m
    }

    private fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[c * 4 + k]
                r[c * 4 + row] = sum
            }
        }
        return r
    }

    private fun transformPositions(values: FloatArray, m: FloatArray) {
        var i = 0
        while (i < values.size) {
            val x = values[i]; val y = values[i + 1]; val z = values[i + 2]
            values[i] = m[0] * x + m[4] * y + m[8] * z + m[12]
            values[i + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
            values[i + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
            i += 3
        }
    }

    private fun transformNormals(values: FloatArray, m: FloatArray) {
        var i = 0
        while (i < values.size) {
            val x = values[i]; val y = values[i + 1]; val z = values[i + 2]
            var nx = m[0] * x + m[4] * y + m[8] * z
            var ny = m[1] * x + m[5] * y + m[9] * z
            var nz = m[2] * x + m[6] * y + m[10] * z
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) { nx /= len; ny /= len; nz /= len } else { nx = 0f; ny = 1f; nz = 0f }
            values[i] = nx; values[i + 1] = ny; values[i + 2] = nz
            i += 3
        }
    }

    /** Morph deltas are directions: rotation/scale apply, translation does not. */
    private fun transformDirections(values: FloatArray, m: FloatArray) {
        var i = 0
        while (i < values.size) {
            val x = values[i]; val y = values[i + 1]; val z = values[i + 2]
            values[i] = m[0] * x + m[4] * y + m[8] * z
            values[i + 1] = m[1] * x + m[5] * y + m[9] * z
            values[i + 2] = m[2] * x + m[6] * y + m[10] * z
            i += 3
        }
    }

    // ---- container / buffer handling ----

    private class Container(val json: String, val bin: ByteArray?)

    private fun readContainer(bytes: ByteArray): Container? {
        if (bytes.size >= 20) {
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (header.int == MAGIC_GLB) {
                header.int
                header.int
                val jsonLength = header.int
                val jsonType = header.int
                if (jsonType != CHUNK_JSON) return null
                val json = String(bytes, 20, jsonLength, Charsets.UTF_8)
                var offset = 20 + jsonLength
                var bin: ByteArray? = null
                if (offset + 8 <= bytes.size) {
                    val chunk = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN)
                    val length = chunk.int
                    val type = chunk.int
                    if (type == CHUNK_BIN) {
                        bin = bytes.copyOfRange(offset + 8, minOf(offset + 8 + length, bytes.size))
                    }
                }
                return Container(json, bin)
            }
        }
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
        return if (text.trimStart().startsWith("{")) Container(text, null) else null
    }

    private fun resolveBuffers(gltf: JSONObject, bin: ByteArray?): List<ByteArray?> {
        val declared = gltf.optJSONArray("buffers") ?: return listOf(bin)
        val result = ArrayList<ByteArray?>(declared.length())
        for (i in 0 until declared.length()) {
            val uri = declared.optJSONObject(i)?.optString("uri", null)
            result.add(
                when {
                    uri == null -> if (i == 0) bin else null
                    uri.startsWith("data:") -> decodeDataUri(uri)
                    else -> null // external .bin: the caller registers it via the sibling-file path
                }
            )
        }
        return result
    }

    private fun decodeDataUri(uri: String): ByteArray? = runCatching {
        val comma = uri.indexOf(',')
        if (comma < 0) return@runCatching null
        val payload = uri.substring(comma + 1)
        if (uri.substring(0, comma).contains(";base64")) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
        }
    }.getOrNull()

    // ---- accessors ----

    private fun bufferFor(buffers: List<ByteArray?>, view: JSONObject): ByteBuffer? {
        val index = view.optInt("buffer", 0)
        val data = buffers.getOrNull(index) ?: return null
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun viewSlice(accessors: JSONArray, bufferViews: JSONArray, buffers: List<ByteArray?>, accessorIndex: Int): Pair<ByteBuffer, JSONObject>? {
        if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
        val accessor = accessors.optJSONObject(accessorIndex) ?: return null
        val viewIndex = accessor.optInt("bufferView", -1)
        if (viewIndex < 0 || viewIndex >= bufferViews.length()) return null
        val view = bufferViews.optJSONObject(viewIndex) ?: return null
        val buffer = bufferFor(buffers, view) ?: return null
        return buffer to accessor
    }

    private val componentSizes = mapOf(5120 to 1, 5121 to 1, 5122 to 2, 5123 to 2, 5125 to 4, 5126 to 4)

    private fun componentCount(type: String): Int = when (type) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        "MAT2" -> 4
        "MAT3" -> 9
        "MAT4" -> 16
        else -> 0
    }

    private fun readVec3(accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, index: Int): FloatArray? =
        readFloats(accessors, views, buffers, index, 3)

    private fun readVec2(accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, index: Int): FloatArray? =
        readFloats(accessors, views, buffers, index, 2)

    private fun readVec4(accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, index: Int): FloatArray? =
        readFloats(accessors, views, buffers, index, 4)

    private fun readFloats(
        accessors: JSONArray,
        views: JSONArray,
        buffers: List<ByteArray?>,
        accessorIndex: Int,
        expected: Int
    ): FloatArray? {
        if (accessorIndex < 0) return null
        val (buffer, accessor) = viewSlice(accessors, views, buffers, accessorIndex) ?: return null
        if (componentCount(accessor.optString("type")) != expected) return null
        val componentType = accessor.optInt("componentType", 5126)
        val componentSize = componentSizes[componentType] ?: return null
        val count = accessor.optInt("count", 0)
        if (count <= 0) return null
        val normalized = accessor.optBoolean("normalized", false)
        val view = views.optJSONObject(accessor.optInt("bufferView", -1)) ?: return null
        val base = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: (componentSize * expected)
        val out = FloatArray(count * expected)
        for (i in 0 until count) {
            var offset = base + i * stride
            for (c in 0 until expected) {
                out[i * expected + c] = readComponent(buffer, offset, componentType, normalized)
                offset += componentSize
            }
        }
        return out
    }

    private fun readIndices(
        accessors: JSONArray,
        views: JSONArray,
        buffers: List<ByteArray?>,
        accessorIndex: Int
    ): IntArray? {
        if (accessorIndex < 0) return null
        val (buffer, accessor) = viewSlice(accessors, views, buffers, accessorIndex) ?: return null
        val componentType = accessor.optInt("componentType", 5125)
        val componentSize = componentSizes[componentType] ?: return null
        val count = accessor.optInt("count", 0)
        if (count <= 0) return null
        val view = views.optJSONObject(accessor.optInt("bufferView", -1)) ?: return null
        val base = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: componentSize
        val out = IntArray(count)
        for (i in 0 until count) {
            val offset = base + i * stride
            out[i] = when (componentType) {
                5121 -> buffer.get(offset).toInt() and 0xFF
                5123 -> buffer.getShort(offset).toInt() and 0xFFFF
                5125 -> buffer.getInt(offset)
                5120 -> buffer.get(offset).toInt()
                5122 -> buffer.getShort(offset).toInt()
                else -> return null
            }
        }
        return out
    }

    private fun readComponent(buffer: ByteBuffer, offset: Int, componentType: Int, normalized: Boolean): Float {
        if (offset + componentSizes[componentType]!! > buffer.capacity()) return 0f
        return when (componentType) {
            5126 -> buffer.getFloat(offset)
            5125 -> (buffer.getInt(offset).toLong() and 0xFFFFFFFFL).toFloat()
            5123 -> {
                val v = buffer.getShort(offset).toInt() and 0xFFFF
                if (normalized) v / 65535f else v.toFloat()
            }
            5121 -> {
                val v = buffer.get(offset).toInt() and 0xFF
                if (normalized) v / 255f else v.toFloat()
            }
            5122 -> {
                val v = buffer.getShort(offset).toInt()
                if (normalized) (v / 32767f).coerceIn(-1f, 1f) else v.toFloat()
            }
            5120 -> {
                val v = buffer.get(offset).toInt()
                if (normalized) (v / 127f).coerceIn(-1f, 1f) else v.toFloat()
            }
            else -> 0f
        }
    }
}

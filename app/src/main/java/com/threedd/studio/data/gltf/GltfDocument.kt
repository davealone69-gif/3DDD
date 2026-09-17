package com.threedd.studio.data.gltf

import java.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Self-contained glTF 2.0 / GLB reader.
 *
 * Exposes the full scene description the renderer needs: the node hierarchy, skins with
 * inverse bind matrices, animation channels, decoded textures and per-primitive geometry
 * (positions, normals, tangents, UVs, vertex colours, bone weights and morph targets).
 * Node transforms are kept as animation state rather than baked, so skeletal animation and
 * rigid node animation both work.
 */
object GltfDocument {

    private const val MAGIC_GLB = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    const val PATH_TRANSLATION = 0
    const val PATH_ROTATION = 1
    const val PATH_SCALE = 2
    const val PATH_WEIGHTS = 3

    const val INTERP_STEP = 0
    const val INTERP_LINEAR = 1
    const val INTERP_CUBICSPLINE = 2

    data class Material(
        val baseColorFactor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
        val metallicFactor: Float = 1f,
        val roughnessFactor: Float = 1f,
        val emissiveFactor: FloatArray = floatArrayOf(0f, 0f, 0f),
        val occlusionStrength: Float = 1f,
        val normalScale: Float = 1f,
        val baseColorTexture: Int = -1,
        val metallicRoughnessTexture: Int = -1,
        val normalTexture: Int = -1,
        val occlusionTexture: Int = -1,
        val emissiveTexture: Int = -1,
        val doubleSided: Boolean = false,
        val alphaMode: Int = 0
    )

    /** How a texture image is sampled; glTF sampler state folded into one object. */
    data class TextureRef(val imageIndex: Int, val wrapS: Int, val wrapT: Int, val minFilter: Int, val magFilter: Int)

    data class Primitive(
        val nodeIndex: Int,
        val positions: FloatArray,
        val normals: FloatArray?,
        val tangents: FloatArray?,
        val uvs: FloatArray?,
        val colors: FloatArray?,
        val boneIndices: IntArray?,
        val boneWeights: FloatArray?,
        val indices: IntArray,
        val morphTargets: List<FloatArray>,
        val morphTargetNames: List<String>,
        val materialIndex: Int
    ) {
        val vertexCount: Int get() = positions.size / 3
        val triangleCount: Int get() = indices.size / 3
    }

    data class Node(
        val index: Int,
        val name: String?,
        val parent: Int,
        val children: IntArray,
        val meshIndex: Int,
        val skinIndex: Int,
        val translation: FloatArray,
        val rotation: FloatArray,
        val scale: FloatArray,
        val matrix: FloatArray?,
        val weights: FloatArray
    )

    data class Skin(val joints: IntArray, val inverseBind: FloatArray)

    data class Channel(
        val nodeIndex: Int,
        val path: Int,
        val interpolation: Int,
        val times: FloatArray,
        val values: FloatArray,
        val componentCount: Int
    ) {
        val keyCount: Int get() = times.size
    }

    data class Animation(val name: String, val channels: List<Channel>, val duration: Float)

    data class Document(
        val primitives: List<Primitive>,
        val nodes: List<Node>,
        val sceneRoots: IntArray,
        val skins: List<Skin>,
        val animations: List<Animation>,
        val images: List<ByteArray?>,
        val textures: List<TextureRef?>,
        val materials: List<Material>,
        val morphTargetNames: List<String>
    ) {
        fun bounds(): FloatArray {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            primitives.forEach { p ->
                var i = 0
                while (i + 2 < p.positions.size) {
                    val x = p.positions[i]; val y = p.positions[i + 1]; val z = p.positions[i + 2]
                    if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
                    if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
                    i += 3
                }
            }
            if (minX > maxX) return floatArrayOf(-1f, -1f, -1f, 1f, 1f, 1f)
            return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        }
    }

    fun parse(bytes: ByteArray): Document? {
        val container = readContainer(bytes) ?: return null
        val gltf = runCatching { JSONObject(container.json) }.getOrNull() ?: return null

        val buffers = resolveBuffers(gltf, container.bin)
        val accessors = gltf.optJSONArray("accessors") ?: JSONArray()
        val bufferViews = gltf.optJSONArray("bufferViews") ?: JSONArray()
        val meshes = gltf.optJSONArray("meshes") ?: return null
        val nodesJson = gltf.optJSONArray("nodes") ?: JSONArray()

        val materials = readMaterials(gltf)
        val images = readImages(gltf, bufferViews, buffers)
        val textures = readTextures(gltf)
        val skins = readSkins(accessors, bufferViews, buffers, gltf)
        val animations = readAnimations(accessors, bufferViews, buffers, gltf)
        val nodes = readNodes(nodesJson)

        val primitives = ArrayList<Primitive>()
        nodes.forEach { node ->
            if (node.meshIndex < 0) return@forEach
            val mesh = meshes.optJSONObject(node.meshIndex) ?: return@forEach
            val targetNames = mesh.optJSONObject("extras")?.optJSONArray("targetNames")
                ?.let { arr -> (0 until arr.length()).map { arr.optString(it, "target$it") } }
                ?: emptyList()
            val prims = mesh.optJSONArray("primitives") ?: return@forEach
            for (i in 0 until prims.length()) {
                val prim = prims.getJSONObject(i)
                val attributes = prim.optJSONObject("attributes") ?: continue
                val positions = readFloats(accessors, bufferViews, buffers, attributes.optInt("POSITION", -1), 3)
                    ?: continue
                val vertexCount = positions.size / 3
                val indices = readIndices(accessors, bufferViews, buffers, prim.optInt("indices", -1))
                    ?: IntArray(vertexCount) { it }

                val targets = ArrayList<FloatArray>()
                prim.optJSONArray("targets")?.let { array ->
                    for (t in 0 until array.length()) {
                        readFloats(accessors, bufferViews, buffers, array.getJSONObject(t).optInt("POSITION", -1), 3)
                            ?.let { targets.add(it) }
                    }
                }

                primitives.add(
                    Primitive(
                        nodeIndex = node.index,
                        positions = positions,
                        normals = readFloats(accessors, bufferViews, buffers, attributes.optInt("NORMAL", -1), 3),
                        tangents = readFloats(accessors, bufferViews, buffers, attributes.optInt("TANGENT", -1), 4),
                        uvs = readFloats(accessors, bufferViews, buffers, attributes.optInt("TEXCOORD_0", -1), 2),
                        colors = readFloats(accessors, bufferViews, buffers, attributes.optInt("COLOR_0", -1), 4),
                        boneIndices = readInts4(accessors, bufferViews, buffers, attributes.optInt("JOINTS_0", -1)),
                        boneWeights = readFloats(accessors, bufferViews, buffers, attributes.optInt("WEIGHTS_0", -1), 4),
                        indices = indices,
                        morphTargets = targets,
                        morphTargetNames = targetNames,
                        materialIndex = if (prim.has("material")) prim.optInt("material", -1) else -1
                    )
                )
            }
        }

        if (primitives.isEmpty()) return null
        val roots = resolveSceneRoots(gltf, nodes)
        return Document(
            primitives = primitives,
            nodes = nodes,
            sceneRoots = roots,
            skins = skins,
            animations = animations,
            images = images,
            textures = textures,
            materials = materials,
            morphTargetNames = primitives.firstOrNull()?.morphTargetNames ?: emptyList()
        )
    }

    // ---- scene graph ----

    private fun readNodes(array: JSONArray): List<Node> {
        val parentOf = IntArray(array.length()) { -1 }
        for (i in 0 until array.length()) {
            val children = array.getJSONObject(i).optJSONArray("children") ?: continue
            for (c in 0 until children.length()) {
                val child = children.optInt(c, -1)
                if (child in parentOf.indices) parentOf[child] = i
            }
        }
        return (0 until array.length()).map { i ->
            val n = array.getJSONObject(i)
            val children = n.optJSONArray("children")
            val matrix = n.optJSONArray("matrix")
            Node(
                index = i,
                name = n.optString("name", null),
                parent = parentOf[i],
                children = if (children != null) IntArray(children.length()) { children.optInt(it, -1) } else IntArray(0),
                meshIndex = if (n.has("mesh")) n.optInt("mesh", -1) else -1,
                skinIndex = if (n.has("skin")) n.optInt("skin", -1) else -1,
                translation = float3(n.optJSONArray("translation"), 0f, 0f, 0f),
                rotation = float4(n.optJSONArray("rotation"), 0f, 0f, 0f, 1f),
                scale = float3(n.optJSONArray("scale"), 1f, 1f, 1f),
                matrix = if (matrix != null && matrix.length() == 16) {
                    FloatArray(16) { matrix.optDouble(it, 0.0).toFloat() }
                } else null,
                weights = n.optJSONArray("weights")?.let { w -> FloatArray(w.length()) { w.optDouble(it, 0.0).toFloat() } } ?: FloatArray(0)
            )
        }
    }

    private fun resolveSceneRoots(gltf: JSONObject, nodes: List<Node>): IntArray {
        val scenes = gltf.optJSONArray("scenes")
        val scene = scenes?.optJSONObject(gltf.optInt("scene", 0))
        val declared = scene?.optJSONArray("nodes")
        if (declared != null && declared.length() > 0) {
            return IntArray(declared.length()) { declared.optInt(it, -1) }.filter { it in nodes.indices }.toIntArray()
        }
        return nodes.filter { it.parent < 0 }.map { it.index }.toIntArray()
    }

    private fun readSkins(
        accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, gltf: JSONObject
    ): List<Skin> {
        val array = gltf.optJSONArray("skins") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val s = array.getJSONObject(i)
            val joints = s.optJSONArray("joints") ?: return@mapNotNull null
            val jointList = IntArray(joints.length()) { joints.optInt(it, -1) }
            val ibm = readFloats(accessors, views, buffers, s.optInt("inverseBindMatrices", -1), 16)
            val bind = ibm ?: FloatArray(jointList.size * 16).also { m ->
                for (j in jointList.indices) {
                    m[j * 16] = 1f; m[j * 16 + 5] = 1f; m[j * 16 + 10] = 1f; m[j * 16 + 15] = 1f
                }
            }
            Skin(jointList, bind)
        }
    }

    private fun readAnimations(
        accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, gltf: JSONObject
    ): List<Animation> {
        val array = gltf.optJSONArray("animations") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val a = array.getJSONObject(i)
            val samplers = a.optJSONArray("samplers") ?: return@mapNotNull null
            val channelsJson = a.optJSONArray("channels") ?: return@mapNotNull null
            val channels = ArrayList<Channel>()
            for (c in 0 until channelsJson.length()) {
                val ch = channelsJson.getJSONObject(c)
                val target = ch.optJSONObject("target") ?: continue
                if (!target.has("node")) continue
                val path = when (target.optString("path")) {
                    "translation" -> PATH_TRANSLATION
                    "rotation" -> PATH_ROTATION
                    "scale" -> PATH_SCALE
                    "weights" -> PATH_WEIGHTS
                    else -> continue
                }
                val sampler = samplers.optJSONObject(ch.optInt("sampler", -1)) ?: continue
                val times = readFloats(accessors, views, buffers, sampler.optInt("input", -1), 1) ?: continue
                if (times.isEmpty()) continue
                val interpolation = when (sampler.optString("interpolation", "LINEAR")) {
                    "STEP" -> INTERP_STEP
                    "CUBICSPLINE" -> INTERP_CUBICSPLINE
                    else -> INTERP_LINEAR
                }
                val components = when (path) {
                    PATH_ROTATION -> 4
                    PATH_WEIGHTS -> {
                        val outIndex = sampler.optInt("output", -1)
                        val count = accessorCount(accessors, outIndex)
                        if (count <= 0) continue
                        count / times.size
                    }
                    else -> 3
                }
                val values = readFloats(accessors, views, buffers, sampler.optInt("output", -1), components)
                    ?: continue
                channels.add(Channel(target.optInt("node", -1), path, interpolation, times, values, components))
            }
            if (channels.isEmpty()) return@mapNotNull null
            val duration = channels.maxOf { it.times.last() }
            Animation(a.optString("name", "Clip $i"), channels, duration)
        }
    }

    private fun accessorCount(accessors: JSONArray, index: Int): Int {
        if (index < 0 || index >= accessors.length()) return 0
        return accessors.getJSONObject(index).optInt("count", 0)
    }

    // ---- materials / images / textures ----

    private fun readMaterials(gltf: JSONObject): List<Material> {
        val array = gltf.optJSONArray("materials") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val m = array.getJSONObject(i)
            val pbr = m.optJSONObject("pbrMetallicRoughness")
            val normal = m.optJSONObject("normalTexture")
            val occlusion = m.optJSONObject("occlusionTexture")
            val emissive = m.optJSONObject("emissiveTexture")
            Material(
                baseColorFactor = float4(pbr?.optJSONArray("baseColorFactor"), 1f, 1f, 1f, 1f),
                metallicFactor = pbr?.optDouble("metallicFactor", 1.0)?.toFloat() ?: 1f,
                roughnessFactor = pbr?.optDouble("roughnessFactor", 1.0)?.toFloat() ?: 1f,
                emissiveFactor = float3(m.optJSONArray("emissiveFactor"), 0f, 0f, 0f),
                occlusionStrength = occlusion?.optDouble("strength", 1.0)?.toFloat() ?: 1f,
                normalScale = normal?.optDouble("scale", 1.0)?.toFloat() ?: 1f,
                baseColorTexture = pbr?.optJSONObject("baseColorTexture")?.optInt("index", -1) ?: -1,
                metallicRoughnessTexture = pbr?.optJSONObject("metallicRoughnessTexture")?.optInt("index", -1) ?: -1,
                normalTexture = normal?.optInt("index", -1) ?: -1,
                occlusionTexture = occlusion?.optInt("index", -1) ?: -1,
                emissiveTexture = emissive?.optInt("index", -1) ?: -1,
                doubleSided = m.optBoolean("doubleSided", false),
                alphaMode = when (m.optString("alphaMode", "OPAQUE")) {
                    "MASK" -> 1
                    "BLEND" -> 2
                    else -> 0
                }
            )
        }
    }

    private fun readTextures(gltf: JSONObject): List<TextureRef?> {
        val array = gltf.optJSONArray("textures") ?: return emptyList()
        val samplers = gltf.optJSONArray("samplers")
        return (0 until array.length()).map { i ->
            val t = array.getJSONObject(i)
            if (!t.has("source")) return@map null
            val s = if (t.has("sampler") && samplers != null) {
                samplers.optJSONObject(t.optInt("sampler", -1))
            } else null
            TextureRef(
                imageIndex = t.optInt("source", -1),
                wrapS = s?.optInt("wrapS", 10497) ?: 10497,
                wrapT = s?.optInt("wrapT", 10497) ?: 10497,
                minFilter = s?.optInt("minFilter", 9987) ?: 9987,
                magFilter = s?.optInt("magFilter", 9729) ?: 9729
            )
        }
    }

    private fun readImages(gltf: JSONObject, views: JSONArray, buffers: List<ByteArray?>): List<ByteArray?> {
        val array = gltf.optJSONArray("images") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val image = array.getJSONObject(i)
            val bytes: ByteArray? = when {
                image.has("bufferView") -> {
                    val viewIndex = image.optInt("bufferView", -1)
                    val view = views.optJSONObject(viewIndex)
                    val buffer = view?.let { buffers.getOrNull(it.optInt("buffer", 0)) }
                    if (view != null && buffer != null) {
                        val start = view.optInt("byteOffset", 0)
                        val length = view.optInt("byteLength", 0)
                        if (start >= 0 && length > 0 && start + length <= buffer.size) {
                            buffer.copyOfRange(start, start + length)
                        } else null
                    } else null
                }
                image.has("uri") -> {
                    val uri = image.optString("uri", "")
                    if (uri.startsWith("data:")) decodeDataUri(uri) else null
                }
                else -> null
            }
            bytes
        }
    }

    // ---- buffers / accessors ----

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
        return (0 until declared.length()).map { i ->
            val uri = declared.optJSONObject(i)?.optString("uri", null)
            when {
                uri == null -> if (i == 0) bin else null
                uri.startsWith("data:") -> decodeDataUri(uri)
                else -> null
            }
        }
    }

    private fun decodeDataUri(uri: String): ByteArray? = runCatching {
        val comma = uri.indexOf(',')
        if (comma < 0) return@runCatching null
        val payload = uri.substring(comma + 1)
        if (uri.substring(0, comma).contains(";base64")) {
            Base64.getDecoder().decode(payload)
        } else {
            java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
        }
    }.getOrNull()

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

    private fun readFloats(
        accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, accessorIndex: Int, expected: Int
    ): FloatArray? {
        if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
        val accessor = accessors.optJSONObject(accessorIndex) ?: return null
        val componentTotal = componentCount(accessor.optString("type"))
        if (componentTotal == 0) return null
        val components = if (expected > 0) minOf(expected, componentTotal) else componentTotal
        val componentType = accessor.optInt("componentType", 5126)
        val componentSize = componentSizes[componentType] ?: return null
        val count = accessor.optInt("count", 0)
        if (count <= 0) return null
        val viewIndex = accessor.optInt("bufferView", -1)
        if (viewIndex < 0) return null
        val view = views.optJSONObject(viewIndex) ?: return null
        val buffer = bufferFor(buffers, view) ?: return null
        val normalized = accessor.optBoolean("normalized", false)
        val base = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: (componentSize * componentTotal)
        val out = FloatArray(count * components)
        for (i in 0 until count) {
            var offset = base + i * stride
            for (c in 0 until components) {
                out[i * components + c] = readComponent(buffer, offset, componentType, normalized)
                offset += componentSize
            }
        }
        return out
    }

    private fun readInts4(
        accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, accessorIndex: Int
    ): IntArray? {
        if (accessorIndex < 0 || accessorIndex >= accessors.length()) return null
        val accessor = accessors.optJSONObject(accessorIndex) ?: return null
        val componentTotal = componentCount(accessor.optString("type"))
        if (componentTotal == 0) return null
        val components = minOf(4, componentTotal)
        val componentType = accessor.optInt("componentType", 5121)
        val componentSize = componentSizes[componentType] ?: return null
        val count = accessor.optInt("count", 0)
        if (count <= 0) return null
        val viewIndex = accessor.optInt("bufferView", -1)
        if (viewIndex < 0) return null
        val view = views.optJSONObject(viewIndex) ?: return null
        val buffer = bufferFor(buffers, view) ?: return null
        val base = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: (componentSize * componentTotal)
        val out = IntArray(count * components)
        for (i in 0 until count) {
            var offset = base + i * stride
            for (c in 0 until components) {
                out[i * components + c] = when (componentType) {
                    5121 -> buffer.get(offset).toInt() and 0xFF
                    5123 -> buffer.getShort(offset).toInt() and 0xFFFF
                    5120 -> buffer.get(offset).toInt()
                    5122 -> buffer.getShort(offset).toInt()
                    else -> 0
                }
                offset += componentSize
            }
        }
        return out
    }

    private fun readIndices(
        accessors: JSONArray, views: JSONArray, buffers: List<ByteArray?>, accessorIndex: Int
    ): IntArray? {
        val values = readInts4(accessors, views, buffers, accessorIndex) ?: return null
        // SCALAR accessors were read as a single component; readInts4 pads to 1 when needed.
        return if (accessorIndex in 0 until accessors.length()) {
            val type = accessors.optJSONObject(accessorIndex)?.optString("type")
            if (type == "SCALAR") values else values
        } else values
    }

    private fun bufferFor(buffers: List<ByteArray?>, view: JSONObject): ByteBuffer? {
        val data = buffers.getOrNull(view.optInt("buffer", 0)) ?: return null
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun readComponent(buffer: ByteBuffer, offset: Int, componentType: Int, normalized: Boolean): Float {
        if (offset + (componentSizes[componentType] ?: return 0f) > buffer.capacity()) return 0f
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

    private fun float3(array: JSONArray?, dx: Float, dy: Float, dz: Float) = floatArrayOf(
        array?.optDouble(0, dx.toDouble())?.toFloat() ?: dx,
        array?.optDouble(1, dy.toDouble())?.toFloat() ?: dy,
        array?.optDouble(2, dz.toDouble())?.toFloat() ?: dz
    )

    private fun float4(array: JSONArray?, dx: Float, dy: Float, dz: Float, dw: Float) = floatArrayOf(
        array?.optDouble(0, dx.toDouble())?.toFloat() ?: dx,
        array?.optDouble(1, dy.toDouble())?.toFloat() ?: dy,
        array?.optDouble(2, dz.toDouble())?.toFloat() ?: dz,
        array?.optDouble(3, dw.toDouble())?.toFloat() ?: dw
    )
}

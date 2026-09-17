package com.threedd.studio.scan

import com.threedd.studio.data.avatar.TriangleMesh
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes a textured triangle mesh out as a self-contained GLB (embedded PNG, embedded
 * binary buffer). Used to persist reconstructed scan avatars.
 */
object GltfMeshWriter {

    private const val MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    fun write(mesh: TriangleMesh, texturePng: ByteArray?, name: String, destination: File) {
        val positions = floatBytes(mesh.positions)
        val normals = floatBytes(mesh.normals)
        val uvs = floatBytes(mesh.uvs)
        val indices = intBytes(mesh.indices)

        val buffer = ByteArrayOutputStream()
        val bufferViews = JSONArray()
        val accessors = JSONArray()

        fun addView(data: ByteArray, target: Int?): Int {
            while (buffer.size() % 4 != 0) buffer.write(0)
            val offset = buffer.size()
            buffer.write(data)
            val view = JSONObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", data.size)
            if (target != null) view.put("target", target)
            bufferViews.put(view)
            return bufferViews.length() - 1
        }

        fun addAccessor(view: Int, componentType: Int, type: String, count: Int, min: FloatArray? = null, max: FloatArray? = null): Int {
            val acc = JSONObject()
                .put("bufferView", view).put("componentType", componentType)
                .put("count", count).put("type", type)
            if (min != null && max != null) {
                acc.put("min", JSONArray(min.toList()))
                acc.put("max", JSONArray(max.toList()))
            }
            accessors.put(acc)
            return accessors.length() - 1
        }

        val minPos = FloatArray(3) { Float.MAX_VALUE }
        val maxPos = FloatArray(3) { -Float.MAX_VALUE }
        for (i in 0 until mesh.vertexCount) {
            for (c in 0 until 3) {
                val v = mesh.positions[i * 3 + c]
                if (v < minPos[c]) minPos[c] = v
                if (v > maxPos[c]) maxPos[c] = v
            }
        }

        val posView = addView(positions, 34962)
        val accPos = addAccessor(posView, 5126, "VEC3", mesh.vertexCount, minPos, maxPos)
        val nrmView = addView(normals, 34962)
        addAccessor(nrmView, 5126, "VEC3", mesh.vertexCount)
        val uvView = addView(uvs, 34962)
        addAccessor(uvView, 5126, "VEC2", mesh.vertexCount)
        val idxView = addView(indices, 34963)
        addAccessor(idxView, 5125, "SCALAR", mesh.indices.size)

        val attributes = JSONObject()
            .put("POSITION", accPos)
            .put("NORMAL", accPos + 1)
            .put("TEXCOORD_0", accPos + 2)

        val material = JSONObject().put(
            "pbrMetallicRoughness",
            JSONObject()
                .put("baseColorFactor", JSONArray(listOf(1.0, 1.0, 1.0, 1.0)))
                .put("metallicFactor", 0.0)
                .put("roughnessFactor", 0.62)
        )
        val materials = JSONArray()

        if (texturePng != null && texturePng.isNotEmpty()) {
            while (buffer.size() % 4 != 0) buffer.write(0)
            val imageOffset = buffer.size()
            buffer.write(texturePng)
            bufferViews.put(
                JSONObject().put("buffer", 0).put("byteOffset", imageOffset).put("byteLength", texturePng.size)
            )
            val imageView = bufferViews.length() - 1
            materials.put(
                material.put(
                    "pbrMetallicRoughness",
                    material.getJSONObject("pbrMetallicRoughness")
                        .put("baseColorTexture", JSONObject().put("index", 0))
                )
            )
            val gltfExtras = JSONObject()
                .put("images", JSONArray().put(JSONObject().put("bufferView", imageView).put("mimeType", "image/png")))
                .put("samplers", JSONArray().put(JSONObject().put("magFilter", 9729).put("minFilter", 9987).put("wrapS", 33071).put("wrapT", 33071)))
                .put("textures", JSONArray().put(JSONObject().put("sampler", 0).put("source", 0)))
            writeGlb(mesh, buffer.toByteArray(), bufferViews, accessors, attributes, idxView, materials, gltfExtras, name, destination)
        } else {
            materials.put(material)
            writeGlb(mesh, buffer.toByteArray(), bufferViews, accessors, attributes, idxView, materials, JSONObject(), name, destination)
        }
    }

    private fun writeGlb(
        mesh: TriangleMesh,
        bin: ByteArray,
        bufferViews: JSONArray,
        accessors: JSONArray,
        attributes: JSONObject,
        indexView: Int,
        materials: JSONArray,
        extras: JSONObject,
        name: String,
        destination: File
    ) {
        val primitive = JSONObject()
            .put("attributes", attributes)
            .put("indices", accessors.length() - 1)
            .put("material", 0)
            .put("mode", 4)

        val gltf = JSONObject()
            .put("asset", JSONObject().put("version", "2.0").put("generator", "3DoubleD scan"))
            .put("scene", 0)
            .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
            .put("nodes", JSONArray().put(JSONObject().put("mesh", 0).put("name", name)))
            .put("meshes", JSONArray().put(JSONObject().put("name", name).put("primitives", JSONArray().put(primitive))))
            .put("materials", materials)
            .put("accessors", accessors)
            .put("bufferViews", bufferViews)
            .put("buffers", JSONArray().put(JSONObject().put("byteLength", bin.size)))
        extras.keys().forEach { key -> gltf.put(key, extras.get(key)) }
        indexView.let { }

        var json = gltf.toString().toByteArray(Charsets.UTF_8)
        val jsonOut = ByteArrayOutputStream()
        jsonOut.write(json)
        while (jsonOut.size() % 4 != 0) jsonOut.write(0x20)
        json = jsonOut.toByteArray()

        val binPadded = ByteArrayOutputStream().apply {
            write(bin)
            while (size() % 4 != 0) write(0)
        }.toByteArray()

        val total = 12 + 8 + json.size + 8 + binPadded.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(MAGIC); out.putInt(2); out.putInt(total)
        out.putInt(json.size); out.putInt(CHUNK_JSON); out.put(json)
        out.putInt(binPadded.size); out.putInt(CHUNK_BIN); out.put(binPadded)
        destination.outputStream().use { it.write(out.array()) }
    }

    private fun floatBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun intBytes(values: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putInt(it) }
        return buffer.array()
    }
}

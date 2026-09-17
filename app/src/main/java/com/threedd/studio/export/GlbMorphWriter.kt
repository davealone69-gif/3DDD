package com.threedd.studio.export

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bakes the current morph-target weights into a copy of a source GLB and writes a new,
 * self-contained glTF binary. Positions are displaced in the binary chunk in place;
 * accessor layout is unchanged because vertex counts do not change.
 */
object GlbMorphWriter {

    private const val MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942
    private const val FLOAT = 5126

    fun bake(source: File, weights: Map<String, Float>, destination: File): Boolean {
        val bytes = runCatching { source.readBytes() }.getOrNull() ?: return false
        if (bytes.size < 20) return false
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (header.int != MAGIC) return false
        header.int // version
        header.int // total length

        val jsonLength = header.int
        val jsonType = header.int
        if (jsonType != CHUNK_JSON) return false
        val jsonText = String(bytes, 20, jsonLength, Charsets.UTF_8).trim()
        val gltf = runCatching { JSONObject(jsonText) }.getOrNull() ?: return false

        var binOffset = 20 + jsonLength
        var binBuffer: ByteBuffer? = null
        if (binOffset + 8 <= bytes.size) {
            val binHeader = ByteBuffer.wrap(bytes, binOffset, 8).order(ByteOrder.LITTLE_ENDIAN)
            val binLength = binHeader.int
            val binType = binHeader.int
            if (binType == CHUNK_BIN) {
                val data = bytes.copyOfRange(binOffset + 8, minOf(binOffset + 8 + binLength, bytes.size))
                binBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            }
        }
        val bin = binBuffer ?: return false

        val accessors = gltf.optJSONArray("accessors") ?: return false
        val bufferViews = gltf.optJSONArray("bufferViews") ?: return false
        val meshes = gltf.optJSONArray("meshes") ?: return false

        var bakedAny = false
        for (m in 0 until meshes.length()) {
            val mesh = meshes.getJSONObject(m)
            val targetNames = mesh.optJSONObject("extras")?.optJSONArray("targetNames")
            val primitives = mesh.optJSONArray("primitives") ?: continue
            for (p in 0 until primitives.length()) {
                val prim = primitives.getJSONObject(p)
                val targets = prim.optJSONArray("targets") ?: continue
                val baseAcc = prim.optJSONObject("attributes")?.optInt("POSITION", -1) ?: -1
                if (baseAcc < 0) continue
                val base = floatTriples(accessors, bufferViews, bin, baseAcc) ?: continue
                val displaced = Array(base.size) { FloatArray(3) }
                for (i in base.indices) {
                    displaced[i][0] = base[i][0]; displaced[i][1] = base[i][1]; displaced[i][2] = base[i][2]
                }
                var applied = false
                for (t in 0 until targets.length()) {
                    val weight = targetWeight(targetNames, t, weights)
                    if (weight == 0f) continue
                    val targetAcc = targets.getJSONObject(t).optInt("POSITION", -1)
                    if (targetAcc < 0) continue
                    val deltas = floatTriples(accessors, bufferViews, bin, targetAcc) ?: continue
                    for (i in base.indices) {
                        if (i >= deltas.size) break
                        displaced[i][0] += deltas[i][0] * weight
                        displaced[i][1] += deltas[i][1] * weight
                        displaced[i][2] += deltas[i][2] * weight
                    }
                    applied = true
                }
                if (!applied) continue
                val acc = accessors.getJSONObject(baseAcc)
                val view = bufferViews.getJSONObject(acc.getInt("bufferView"))
                val offset = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
                if (offset + displaced.size * 12 > bin.capacity()) continue
                for (i in displaced.indices) {
                    bin.putFloat(offset + i * 12, displaced[i][0])
                    bin.putFloat(offset + i * 12 + 4, displaced[i][1])
                    bin.putFloat(offset + i * 12 + 8, displaced[i][2])
                }
                bakedAny = true
            }
        }

        // Reset node weights, since the displacement is now baked into the geometry.
        val nodes = gltf.optJSONArray("nodes")
        if (nodes != null) {
            for (n in 0 until nodes.length()) {
                val node = nodes.getJSONObject(n)
                if (node.has("weights")) {
                    val count = node.getJSONArray("weights").length()
                    node.put("weights", JSONArray(Array(count) { 0.0 }))
                }
            }
        }

        val binBytes = ByteArray(bin.capacity())
        bin.rewind()
        bin.get(binBytes)
        writeGlb(gltf, binBytes, destination)
        return bakedAny
    }

    private fun targetWeight(targetNames: JSONArray?, targetIndex: Int, weights: Map<String, Float>): Float {
        val name = targetNames?.optString(targetIndex, "") ?: ""
        return if (name.isEmpty()) 0f else weights[name] ?: 0f
    }

    private fun floatTriples(
        accessors: JSONArray,
        bufferViews: JSONArray,
        bin: ByteBuffer,
        accessorIndex: Int
    ): Array<FloatArray>? {
        if (accessorIndex >= accessors.length()) return null
        val acc = accessors.getJSONObject(accessorIndex)
        if (acc.optInt("componentType") != FLOAT) return null
        if (!acc.optString("type").equals("VEC3", true)) return null
        val count = acc.optInt("count", 0)
        if (count <= 0) return null
        val view = bufferViews.getJSONObject(acc.getInt("bufferView"))
        val viewOffset = view.optInt("byteOffset", 0)
        val accOffset = acc.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: 12
        val start = viewOffset + accOffset
        if (start + (count - 1) * stride + 12 > bin.capacity()) return null
        val out = Array(count) { FloatArray(3) }
        for (i in 0 until count) {
            val o = start + i * stride
            out[i][0] = bin.getFloat(o)
            out[i][1] = bin.getFloat(o + 4)
            out[i][2] = bin.getFloat(o + 8)
        }
        return out
    }

    private fun writeGlb(gltf: JSONObject, bin: ByteArray, destination: File) {
        var binBytes = bin
        while (binBytes.size % 4 != 0) binBytes += 0
        gltf.optJSONArray("buffers")?.let { buffers ->
            if (buffers.length() > 0) buffers.getJSONObject(0).put("byteLength", binBytes.size)
        }
        var json = gltf.toString().toByteArray(Charsets.UTF_8)
        while (json.size % 4 != 0) json += ' '.code.toByte()

        val total = 12 + 8 + json.size + 8 + binBytes.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(MAGIC); out.putInt(2); out.putInt(total)
        out.putInt(json.size); out.putInt(CHUNK_JSON); out.put(json)
        out.putInt(binBytes.size); out.putInt(CHUNK_BIN); out.put(binBytes)
        destination.outputStream().use { it.write(out.array()) }
    }
}

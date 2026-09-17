package com.threedd.studio.data.local

import org.json.JSONObject

/** Minimal, dependency-free codec for the Float map that Room cannot store directly. */
object JsonCodec {

    fun encodeFloats(map: Map<String, Float>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        return obj.toString()
    }

    fun decodeFloats(json: String): Map<String, Float> {
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, Float>(obj.length())
        obj.keys().forEach { key -> out[key] = obj.optDouble(key, 0.0).toFloat() }
        return out
    }
}

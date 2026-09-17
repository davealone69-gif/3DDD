package com.threedd.studio.render

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Geometry helpers shared by the model loader and the appearance part layer.
 *
 * Filament has no NORMAL vertex attribute - the tangent frame is a single quaternion in the
 * TANGENTS slot - so any generated mesh must be encoded the same way imported glTF is.
 */
object GeometryTools {

    /** Area-weighted vertex normals, used when a mesh does not carry them. */
    fun computeNormals(positions: FloatArray, indices: IntArray): FloatArray {
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
        normalise(out)
        return out
    }

    private fun normalise(values: FloatArray) {
        var v = 0
        while (v + 2 < values.size) {
            val len = sqrt(values[v] * values[v] + values[v + 1] * values[v + 1] + values[v + 2] * values[v + 2])
            if (len > 1e-6f) {
                values[v] /= len; values[v + 1] /= len; values[v + 2] /= len
            } else {
                values[v] = 0f; values[v + 1] = 0f; values[v + 2] = 1f
            }
            v += 3
        }
    }

    /**
     * Encodes vertex normals as the quaternion Filament decodes in common_math.glsl:
     *   n = (0,0,1) + (2,-2,-2)qx(qz,qw,qx) + (2,2,-2)qy(qw,qz,qy)
     * which is the third column of the rotation matrix built from the quaternion. When UVs are
     * available a mikktspace-style tangent is derived so normal maps are oriented correctly.
     */
    fun packTangents(
        normals: FloatArray,
        positions: FloatArray,
        uvs: FloatArray?,
        indices: IntArray,
        gltfTangents: FloatArray?
    ): FloatArray {
        val count = normals.size / 3
        val out = FloatArray(count * 4)
        val derived = if (uvs != null && uvs.size >= count * 2) deriveTangents(positions, uvs, indices) else null

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
    fun deriveTangents(positions: FloatArray, uvs: FloatArray, indices: IntArray): FloatArray {
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
}

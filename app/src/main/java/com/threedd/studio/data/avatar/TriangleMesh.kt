package com.threedd.studio.data.avatar

/** A plain indexed triangle mesh, shared by the scan and photo avatar builders. */
data class TriangleMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

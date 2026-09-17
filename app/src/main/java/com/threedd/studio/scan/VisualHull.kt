package com.threedd.studio.scan

import kotlin.math.cos
import kotlin.math.sin

/**
 * Shape-from-silhouette reconstruction.
 *
 * A voxel volume is carved by every captured silhouette under an orthographic, known-azimuth
 * camera model. The surviving voxels are then surfaced into a triangle mesh whose UVs come
 * from a front-projection so a captured frame can be used directly as the texture.
 */
class VisualHull(
    private val resolution: Int = 56,
    private val halfWidth: Float = 0.45f,
    private val halfHeight: Float = 0.9f
) {

    private val voxels = BooleanArray(resolution * resolution * resolution)

    data class View(val mask: BooleanArray, val width: Int, val height: Int, val azimuthRadians: Float)

    data class Mesh(
        val positions: FloatArray,
        val normals: FloatArray,
        val uvs: FloatArray,
        val indices: IntArray
    ) {
        val vertexCount get() = positions.size / 3
        val triangleCount get() = indices.size / 3
    }

    fun carve(views: List<View>) {
        voxels.fill(true)
        views.forEach { view -> carveWith(view) }
    }

    private fun carveWith(view: View) {
        val cosA = cos(view.azimuthRadians)
        val sinA = sin(view.azimuthRadians)
        for (iz in 0 until resolution) {
            for (iy in 0 until resolution) {
                for (ix in 0 until resolution) {
                    val index = (iz * resolution + iy) * resolution + ix
                    if (!voxels[index]) continue
                    val p = voxelCenter(ix, iy, iz)
                    // Rotate the point into the camera's horizontal frame.
                    val u = p[0] * cosA + p[2] * sinA
                    val v = p[1]
                    val px = (((u + halfWidth) / (2 * halfWidth)) * view.width).toInt()
                    val py = (((halfHeight - v) / (2 * halfHeight)) * view.height).toInt()
                    if (px !in 0 until view.width || py !in 0 until view.height) {
                        voxels[index] = false
                        continue
                    }
                    if (!view.mask[py * view.width + px]) voxels[index] = false
                }
            }
        }
    }

    private fun voxelCenter(ix: Int, iy: Int, iz: Int): FloatArray {
        val sx = -halfWidth + (ix + 0.5f) / resolution * (2 * halfWidth)
        val sy = -halfHeight + (iy + 0.5f) / resolution * (2 * halfHeight)
        val sz = -halfWidth + (iz + 0.5f) / resolution * (2 * halfWidth)
        return floatArrayOf(sx, sy, sz)
    }

    private fun occupied(ix: Int, iy: Int, iz: Int): Boolean {
        if (ix < 0 || iy < 0 || iz < 0 || ix >= resolution || iy >= resolution || iz >= resolution) return false
        return voxels[(iz * resolution + iy) * resolution + ix]
    }

    fun buildMesh(): Mesh {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        val indices = ArrayList<Int>()

        val sizeX = 2 * halfWidth / resolution
        val sizeY = 2 * halfHeight / resolution
        val sizeZ = sizeX

        fun uFor(x: Float) = (x + halfWidth) / (2 * halfWidth)
        fun vFor(y: Float) = (halfHeight - y) / (2 * halfHeight)

        val faces = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
        )

        for (iz in 0 until resolution) {
            for (iy in 0 until resolution) {
                for (ix in 0 until resolution) {
                    if (!occupied(ix, iy, iz)) continue
                    val cx = -halfWidth + (ix + 0.5f) * sizeX
                    val cy = -halfHeight + (iy + 0.5f) * sizeY
                    val cz = -halfWidth + (iz + 0.5f) * sizeZ
                    faces.forEach { dir ->
                        if (occupied(ix + dir[0], iy + dir[1], iz + dir[2])) return@forEach
                        val nx = dir[0].toFloat(); val ny = dir[1].toFloat(); val nz = dir[2].toFloat()
                        val hx = sizeX / 2; val hy = sizeY / 2; val hz = sizeZ / 2
                        val base = positions.size / 3
                        val quad = if (dir[0] != 0) arrayOf(
                            floatArrayOf(cx + hx * nx, cy - hy, cz - hz),
                            floatArrayOf(cx + hx * nx, cy - hy, cz + hz),
                            floatArrayOf(cx + hx * nx, cy + hy, cz + hz),
                            floatArrayOf(cx + hx * nx, cy + hy, cz - hz)
                        ) else if (dir[1] != 0) arrayOf(
                            floatArrayOf(cx - hx, cy + hy * ny, cz - hz),
                            floatArrayOf(cx + hx, cy + hy * ny, cz - hz),
                            floatArrayOf(cx + hx, cy + hy * ny, cz + hz),
                            floatArrayOf(cx - hx, cy + hy * ny, cz + hz)
                        ) else arrayOf(
                            floatArrayOf(cx - hx, cy - hy, cz + hz * nz),
                            floatArrayOf(cx + hx, cy - hy, cz + hz * nz),
                            floatArrayOf(cx + hx, cy + hy, cz + hz * nz),
                            floatArrayOf(cx - hx, cy + hy, cz + hz * nz)
                        )
                        quad.forEach { p ->
                            positions.add(p[0]); positions.add(p[1]); positions.add(p[2])
                            normals.add(nx); normals.add(ny); normals.add(nz)
                            uvs.add(uFor(p[0])); uvs.add(vFor(p[1]))
                        }
                        val flip = dir[0] + dir[1] + dir[2] < 0
                        if (flip) {
                            indices.add(base + 0); indices.add(base + 2); indices.add(base + 1)
                            indices.add(base + 0); indices.add(base + 3); indices.add(base + 2)
                        } else {
                            indices.add(base + 0); indices.add(base + 1); indices.add(base + 2)
                            indices.add(base + 0); indices.add(base + 2); indices.add(base + 3)
                        }
                    }
                }
            }
        }

        return Mesh(
            positions.toFloatArray(),
            normals.toFloatArray(),
            uvs.toFloatArray(),
            indices.toIntArray()
        )
    }

    fun occupiedCount(): Int = voxels.count { it }
}

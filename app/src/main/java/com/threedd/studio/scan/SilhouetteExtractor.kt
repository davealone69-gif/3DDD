package com.threedd.studio.scan

import android.graphics.Bitmap

/**
 * Turns a captured frame into a foreground mask.
 *
 * The background colour is estimated from the frame border, a distance histogram is split
 * with Otsu's method, and only the largest connected component is kept so stray highlights
 * do not leak into the reconstruction volume.
 */
object SilhouetteExtractor {

    const val MAX_DIM = 256

    data class Result(val mask: BooleanArray, val width: Int, val height: Int, val coverage: Float)

    fun extract(source: Bitmap): Result {
        val scale = MAX_DIM.toFloat() / maxOf(source.width, source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(16)
        val h = (source.height * scale).toInt().coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small != source) small.recycle()

        val background = estimateBackground(pixels, w, h)
        val distances = FloatArray(w * h)
        var maxDistance = 1f
        for (i in pixels.indices) {
            val d = distance(pixels[i], background)
            distances[i] = d
            if (d > maxDistance) maxDistance = d
        }

        val histogram = IntArray(256)
        for (d in distances) histogram[((d / maxDistance) * 255).toInt().coerceIn(0, 255)]++
        val threshold = (otsu(histogram) / 255f) * maxDistance

        val raw = BooleanArray(w * h) { distances[it] > threshold }
        val kept = largestComponent(raw, w, h)
        var foreground = 0
        for (b in kept) if (b) foreground++
        return Result(kept, w, h, foreground.toFloat() / (w * h))
    }

    private fun estimateBackground(pixels: IntArray, w: Int, h: Int): Int {
        val samples = ArrayList<Int>()
        val step = 4
        for (x in 0 until w step step) {
            samples.add(pixels[x]); samples.add(pixels[(h - 1) * w + x])
        }
        for (y in 0 until h step step) {
            samples.add(pixels[y * w]); samples.add(pixels[y * w + w - 1])
        }
        val r = samples.map { it shr 16 and 0xFF }.sorted()[samples.size / 2]
        val g = samples.map { it shr 8 and 0xFF }.sorted()[samples.size / 2]
        val b = samples.map { it and 0xFF }.sorted()[samples.size / 2]
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun distance(pixel: Int, reference: Int): Float {
        val dr = ((pixel shr 16 and 0xFF) - (reference shr 16 and 0xFF)).toFloat()
        val dg = ((pixel shr 8 and 0xFF) - (reference shr 8 and 0xFF)).toFloat()
        val db = ((pixel and 0xFF) - (reference and 0xFF)).toFloat()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    private fun otsu(histogram: IntArray): Int {
        val total = histogram.sum()
        if (total == 0) return 128
        var sum = 0.0
        for (i in histogram.indices) sum += i.toDouble() * histogram[i]
        var sumB = 0.0
        var weightB = 0
        var best = 0.0
        var threshold = 128
        for (i in histogram.indices) {
            weightB += histogram[i]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += i.toDouble() * histogram[i]
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > best) {
                best = between
                threshold = i
            }
        }
        return threshold
    }

    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val visited = BooleanArray(mask.size)
        var best = IntArray(0)
        val queue = IntArray(mask.size)
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var count = 0
            while (head < tail) {
                val index = queue[head++]
                count++
                val x = index % w
                val y = index / w
                if (x > 0) push(mask, visited, queue, index - 1, tail).let { if (it > tail) tail = it }
                if (x < w - 1) push(mask, visited, queue, index + 1, tail).let { if (it > tail) tail = it }
                if (y > 0) push(mask, visited, queue, index - w, tail).let { if (it > tail) tail = it }
                if (y < h - 1) push(mask, visited, queue, index + w, tail).let { if (it > tail) tail = it }
            }
            if (count > best.size) {
                best = queue.copyOfRange(0, tail)
            }
        }
        val out = BooleanArray(mask.size)
        best.forEach { out[it] = true }
        return out
    }

    private fun push(mask: BooleanArray, visited: BooleanArray, queue: IntArray, index: Int, tail: Int): Int {
        if (!mask[index] || visited[index]) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }
}

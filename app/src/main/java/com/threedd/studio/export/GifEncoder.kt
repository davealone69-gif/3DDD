package com.threedd.studio.export

import android.graphics.Bitmap
import java.io.OutputStream
import kotlin.math.sqrt

/**
 * A complete GIF89a encoder: median-cut palette generation, nearest-colour mapping and
 * LZW compression. Written here so the project can produce animated GIFs without a
 * third-party dependency.
 */
class GifEncoder(
    private val width: Int,
    private val height: Int,
    private val delayCentiseconds: Int = 8,
    private val loop: Boolean = true
) {

    private val frames = mutableListOf<IntArray>()

    fun addFrame(bitmap: Bitmap) {
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true).also { if (it != bitmap) bitmap.recycle() }
        } else bitmap
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        frames.add(pixels)
    }

    fun encode(out: OutputStream) {
        require(frames.isNotEmpty()) { "GIF needs at least one frame" }
        val palette = buildPalette()
        val indices = frames.map { mapToPalette(it, palette) }

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7)                       // global colour table, 256 entries
        out.write(0)                          // background colour index
        out.write(0)                          // pixel aspect ratio
        for (c in palette) {
            out.write(c shr 16 and 0xFF)
            out.write(c shr 8 and 0xFF)
            out.write(c and 0xFF)
        }
        for (i in palette.size until 256) repeat(3) { out.write(0) }

        if (loop && frames.size > 1) {
            out.write(0x21); out.write(0xFF); out.write(11)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(3); out.write(1); writeShort(out, 0); out.write(0)
        }

        indices.forEach { frame ->
            out.write(0x21); out.write(0xF9); out.write(4); out.write(0)
            writeShort(out, delayCentiseconds)
            out.write(0); out.write(0)
            out.write(0x2C)
            writeShort(out, 0); writeShort(out, 0)
            writeShort(out, width); writeShort(out, height)
            out.write(0)
            lzwEncode(out, frame, 8)
        }
        out.write(0x3B)
        out.flush()
    }

    // ---- palette ----

    private fun buildPalette(maxColors: Int = 256): IntArray {
        val sample = ArrayList<Int>(4096)
        frames.forEach { frame ->
            var i = 0
            val step = (frame.size / 1024).coerceAtLeast(1)
            while (i < frame.size) {
                sample.add(frame[i] and 0xFFFFFF)
                i += step
            }
        }
        if (sample.isEmpty()) return IntArray(maxColors) { it * 0x010101 }
        val boxes: MutableList<List<Int>> = mutableListOf(sample)
        while (boxes.size < maxColors) {
            val target = boxes.maxByOrNull { box -> channelRange(box) } ?: break
            if (target.size < 2 || channelRange(target) == 0) break
            boxes.remove(target)
            boxes.addAll(splitBox(target))
        }
        val palette = IntArray(maxColors)
        boxes.forEachIndexed { i, box ->
            if (i >= maxColors) return@forEachIndexed
            var r = 0L; var g = 0L; var b = 0L
            box.forEach { c ->
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            val n = box.size.coerceAtLeast(1)
            palette[i] = ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        return palette
    }

    private fun channelRange(box: List<Int>): Int {
        var rMin = 255; var rMax = 0; var gMin = 255; var gMax = 0; var bMin = 255; var bMax = 0
        box.forEach { c ->
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (r < rMin) rMin = r; if (r > rMax) rMax = r
            if (g < gMin) gMin = g; if (g > gMax) gMax = g
            if (b < bMin) bMin = b; if (b > bMax) bMax = b
        }
        return maxOf(rMax - rMin, gMax - gMin, bMax - bMin)
    }

    private fun splitBox(box: List<Int>): List<List<Int>> {
        var rMin = 255; var rMax = 0; var gMin = 255; var gMax = 0; var bMin = 255; var bMax = 0
        box.forEach { c ->
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (r < rMin) rMin = r; if (r > rMax) rMax = r
            if (g < gMin) gMin = g; if (g > gMax) gMax = g
            if (b < bMin) bMin = b; if (b > bMax) bMax = b
        }
        val spans = intArrayOf(rMax - rMin, gMax - gMin, bMax - bMin)
        val channel = spans.indices.maxByOrNull { spans[it] } ?: 0
        val shift = when (channel) { 0 -> 16; 1 -> 8; else -> 0 }
        val sorted = box.sortedBy { (it shr shift) and 0xFF }
        val mid = sorted.size / 2
        return listOf(sorted.subList(0, mid), sorted.subList(mid, sorted.size))
    }

    private fun mapToPalette(pixels: IntArray, palette: IntArray): ByteArray {
        val cache = HashMap<Int, Int>(1 shl 16)
        val out = ByteArray(pixels.size)
        pixels.forEachIndexed { i, packed ->
            val rgb = packed and 0xFFFFFF
            val index = cache.getOrPut(rgb) {
                var best = 0
                var bestDistance = Float.MAX_VALUE
                palette.forEachIndexed { pi, pc ->
                    val dr = ((rgb shr 16 and 0xFF) - (pc shr 16 and 0xFF)).toFloat()
                    val dg = ((rgb shr 8 and 0xFF) - (pc shr 8 and 0xFF)).toFloat()
                    val db = ((rgb and 0xFF) - (pc and 0xFF)).toFloat()
                    val d = dr * dr + dg * dg + db * db
                    if (d < bestDistance) { bestDistance = d; best = pi }
                }
                best
            }
            out[i] = index.toByte()
        }
        return out
    }

    // ---- LZW ----

    private fun lzwEncode(out: OutputStream, data: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1
        var dictionary = HashMap<String, Int>()

        val block = java.io.ByteArrayOutputStream()
        var bitBuffer = 0
        var bitCount = 0

        fun flushBlock() {
            while (block.size() > 0) {
                val chunk = ByteArray(minOf(255, block.size()))
                val copy = block.toByteArray()
                System.arraycopy(copy, 0, chunk, 0, chunk.size)
                out.write(chunk.size)
                out.write(chunk, 0, chunk.size)
                block.reset()
                if (copy.size > chunk.size) block.write(copy, chunk.size, copy.size - chunk.size)
            }
        }

        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                block.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
                if (block.size() >= 255) flushBlock()
            }
        }

        emit(clearCode)
        var current = ""
        for (b in data) {
            val next = current + b.toChar()
            if (dictionary.containsKey(next) || next.length == 1) {
                current = next
            } else {
                emit(if (current.length == 1) current[0].code else dictionary[current] ?: clearCode)
                dictionary[next] = nextCode++
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
                if (nextCode >= 4096) {
                    emit(clearCode)
                    dictionary = HashMap()
                    nextCode = eoiCode + 1
                    codeSize = minCodeSize + 1
                }
                current = b.toChar().toString()
            }
        }
        if (current.isNotEmpty()) {
            emit(if (current.length == 1) current[0].code else dictionary[current] ?: clearCode)
        }
        emit(eoiCode)
        if (bitCount > 0) {
            block.write(bitBuffer and 0xFF)
        }
        flushBlock()
        out.write(0)
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    /** Rough RMS error of the quantisation, used by tests/reporting. */
    fun paletteError(frames: List<IntArray>, palette: IntArray): Double {
        var sum = 0.0
        var n = 0
        frames.forEach { f ->
            f.forEach { p ->
                val best = palette.minOf { pc ->
                    val dr = ((p shr 16 and 0xFF) - (pc shr 16 and 0xFF)).toDouble()
                    val dg = ((p shr 8 and 0xFF) - (pc shr 8 and 0xFF)).toDouble()
                    val db = ((p and 0xFF) - (pc and 0xFF)).toDouble()
                    dr * dr + dg * dg + db * db
                }
                sum += best; n++
            }
        }
        return if (n == 0) 0.0 else sqrt(sum / n)
    }
}

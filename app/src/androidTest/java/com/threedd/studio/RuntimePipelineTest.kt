package com.threedd.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.MaterialState
import com.threedd.studio.data.model.ModelSource
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.export.ExportManager
import com.threedd.studio.render.OffscreenCapture
import com.threedd.studio.render.StudioRenderer
import com.threedd.studio.scan.ScanPipeline
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executes the real Filament pipeline on a device. This is the only place the rendering,
 * skinning, animation, capture, export and scan paths are actually run rather than compiled.
 */
@RunWith(AndroidJUnit4::class)
class RuntimePipelineTest {

    private lateinit var renderer: StudioRenderer
    private lateinit var exports: ExportManager

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        renderer = StudioRenderer(targetContext)
        exports = ExportManager(targetContext)
    }

    @After
    fun tearDown() {
        runCatching { renderer.destroy() }
    }

    private fun fixtureModel(): AvatarModel {
        val file = File(targetContext.cacheDir, "test-rig.glb")
        if (!file.exists()) {
            testContext.assets.open("test-rig.glb").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return AvatarModel(
            id = "fixture",
            displayName = "Fixture rig",
            source = ModelSource.IMPORTED,
            location = "file://${file.absolutePath}"
        )
    }

    @Test
    fun loadsEveryBuiltInRig() {
        ModelRepository.builtInModels.forEach { model ->
            assertTrue("failed to load ${model.displayName}", renderer.load(model))
            assertTrue("${model.displayName} has no triangles", renderer.models.triangleCount > 0)
            val height = renderer.models.boundingHeight
            assertTrue("${model.displayName} height $height out of range", height in 0.4f..4f)
            assertTrue("${model.displayName} exposes morph targets", renderer.models.morphTargetNames.isNotEmpty())
        }
    }

    @Test
    fun loadsSkinnedAnimatedTexturedFixture() {
        val model = fixtureModel()
        assertTrue("fixture failed to load", renderer.load(model))
        assertEquals(96, renderer.models.triangleCount)
        assertEquals(listOf("widen"), renderer.models.morphTargetNames)
        val clips = renderer.models.animations()
        assertEquals(1, clips.size)
        assertEquals("Bend", clips[0].name)
        assertTrue("clip should have a duration", clips[0].durationSeconds > 0.5f)
    }

    @Test
    fun evaluatesAnimationAcrossTheWholeClip() {
        assertTrue(renderer.load(fixtureModel()))
        // walk the clip, including the CUBICSPLINE and STEP sections, and re-upload bones each step
        var t = 0f
        while (t <= 1.0f) {
            renderer.models.applyAnimation(0, t)
            t += 0.05f
        }
        renderer.models.applyAnimation(0, 5f)
        assertTrue(renderer.models.triangleCount > 0)
    }

    @Test
    fun appliesMorphWeightsAndMaterialEdits() {
        assertTrue(renderer.load(fixtureModel()))
        renderer.setMorphWeights(mapOf("widen" to 1f))
        renderer.setMorphWeights(mapOf("widen" to 0.5f))
        renderer.setMorphWeights(emptyMap())
        renderer.setMaterial(
            MaterialState(baseColorHex = "#C0C8D8", metallic = 1f, roughness = 0.1f,
                emissiveHex = "#22E4FF", emissiveIntensity = 3f),
            overrideModelMaterials = true
        )
        renderer.useModelMaterials()
        assertTrue(renderer.models.triangleCount > 0)
    }

    @Test
    fun rendersAVisibleOffscreenFrame() {
        assertTrue(renderer.load(ModelRepository.builtInModels[0]))
        val size = 256
        val bitmap = OffscreenCapture(renderer).capture(size, size, 0L)
        assertNotNull("capture returned null", bitmap)
        bitmap!!

        // The scene clear colour is a very dark blue; the lit avatar must put brighter pixels on top.
        var lit = 0
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        pixels.forEach { p ->
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r + g + b > 90) lit++
        }
        bitmap.recycle()
        assertTrue("frame appears blank (lit=$lit of ${size * size})", lit > size * size / 100)
    }

    @Test
    fun exportsPngGifAndBakedGlb() {
        val model = fixtureModel()
        assertTrue(renderer.load(model))

        val png = runBlockingExport { exports.exportPng(renderer, 192, 192, "test-still") }
        assertNotNull("png export failed", png)
        assertTrue("png is empty", png!!.length() > 1024)

        val gif = runBlockingExport { exports.exportGif(renderer, size = 128, frames = 6, name = "test-gif") }
        assertNotNull("gif export failed", gif)
        val header = gif!!.readBytes().copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("GIF89a", header)

        val glb = runBlockingExport { exports.exportGlb(model, mapOf("widen" to 1f), "test-baked") }
        assertNotNull("glb export failed", glb)
        assertTrue("glb is empty", glb!!.length() > 1024)
    }

    @Test
    fun reconstructsAnAvatarFromSyntheticSilhouettes() {
        val pipeline = ScanPipeline(targetContext)
        val session = pipeline.newSession()
        val frames = ArrayList<File>()
        // A dark ellipse on a bright background, captured at turntable positions.
        for (i in 0 until ScanPipeline.MIN_FRAMES) {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.rgb(20, 20, 25); isAntiAlias = false }
            canvas.drawOval(90f, 40f, 166f, 216f, paint)
            val file = pipeline.frameFile(session, i)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            frames.add(file)
        }
        assertTrue("need enough frames", frames.size >= ScanPipeline.MIN_FRAMES)

        val output = pipeline.reconstruct(frames, "test-scan")
        assertNotNull("reconstruction produced no file", output)
        assertTrue("reconstructed glb is empty", output!!.length() > 512)
        pipeline.clearSession(session)
    }

    /** Instrumented tests run on a background thread, so a tiny blocking bridge is enough. */
    private fun <T> runBlockingExport(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}

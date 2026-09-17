package com.threedd.studio.render

import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.LightState
import com.threedd.studio.data.model.MaterialState
import com.threedd.studio.data.model.QualityPreset

/**
 * The rendering core: one Engine, Renderer, Scene, View and Camera for the studio.
 * Owned by the viewport composable for the lifetime of the screen.
 */
class StudioRenderer(private val context: Context) {

    val engine: Engine = Engine.create()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    private val em = engine.entityManager
    private val cameraEntity = em.create()
    val camera: Camera = engine.createCamera(cameraEntity)

    val materials = MaterialFactory(engine)
    private val environment = StudioEnvironment(engine, scene)
    val models = ModelLoader(context, engine, scene, materials)
    val orbit = OrbitCamera(camera)

    val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)

    private var swapChain: SwapChain? = null
    @Volatile private var viewportWidth = 1
    @Volatile private var viewportHeight = 1
    private var quality: QualityPreset = QualityPreset.BALANCED
    private var lightState = LightState()

    val isReadyToRender: Boolean get() = uiHelper.isReadyToRender && swapChain != null
    val loadedModelName: String? get() = loadedName
    private var loadedName: String? = null

    init {
        view.camera = camera
        view.scene = scene
        view.isPostProcessingEnabled = true
        view.bloomOptions = View.BloomOptions().apply {
            enabled = true
            strength = 0.12f
        }
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.02, 0.024, 0.043, 1.0)
        }
        uiHelper.setRenderCallback(object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromWindow() {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = null
                engine.flushAndWait()
            }

            override fun onResized(width: Int, height: Int) {
                viewportWidth = width.coerceAtLeast(1)
                viewportHeight = height.coerceAtLeast(1)
                view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
            }
        })
    }

    fun attach(surfaceView: SurfaceView) = uiHelper.attachTo(surfaceView)

    fun detach() = uiHelper.detach()

    fun setQuality(preset: QualityPreset) {
        quality = preset
    }

    fun setLight(state: LightState) {
        lightState = state
        environment.install(state)
    }

    fun updateLight(state: LightState) {
        lightState = state
        environment.apply(state)
    }

    fun load(model: AvatarModel): Boolean {
        val ok = models.load(model)
        if (ok) {
            loadedName = model.displayName
            environment.install(lightState)
            val box = models.boundingBoxCenterY to models.boundingHeight
            orbit.frame(box.first, box.second)
        }
        return ok
    }

    fun setMaterial(state: MaterialState, overrideModelMaterials: Boolean) {
        if (overrideModelMaterials) models.overrideMaterials(state)
    }

    fun useModelMaterials() = models.clearMaterialOverride()

    fun setMorphWeights(weights: Map<String, Float>) = models.setMorphWeights(weights)

    /**
     * Renders one frame into the currently bound RenderTarget. A headless swap chain of the
     * requested size satisfies beginFrame(); nothing is presented to the screen.
     */
    fun renderOffscreen(width: Int, height: Int, frameTimeNanos: Long): Boolean {
        val swap = ensureHeadlessSwapChain(width, height)
        if (renderer.beginFrame(swap, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            return true
        }
        return false
    }

    private var headlessSwapChain: SwapChain? = null
    private var headlessWidth = 0
    private var headlessHeight = 0

    private fun ensureHeadlessSwapChain(width: Int, height: Int): SwapChain {
        val existing = headlessSwapChain
        if (existing != null && headlessWidth == width && headlessHeight == height) return existing
        existing?.let { engine.destroySwapChain(it) }
        val created = engine.createSwapChain(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            SwapChainFlags.CONFIG_DEFAULT
        )
        headlessSwapChain = created
        headlessWidth = width
        headlessHeight = height
        return created
    }

    fun frame(frameTimeNanos: Long): Boolean {
        val swap = swapChain ?: return false
        if (!uiHelper.isReadyToRender) return false
        orbit.apply(viewportWidth.toDouble() / viewportHeight.toDouble())
        if (renderer.beginFrame(swap, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            return true
        }
        return false
    }

    // ---- encoder (video export) surface path ----

    fun createEncoderSwapChain(surface: Surface): SwapChain = engine.createSwapChain(surface)

    fun renderEncoderFrame(swapChain: SwapChain, frameTimeNanos: Long): Boolean {
        if (renderer.beginFrame(swapChain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            return true
        }
        return false
    }

    fun destroyEncoderSwapChain(swapChain: SwapChain) = engine.destroySwapChain(swapChain)

    fun destroy() {
        uiHelper.detach()
        headlessSwapChain?.let { engine.destroySwapChain(it) }
        headlessSwapChain = null
        models.destroy()
        materials.destroy()
        environment.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        em.destroy(cameraEntity)
        engine.destroy()
    }
}

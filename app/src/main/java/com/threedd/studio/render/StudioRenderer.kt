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
import com.threedd.studio.data.avatar.AppearanceSpec
import com.threedd.studio.repair.RepairSupervisor
import com.threedd.studio.data.avatar.PartBuilder
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.LightState
import com.threedd.studio.data.model.MaterialState
import com.threedd.studio.data.model.QualityPreset

/**
 * The rendering core: one Engine, Renderer, Scene, View and Camera for the studio.
 * Owned by the viewport composable for the lifetime of the screen.
 */
class StudioRenderer(val context: Context, private val supervisor: RepairSupervisor) {

    val engine: Engine = createEngine()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    private val em = engine.entityManager
    private val cameraEntity = em.create()
    val camera: Camera = engine.createCamera(cameraEntity)

    val materials = MaterialFactory(engine)
    private val environment = StudioEnvironment(engine, scene)
    val models = ModelLoader(context, engine, scene, materials)
    private val partLayer = PartLayer(engine, scene, materials)
    val orbit = OrbitCamera(camera)

    val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)

    private var swapChain: SwapChain? = null
    @Volatile private var viewportWidth = 1
    @Volatile private var viewportHeight = 1
    private var quality: QualityPreset = QualityPreset.BALANCED
    private var lightState = LightState()

    /**
     * Filament's DEFAULT backend selection can silently land on the no-op driver - notably on
     * emulators without Vulkan - and a no-op engine then fails every texture upload and
     * material creation with an opaque precondition error. Detect that and request GLES.
     */
    private fun createEngine(): Engine {
        var created = Engine.create()
        if (created.backend == Engine.Backend.NOOP) {
            runCatching { created.destroy() }
            created = runCatching { Engine.create(Engine.Backend.OPENGL) }.getOrElse { Engine.create() }
        }
        android.util.Log.i(TAG, "Filament backend = ${created.backend}")
        return created
    }

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

            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = null
                engine.flushAndWait()
            }

            override fun onResized(width: Int, height: Int) {
                viewportWidth = width.coerceAtLeast(1)
                viewportHeight = height.coerceAtLeast(1)
                view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
                // keep the subject filling the view when the viewport changes shape
                fitCamera()
            }
        })
    }

    fun attach(surfaceView: SurfaceView) = uiHelper.attachTo(surfaceView)

    val backendName: String get() = engine.backend.name

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

    /** Set when the last load failed, so the UI can explain an empty viewport. */
    var lastError: String? = null
        private set

    /**
     * Loads a model and frames it to fill the viewport. Never throws: a driver or asset
     * failure is reported through [lastError] rather than taking the screen down.
     */
    fun load(model: AvatarModel): Boolean {
        lastError = null
        // A load can fail for several distinct reasons. Rather than report and give up, try the
        // recoveries the app knows about, remembering which one works for this failure.
        val repairs = listOf(
            repair("clear-generated-parts", "Discard the generated wearables and retry") {
                partLayer.clear(); true
            },
            repair("rebuild-material", "Recompile the PBR material and retry") {
                materials.destroy(); true
            },
            repair("simplified-material", "Fall back to the simplified shader and retry") {
                materials.forceSimplified(); true
            },
            repair("clear-scene", "Empty the scene and retry") {
                scene.removeAllEntities(); true
            }
        )
        val loaded = supervisor.attempt("render-load", repairs) { models.load(model) }
        if (loaded == null) {
            lastError = "Could not load ${model.displayName} after repair attempts"
            return false
        }
        loadedName = model.displayName
        try {
            environment.install(lightState)
            lastBounds = models.bounds()
            fitCamera()
            applyAppearance(lastAppearance)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "failed to prepare the scene", t)
            lastError = t.message ?: t::class.java.simpleName
            return false
        }
        return true
    }

    private var lastBounds: FloatArray? = null

    /** Scales the model to fit the current viewport, accounting for its aspect ratio. */
    private var lastAppearance = AppearanceSpec()

    /**
     * Rebuilds the wearables from [appearance] and pushes the skin tone onto the avatar
     * material. Parts are sized from the loaded rig's proportions, so they fit any model.
     */
    fun applyAppearance(appearance: AppearanceSpec) {
        lastAppearance = appearance
        val metrics = PartBuilder.RigMetrics.fromBounds(lastBounds ?: models.bounds())
        val glow = appearance.glow.coerceIn(0f, 1f)

        val specs = listOf(
            PartLayer.Spec(
                PartBuilder.hair(appearance.hairStyle, metrics),
                MaterialState(baseColorHex = appearance.hairColorHex, metallic = 0.05f, roughness = 0.42f),
                castShadows = false
            ),
            PartLayer.Spec(
                PartBuilder.eyes(appearance.eyeColorHex, metrics),
                MaterialState(baseColorHex = appearance.eyeColorHex, metallic = 0.1f, roughness = 0.12f)
            ),
            PartLayer.Spec(
                PartBuilder.accessory(appearance.accessory, metrics),
                MaterialState(baseColorHex = appearance.accentColorHex, metallic = 0.85f, roughness = 0.2f)
            ),
            PartLayer.Spec(
                PartBuilder.augment(appearance.augment, metrics),
                MaterialState(
                    baseColorHex = "#4A5266", metallic = 1f, roughness = 0.26f,
                    emissiveHex = appearance.accentColorHex, emissiveIntensity = glow * 3f
                ),
                castShadows = true
            ),
            PartLayer.Spec(
                PartBuilder.outfit(appearance.outfit, metrics),
                MaterialState(baseColorHex = appearance.outfitColorHex, metallic = 0.12f, roughness = 0.6f)
            ),
            PartLayer.Spec(
                PartBuilder.tattoo(appearance.tattoo, metrics),
                MaterialState(
                    baseColorHex = "#121A2B", metallic = 0.2f, roughness = 0.35f,
                    emissiveHex = appearance.accentColorHex, emissiveIntensity = glow * 2.5f
                )
            )
        )
        partLayer.rebuild(specs)
        setMaterial(
            MaterialState(
                baseColorHex = appearance.skinToneHex,
                metallic = 0.05f,
                roughness = 0.55f,
                emissiveHex = "#000000",
                emissiveIntensity = 0f
            ),
            overrideModelMaterials = true
        )
    }

    /** Re-frames the loaded model to fill the viewport. */
    fun resetFraming() {
        orbit.reset()
        fitCamera()
    }

    private fun repair(id: String, description: String, action: () -> Boolean) =
        object : RepairSupervisor.Repair {
            override val id = id
            override val description = description
            override fun apply() = action()
        }

    private fun fitCamera() {
        val bounds = lastBounds ?: return
        val aspect = (viewportWidth.toFloat() / viewportHeight.toFloat()).coerceAtLeast(0.1f)
        orbit.fit(
            minX = bounds[0], minY = bounds[1], minZ = bounds[2],
            maxX = bounds[3], maxY = bounds[4], maxZ = bounds[5],
            aspect = aspect
        )
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

    companion object {
        const val TAG = "3DoubleD-Renderer"
    }

    fun destroy() {
        uiHelper.detach()
        headlessSwapChain?.let { engine.destroySwapChain(it) }
        headlessSwapChain = null
        partLayer.destroy()
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

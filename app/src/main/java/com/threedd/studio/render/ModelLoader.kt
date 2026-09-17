package com.threedd.studio.render

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.JitShaderProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.threedd.studio.data.model.AnimationClip
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.MaterialState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads glTF/GLB assets with gltfio. Materials for imported assets are produced by the
 * JIT shader provider (filamat running on device), so no `.uberz`/`.filamat` binary needs
 * to be shipped and no import path is stubbed out.
 */
class ModelLoader(
    private val context: Context,
    private val engine: Engine,
    private val scene: Scene,
    private val materialFactory: MaterialFactory
) {

    private val em: EntityManager = engine.entityManager
    private val materialProvider = JitShaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, null)
    private val resourceLoader = ResourceLoader(engine, materialProvider)

    private var asset: FilamentAsset? = null
    private var animator: com.google.android.filament.gltfio.Animator? = null
    private val studioInstances = mutableListOf<MaterialInstance>()
    private val originals = mutableListOf<Triple<Int, Int, MaterialInstance>>()

    val animationCount: Int get() = animator?.getAnimationCount() ?: 0
    val boundingHeight: Float
        get() {
            val a = asset ?: return 1.8f
            val box = a.boundingBox
            return (box.halfExtent[1] * 2f).takeIf { it > 0.01f } ?: 1.8f
        }

    val boundingBoxCenterY: Float
        get() {
            val a = asset ?: return 0.9f
            return a.boundingBox.center[1]
        }

    val bottomOffset: Float
        get() {
            val a = asset ?: return 0f
            val box = a.boundingBox
            return box.center[1] - box.halfExtent[1]
        }

    fun animations(): List<AnimationClip> {
        val an = animator ?: return emptyList()
        return (0 until an.getAnimationCount()).map { i ->
            AnimationClip(i, an.getAnimationName(i) ?: "Clip $i", an.getAnimationDuration(i))
        }
    }

    fun morphTargetNames(): List<String> = asset?.morphTargetNames?.toList() ?: emptyList()

    /** Loads a model, replacing anything currently in the scene. Returns true on success. */
    fun load(model: AvatarModel): Boolean {
        unload()
        val buffer = readModel(model) ?: return false
        val loaded = try {
            assetLoader.createAsset(buffer)
        } catch (t: Throwable) {
            null
        } ?: return false

        loaded.let { a ->
            resourceLoader.loadResources(a)
            a.releaseSourceData()
            scene.addEntities(a.entities)
            animator = a.animator
            asset = a
        }
        return true
    }

    /**
     * Swaps every renderable primitive onto a shared studio material instance, remembering
     * the glTF-provided instances so the override can be reverted without a reload.
     */
    fun overrideMaterials(state: MaterialState) {
        val a = asset ?: return
        val rm = engine.renderableManager
        if (studioInstances.isEmpty()) {
            a.entities.forEach { entity ->
                if (!rm.hasComponent(entity)) return@forEach
                val instance = rm.getInstance(entity)
                val primitives = rm.getPrimitiveCount(instance)
                for (p in 0 until primitives) {
                    val original = rm.getMaterialInstanceAt(instance, p)
                    if (original != null) originals.add(Triple(entity, p, original))
                    val mi = materialFactory.createInstance(state)
                    studioInstances.add(mi)
                    rm.setMaterialInstanceAt(instance, p, mi)
                }
            }
        } else {
            studioInstances.forEach { materialFactory.apply(it, state) }
        }
    }

    /** Restores the material instances the glTF loader created. */
    fun clearMaterialOverride() {
        val rm = engine.renderableManager
        originals.forEach { (entity, primitive, original) ->
            if (!rm.hasComponent(entity)) return@forEach
            val live = rm.getInstance(entity)
            rm.setMaterialInstanceAt(live, primitive, original)
        }
        originals.clear()
        releaseStudioInstances()
    }

    fun setMorphWeights(weights: Map<String, Float>) {
        val a = asset ?: return
        val names = a.morphTargetNames ?: return
        if (names.isEmpty()) return
        val values = FloatArray(names.size)
        names.forEachIndexed { i, name ->
            weights[name]?.let { values[i] = it.coerceIn(0f, 1f) }
        }
        a.setMorphTargetWeights(values)
    }

    fun applyAnimation(index: Int, timeSeconds: Float) {
        val an = animator ?: return
        if (an.getAnimationCount() == 0) return
        an.applyAnimation(index.coerceIn(0, an.getAnimationCount() - 1), timeSeconds)
        an.updateBoneMatrices()
    }

    fun unload() {
        originals.clear()
        releaseStudioInstances()
        asset?.let { a ->
            scene.removeEntities(a.entities)
            assetLoader.destroyAsset(a)
        }
        asset = null
        animator = null
    }

    fun destroy() {
        unload()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        resourceLoader.destroy()
    }

    private fun releaseStudioInstances() {
        studioInstances.forEach { engine.destroyMaterialInstance(it) }
        studioInstances.clear()
    }


    private fun readModel(model: AvatarModel): ByteBuffer? {
        val bytes: ByteArray = if (model.isAsset) {
            val path = model.location.removePrefix("models/")
            runCatching { context.assets.open("models/$path").use { it.readBytes() } }.getOrNull() ?: return null
        } else {
            val file = model.toUri().path?.let { File(it) } ?: return null
            if (!file.exists()) return null
            runCatching { file.readBytes() }.getOrNull() ?: return null
        }
        val direct = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        direct.put(bytes)
        direct.flip()
        return direct
    }
}

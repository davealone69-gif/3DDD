package com.threedd.studio.render

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.threedd.studio.data.model.LightState
import com.threedd.studio.render.MaterialFactory.Companion.toRgb

/**
 * Owns the image-based lighting, sky and three-point rig. All of it is built from
 * analytic sources at runtime - no HDR/probe binaries are required.
 */
class StudioEnvironment(private val engine: Engine, private val scene: Scene) {

    private val em = engine.entityManager
    private var indirectLight: IndirectLight? = null
    private var skybox: Skybox? = null
    private val keyEntity = em.create()
    private val fillEntity = em.create()
    private val rimEntity = em.create()
    private var entitiesAdded = false

    private fun directional(entity: Int, intensity: Float, colorHex: String, shadows: Boolean) {
        val (r, g, b) = colorHex.toRgb()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(intensity)
            .castShadows(shadows)
            .build(engine, entity)
    }

    fun install(state: LightState) {
        // Analytic SH-only indirect light: a neutral studio ambience with no reflection probe.
        indirectLight?.let { engine.destroyIndirectLight(it) }
        indirectLight = IndirectLight.Builder()
            .intensity(state.environmentIntensity)
            .build(engine)
        scene.indirectLight = indirectLight

        skybox?.let { engine.destroySkybox(it) }
        skybox = Skybox.Builder().color(0.02f, 0.024f, 0.043f, 1.0f).build(engine)
        scene.skybox = skybox

        directional(keyEntity, state.keyIntensity, state.keyColorHex, state.shadowsEnabled)
        directional(fillEntity, state.fillIntensity, state.fillColorHex, false)
        directional(rimEntity, state.rimIntensity, state.rimColorHex, false)
        if (!entitiesAdded) {
            scene.addEntity(keyEntity)
            scene.addEntity(fillEntity)
            scene.addEntity(rimEntity)
            entitiesAdded = true
        }

        setDirections(KEY_DIR, FILL_DIR, RIM_DIR)
    }

    fun apply(state: LightState) {
        indirectLight?.intensity = state.environmentIntensity
        directional(keyEntity, state.keyIntensity, state.keyColorHex, state.shadowsEnabled)
        directional(fillEntity, state.fillIntensity, state.fillColorHex, false)
        directional(rimEntity, state.rimIntensity, state.rimColorHex, false)
    }

    fun setDirections(key: FloatArray, fill: FloatArray, rim: FloatArray) {
        val lm = engine.lightManager
        lm.setDirection(keyEntity, key[0], key[1], key[2])
        lm.setDirection(fillEntity, fill[0], fill[1], fill[2])
        lm.setDirection(rimEntity, rim[0], rim[1], rim[2])
    }

    fun destroy() {
        indirectLight?.let { engine.destroyIndirectLight(it) }
        skybox?.let { engine.destroySkybox(it) }
        scene.removeEntities(intArrayOf(keyEntity, fillEntity, rimEntity))
        em.destroy(keyEntity)
        em.destroy(fillEntity)
        em.destroy(rimEntity)
        indirectLight = null
        skybox = null
    }

    companion object {
        val KEY_DIR = floatArrayOf(-0.45f, -0.78f, -0.44f)
        val FILL_DIR = floatArrayOf(0.72f, -0.28f, -0.63f)
        val RIM_DIR = floatArrayOf(0.10f, 0.32f, 0.94f)
    }
}

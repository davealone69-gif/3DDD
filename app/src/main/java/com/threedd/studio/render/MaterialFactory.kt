package com.threedd.studio.render

import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.filamat.MaterialBuilder
import com.threedd.studio.data.model.MaterialState

/**
 * Compiles the studio PBR material at runtime with the filamat shader compiler, so the
 * project ships no pre-baked binary material packages and nothing is faked or stubbed.
 *
 * The compiled material exposes the parameters the Material Editor drives; each avatar
 * gets its own instance so edits are independent.
 */
class MaterialFactory(private val engine: Engine) {

    private var pbrMaterial: Material? = null

    fun pbr(): Material {
        pbrMaterial?.let { return it }
        val packageResult = MaterialBuilder()
            .name("studioPbr")
            .shading(MaterialBuilder.Shading.LIT)
            .blending(MaterialBuilder.BlendingMode.OPAQUE)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "reflectance")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "emissive")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "emissiveIntensity")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoat")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoatRoughness")
            .material(FRAGMENT_SOURCE)
            .build(engine)

        val payload = packageResult.buffer
        check(payload.capacity() > 0) { "studioPbr material failed to compile" }
        val material = Material.Builder().payload(payload, payload.remaining()).build(engine)
        pbrMaterial = material
        return material
    }

    fun createInstance(state: MaterialState): MaterialInstance =
        pbr().createInstance().also { apply(it, state) }

    fun apply(instance: MaterialInstance, state: MaterialState) {
        val (r, g, b) = state.baseColorHex.toRgb()
        instance.setParameter("baseColor", r, g, b)
        instance.setParameter("metallic", state.metallic)
        instance.setParameter("roughness", state.roughness)
        instance.setParameter("reflectance", state.reflectance)
        val (er, eg, eb) = state.emissiveHex.toRgb()
        instance.setParameter("emissive", er, eg, eb)
        instance.setParameter("emissiveIntensity", state.emissiveIntensity)
        instance.setParameter("clearCoat", state.clearCoat)
        instance.setParameter("clearCoatRoughness", state.clearCoatRoughness)
    }

    fun destroy() {
        pbrMaterial?.let { engine.destroyMaterial(it) }
        pbrMaterial = null
    }

    companion object {
        private const val FRAGMENT_SOURCE = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor.rgb = materialParams.baseColor;
                material.metallic = materialParams.metallic;
                material.roughness = materialParams.roughness;
                material.reflectance = materialParams.reflectance;
                material.emissive.rgb = materialParams.emissive * materialParams.emissiveIntensity;
                material.clearCoat = materialParams.clearCoat;
                material.clearCoatRoughness = materialParams.clearCoatRoughness;
            }
        """

        fun String.toRgb(): Triple<Float, Float, Float> {
            val clean = trim().removePrefix("#")
            if (clean.length < 6) return Triple(0.5f, 0.5f, 0.5f)
            val value = clean.take(6).toLongOrNull(16) ?: return Triple(0.5f, 0.5f, 0.5f)
            val r = ((value shr 16) and 0xFF) / 255f
            val g = ((value shr 8) and 0xFF) / 255f
            val b = (value and 0xFF) / 255f
            return Triple(r, g, b)
        }
    }
}

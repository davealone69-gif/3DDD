package com.threedd.studio.render

import android.graphics.Bitmap
import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.filamat.MaterialBuilder
import com.threedd.studio.data.gltf.GltfDocument
import com.threedd.studio.data.model.MaterialState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compiles the studio PBR material on device with filamat and owns every Filament texture.
 *
 * The material samples the five glTF PBR maps by default, so imports keep their original
 * appearance and the Material Editor still drives factors on top of them. Missing maps bind
 * to neutral one-pixel textures, which makes the shader path identical for every primitive.
 */
class MaterialFactory(private val engine: Engine) {

    /** Filament resources bound to one primitive. */
    class TextureSet(
        val baseColor: Texture?,
        val metallicRoughness: Texture?,
        val normal: Texture?,
        val occlusion: Texture?,
        val emissive: Texture?,
        val samplers: Map<Int, TextureSampler>
    )

    private val materials = HashMap<Int, Material>()
    private val ownedTextures = ArrayList<Texture>()
    private val ownedSamplers = HashMap<Int, TextureSampler>()

    private var white: Texture? = null
    private var neutralNormal: Texture? = null

    /** True when the full PBR material could not be built and a simplified one is in use. */
    var degraded = false
        private set

    /**
     * Builds the studio PBR material, falling back to a parameter-only material if the driver
     * refuses it. A degraded material still lights and shades the model, so the viewport is
     * never blank; the failure is logged and surfaced rather than thrown at the UI.
     */
    private fun variant(alphaMode: Int): Material {
        materials[alphaMode]?.let { return it }
        val built = runCatching { buildFull(alphaMode) }
            .getOrElse { error ->
                android.util.Log.e(TAG, "full material failed to build; using the simplified material", error)
                degraded = true
                buildMinimal(alphaMode)
            }
        materials[alphaMode] = built
        return built
    }

    private fun buildFull(alphaMode: Int): Material {
        val builder = MaterialBuilder()
            .name("studioPbr$alphaMode")
            .shading(MaterialBuilder.Shading.LIT)
            // The package must be compiled for the backend the engine actually uses
            // (the emulator runs OpenGL ES through SwiftShader, devices may use Vulkan).
            // TargetApi.ALL is 0x15 in this binding and is not a valid union of the flags.
            .targetApi(targetApi())
            .require(MaterialBuilder.VertexAttribute.UV0)
            .require(MaterialBuilder.VertexAttribute.TANGENTS)
            .blending(
                when (alphaMode) {
                    1 -> MaterialBuilder.BlendingMode.MASKED
                    2 -> MaterialBuilder.BlendingMode.TRANSPARENT
                    else -> MaterialBuilder.BlendingMode.OPAQUE
                }
            )
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "reflectance")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "emissive")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "emissiveIntensity")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoat")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoatRoughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "normalScale")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "occlusionStrength")
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT, "baseColorMap"
            )
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT, "metallicRoughnessMap"
            )
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT, "normalMap"
            )
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT, "occlusionMap"
            )
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D, MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT, "emissiveMap"
            )
            .material(FRAGMENT_SOURCE)
        if (alphaMode == 1) builder.maskThreshold(0.5f)
        return finish(builder, alphaMode)
    }

    /** Factors only: no samplers, no texture fetches, no extra vertex requirements. */
    private fun buildMinimal(alphaMode: Int): Material {
        val builder = MaterialBuilder()
            .name("studioFallback$alphaMode")
            .shading(MaterialBuilder.Shading.LIT)
            .targetApi(targetApi())
            .blending(
                when (alphaMode) {
                    1 -> MaterialBuilder.BlendingMode.MASKED
                    2 -> MaterialBuilder.BlendingMode.TRANSPARENT
                    else -> MaterialBuilder.BlendingMode.OPAQUE
                }
            )
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "emissive")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "emissiveIntensity")
            .material(MINIMAL_FRAGMENT_SOURCE)
        if (alphaMode == 1) builder.maskThreshold(0.5f)
        return finish(builder, alphaMode)
    }

    private fun finish(builder: MaterialBuilder, alphaMode: Int): Material {
        val packageResult = builder.build(engine)
        val payload = packageResult.buffer
        if (payload.capacity() <= 0) {
            error("studioPbr material failed to compile (alphaMode=$alphaMode)")
        }
        return guarded({ "create Material (backend=${engine.backend}, bytes=${payload.remaining()})" }) {
            Material.Builder().payload(payload, payload.remaining()).build(engine)
        }
    }

    private fun targetApi(): MaterialBuilder.TargetApi = when (engine.backend) {
        Engine.Backend.VULKAN -> MaterialBuilder.TargetApi.VULKAN
        Engine.Backend.METAL -> MaterialBuilder.TargetApi.METAL
        else -> MaterialBuilder.TargetApi.OPENGL
    }

    fun createInstance(state: MaterialState, textures: TextureSet? = null, alphaMode: Int = 0): MaterialInstance {
        val instance = variant(alphaMode).createInstance()
        apply(instance, state)
        bind(instance, textures)
        return instance
    }

    fun apply(instance: MaterialInstance, state: MaterialState) {
        val (r, g, b) = state.baseColorHex.toRgb()
        val alpha = state.baseColorHex.toAlpha()
        instance.setParameter("baseColor", r, g, b, alpha)
        instance.setParameter("metallic", state.metallic)
        instance.setParameter("roughness", state.roughness)
        instance.setParameter("reflectance", state.reflectance)
        val (er, eg, eb) = state.emissiveHex.toRgb()
        instance.setParameter("emissive", er, eg, eb)
        instance.setParameter("emissiveIntensity", state.emissiveIntensity)
        instance.setParameter("clearCoat", state.clearCoat)
        instance.setParameter("clearCoatRoughness", state.clearCoatRoughness)
        instance.setParameter("normalScale", state.normalScale)
        instance.setParameter("occlusionStrength", state.occlusionStrength)
    }

    fun bind(instance: MaterialInstance, textures: TextureSet?) {
        if (degraded) return
        val fallback = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
        instance.setParameter("baseColorMap", textures?.baseColor ?: whiteTexture(), fallback)
        instance.setParameter("metallicRoughnessMap", textures?.metallicRoughness ?: whiteTexture(), fallback)
        instance.setParameter("normalMap", textures?.normal ?: neutralNormalTexture(), fallback)
        instance.setParameter("occlusionMap", textures?.occlusion ?: whiteTexture(), fallback)
        instance.setParameter("emissiveMap", textures?.emissive ?: whiteTexture(), fallback)
    }

    fun whiteTexture(): Texture {
        white?.let { return it }
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        val texture = upload(bytes, 1, 1, srgb = false)
        white = texture
        return texture
    }

    private fun neutralNormalTexture(): Texture {
        neutralNormal?.let { return it }
        val bytes = byteArrayOf(
            0x80.toByte(), 0x80.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        val texture = upload(bytes, 1, 1, srgb = false)
        neutralNormal = texture
        return texture
    }

    /** Builds the Filament texture set for one glTF material, decoding and uploading its maps. */
    fun texturesFor(document: GltfDocument.Document, material: GltfDocument.Material): TextureSet? {
        if (material.baseColorTexture < 0 && material.metallicRoughnessTexture < 0 &&
            material.normalTexture < 0 && material.occlusionTexture < 0 && material.emissiveTexture < 0
        ) {
            return null
        }
        val samplers = HashMap<Int, TextureSampler>()
        fun build(textureIndex: Int, srgb: Boolean): Texture? {
            if (textureIndex < 0) return null
            val ref = document.textures.getOrNull(textureIndex) ?: return null
            val encoded = document.images.getOrNull(ref.imageIndex) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
                ?: return null
            samplers[textureIndex] = samplerFor(ref)
            return textureFromBitmap(bitmap, srgb)
        }
        return TextureSet(
            baseColor = build(material.baseColorTexture, srgb = true),
            metallicRoughness = build(material.metallicRoughnessTexture, srgb = false),
            normal = build(material.normalTexture, srgb = false),
            occlusion = build(material.occlusionTexture, srgb = false),
            emissive = build(material.emissiveTexture, srgb = true),
            samplers = samplers
        )
    }

    private fun samplerFor(ref: GltfDocument.TextureRef): TextureSampler {
        ownedSamplers[ref.imageIndex]?.let { return it }
        val sampler = TextureSampler(
            when (ref.minFilter) {
                9728 -> TextureSampler.MinFilter.NEAREST
                9729 -> TextureSampler.MinFilter.LINEAR
                9984 -> TextureSampler.MinFilter.NEAREST_MIPMAP_NEAREST
                9985 -> TextureSampler.MinFilter.LINEAR_MIPMAP_NEAREST
                9986 -> TextureSampler.MinFilter.NEAREST_MIPMAP_LINEAR
                else -> TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR
            },
            if (ref.magFilter == 9728) TextureSampler.MagFilter.NEAREST else TextureSampler.MagFilter.LINEAR,
            wrapMode(ref.wrapS)
        )
        sampler.setWrapModeT(wrapMode(ref.wrapT))
        sampler.setWrapModeR(wrapMode(ref.wrapS))
        sampler.setAnisotropy(4f)
        ownedSamplers[ref.imageIndex] = sampler
        return sampler
    }

    private fun wrapMode(code: Int): TextureSampler.WrapMode = when (code) {
        33071 -> TextureSampler.WrapMode.CLAMP_TO_EDGE
        33648 -> TextureSampler.WrapMode.MIRRORED_REPEAT
        else -> TextureSampler.WrapMode.REPEAT
    }

    fun textureFromBitmap(bitmap: Bitmap, srgb: Boolean): Texture {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val width = argb.width
        val height = argb.height
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        argb.copyPixelsToBuffer(buffer)
        buffer.flip()
        val bytes = ByteArray(width * height * 4)
        buffer.get(bytes)
        if (argb != bitmap) argb.recycle()
        return upload(bytes, width, height, srgb)
    }

    private fun upload(bytes: ByteArray, width: Int, height: Int, srgb: Boolean): Texture {
        val texture = Texture.Builder()
            .width(width)
            .height(height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(if (srgb) Texture.InternalFormat.SRGB8_A8 else Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.SAMPLEABLE)
            .build(engine)
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        guarded({ "upload texture ${width}x${height} (bytes=${bytes.size}, srgb=$srgb)" }) {
            texture.setImage(
                engine, 0,
                Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE)
            )
        }
        engine.flushAndWait()
        ownedTextures.add(texture)
        return texture
    }

    fun destroy() {
        ownedTextures.forEach { engine.destroyTexture(it) }
        ownedTextures.clear()
        ownedSamplers.clear()
        white = null
        neutralNormal = null
        materials.values.forEach { engine.destroyMaterial(it) }
        materials.clear()
    }

    companion object {
        const val TAG = "3DoubleD-Material"
        private const val MINIMAL_FRAGMENT_SOURCE = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = materialParams.baseColor;
                material.metallic = materialParams.metallic;
                material.roughness = materialParams.roughness;
                material.emissive.rgb = materialParams.emissive * materialParams.emissiveIntensity;
            }
        """

        private const val FRAGMENT_SOURCE = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec2 uv = getUV0();

                vec4 baseMap = texture(materialParams_baseColorMap, uv);
                material.baseColor = baseMap * materialParams.baseColor;

                vec4 mrMap = texture(materialParams_metallicRoughnessMap, uv);
                material.metallic = materialParams.metallic * mrMap.b;
                material.roughness = materialParams.roughness * mrMap.g;

                vec3 normalSample = texture(materialParams_normalMap, uv).xyz * 2.0 - 1.0;
                normalSample.xy *= materialParams.normalScale;
                material.normal = normalize(normalSample);

                float ao = texture(materialParams_occlusionMap, uv).r;
                material.ambientOcclusion = mix(1.0, ao, materialParams.occlusionStrength);

                vec3 emissiveMap = texture(materialParams_emissiveMap, uv).rgb;
                material.emissive.rgb = materialParams.emissive * emissiveMap * materialParams.emissiveIntensity;

                material.reflectance = materialParams.reflectance;
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

        /** Supports 8-digit hex for opacity; defaults to fully opaque. */
        fun String.toAlpha(): Float {
            val clean = trim().removePrefix("#")
            if (clean.length < 8) return 1f
            return (clean.substring(6, 8).toIntOrNull(16) ?: 255) / 255f
        }
    }
}

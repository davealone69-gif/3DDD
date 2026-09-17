package com.threedd.studio.data.model

import android.net.Uri

/** Where a model came from. Drives which tab of the library it appears under. */
enum class ModelSource { BUILTIN, IMPORTED, SCANNED, PHOTO }

/** A renderable avatar/model the studio can load. */
data class AvatarModel(
    val id: String,
    val displayName: String,
    val source: ModelSource,
    /** Asset path ("models/x.glb") for built-ins, or a content:// / file:// uri otherwise. */
    val location: String,
    val thumbnailPath: String? = null,
    val sizeBytes: Long = 0L,
    val importedAtEpochMs: Long = 0L,
    val license: String = "CC0",
    val mature: Boolean = false
) {
    val isAsset: Boolean get() = source == ModelSource.BUILTIN

    fun toUri(): Uri = Uri.parse(location)
}

/** A point on the body that presets can drive. */
enum class BodyRegion { HEAD, TORSO, ARMS, LEGS, FULL_BODY }

/** Themed preset families shown in the Studio rail. */
enum class ThemeFamily {
    NEUTRAL, ARMOR, CYBORG, TATTOO, NEON, MATURE
}

data class BodyPreset(
    val id: String,
    val label: String,
    val region: BodyRegion,
    val family: ThemeFamily,
    /** Named morph targets and their 0..1 weights. */
    val morphWeights: Map<String, Float>,
    val mature: Boolean = false
)

data class MaterialState(
    val baseColorHex: String = "#C8C8C8",
    val metallic: Float = 0.0f,
    val roughness: Float = 0.65f,
    val reflectance: Float = 0.5f,
    val emissiveHex: String = "#000000",
    val emissiveIntensity: Float = 0.0f,
    val clearCoat: Float = 0.0f,
    val clearCoatRoughness: Float = 0.15f,
    val normalScale: Float = 1.0f,
    val occlusionStrength: Float = 1.0f
)

data class LightState(
    val environmentIntensity: Float = 30_000f,
    val keyIntensity: Float = 110_000f,
    val fillIntensity: Float = 30_000f,
    val rimIntensity: Float = 50_000f,
    val keyColorHex: String = "#FFFFFF",
    val fillColorHex: String = "#8FA8FF",
    val rimColorHex: String = "#FF2BD6",
    val shadowsEnabled: Boolean = true
)

data class RenderQuality(val preset: QualityPreset) {
    val msaaSampleCount: Int
        get() = when (preset) {
            QualityPreset.BATTERY -> 1
            QualityPreset.BALANCED -> 2
            QualityPreset.ULTRA -> 4
        }
    val shadowMapSize: Int
        get() = when (preset) {
            QualityPreset.BATTERY -> 512
            QualityPreset.BALANCED -> 1024
            QualityPreset.ULTRA -> 2048
        }
    val dynamicResolution: Boolean get() = preset == QualityPreset.BATTERY
}

enum class QualityPreset { BATTERY, BALANCED, ULTRA }

/** Everything that defines a saved avatar look. */
data class AvatarDesign(
    val id: Long = 0L,
    val name: String,
    val modelId: String,
    val bodyPresetId: String,
    val material: MaterialState,
    val light: LightState,
    val morphWeights: Map<String, Float> = emptyMap(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val mature: Boolean = false
)

/** glTF animation clip exposed by the loaded asset. */
data class AnimationClip(
    val index: Int,
    val name: String,
    val durationSeconds: Float
)

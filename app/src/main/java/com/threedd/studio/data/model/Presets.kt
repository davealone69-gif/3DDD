package com.threedd.studio.data.model

/**
 * Built-in preset tables.
 *
 * Morph-target names match the targets baked into the generated base models
 * (the .glb files under assets/models): muscle, weight, height, shoulder_width, hip_width,
 * bust, limb_length. Theme families drive material + accent geometry rather
 * than morphs, because that is where the visual difference actually lives.
 */
object Presets {

    private fun m(vararg pairs: Pair<String, Float>) = pairs.toMap()

    val bodyPresets: List<BodyPreset> = listOf(
        BodyPreset("neutral_base", "Neutral base", BodyRegion.FULL_BODY, ThemeFamily.NEUTRAL,
            m("height" to 0.5f, "muscle" to 0.35f, "weight" to 0.4f)),
        BodyPreset("athletic", "Athletic", BodyRegion.FULL_BODY, ThemeFamily.NEUTRAL,
            m("height" to 0.62f, "muscle" to 0.66f, "weight" to 0.26f,
              "shoulder_width" to 0.45f, "hip_width" to 0.48f)),
        BodyPreset("broad", "Broad", BodyRegion.FULL_BODY, ThemeFamily.NEUTRAL,
            m("height" to 0.74f, "muscle" to 0.85f, "weight" to 0.55f,
              "shoulder_width" to 0.78f, "hip_width" to 0.55f)),
        BodyPreset("slender", "Slender", BodyRegion.FULL_BODY, ThemeFamily.NEUTRAL,
            m("height" to 0.55f, "muscle" to 0.15f, "weight" to 0.08f,
              "shoulder_width" to 0.32f, "limb_length" to 0.35f)),
        BodyPreset("curvy", "Curvy", BodyRegion.FULL_BODY, ThemeFamily.NEUTRAL,
            m("height" to 0.5f, "weight" to 0.55f, "hip_width" to 0.75f, "bust" to 0.6f)),
        BodyPreset("armored_plate", "Plate armour", BodyRegion.TORSO, ThemeFamily.ARMOR,
            m("muscle" to 0.6f, "shoulder_width" to 0.65f)),
        BodyPreset("cyborg_chrome", "Chrome limbs", BodyRegion.ARMS, ThemeFamily.CYBORG,
            m("muscle" to 0.55f, "limb_length" to 0.2f)),
        BodyPreset("cyborg_full", "Full conversion", BodyRegion.FULL_BODY, ThemeFamily.CYBORG,
            m("muscle" to 0.7f, "height" to 0.6f, "shoulder_width" to 0.7f)),
        BodyPreset("tattoo_sleeve", "Tattoo sleeve", BodyRegion.ARMS, ThemeFamily.TATTOO,
            m("muscle" to 0.4f)),
        BodyPreset("tattoo_back", "Back piece", BodyRegion.TORSO, ThemeFamily.TATTOO,
            m("muscle" to 0.45f, "weight" to 0.35f)),
        BodyPreset("neon_rider", "Neon rider", BodyRegion.FULL_BODY, ThemeFamily.NEON,
            m("muscle" to 0.5f, "height" to 0.55f)),
        BodyPreset("anatomical", "Anatomical study", BodyRegion.FULL_BODY, ThemeFamily.MATURE,
            m("muscle" to 0.55f, "weight" to 0.45f), mature = true)
    )

    fun preset(id: String): BodyPreset? = bodyPresets.firstOrNull { it.id == id }

    val materialPresets: Map<String, MaterialState> = linkedMapOf(
        "Skin" to MaterialState("#D8A98C", metallic = 0.0f, roughness = 0.55f, reflectance = 0.35f),
        "Chrome" to MaterialState("#E8F2FF", metallic = 1.0f, roughness = 0.08f, reflectance = 0.95f),
        "Gunmetal" to MaterialState("#4A5266", metallic = 0.9f, roughness = 0.35f),
        "Matte armour" to MaterialState("#2B3242", metallic = 0.35f, roughness = 0.8f),
        "Neon emissive" to MaterialState("#101828", metallic = 0.2f, roughness = 0.3f,
            emissiveHex = "#22E4FF", emissiveIntensity = 4.0f),
        "Coated latex" to MaterialState("#0F0F14", metallic = 0.0f, roughness = 0.12f, clearCoat = 1.0f),
        "Tattoo skin" to MaterialState("#C99873", metallic = 0.0f, roughness = 0.6f)
    )

    val lightPresets: Map<String, LightState> = linkedMapOf(
        "Studio soft" to LightState(environmentIntensity = 35_000f, keyIntensity = 95_000f,
            fillIntensity = 45_000f, rimIntensity = 35_000f),
        "Neon night" to LightState(environmentIntensity = 14_000f, keyIntensity = 70_000f,
            fillIntensity = 18_000f, rimIntensity = 120_000f,
            rimColorHex = "#FF2BD6", fillColorHex = "#22E4FF"),
        "Hard noir" to LightState(environmentIntensity = 9_000f, keyIntensity = 160_000f,
            fillIntensity = 8_000f, rimIntensity = 60_000f),
        "Daylight" to LightState(environmentIntensity = 45_000f, keyIntensity = 130_000f,
            fillIntensity = 60_000f, rimIntensity = 30_000f)
    )

    /** Theme family -> material override applied when the preset is selected. */
    fun materialForFamily(family: ThemeFamily): MaterialState? = when (family) {
        ThemeFamily.ARMOR -> materialPresets["Matte armour"]
        ThemeFamily.CYBORG -> materialPresets["Chrome"]
        ThemeFamily.TATTOO -> materialPresets["Tattoo skin"]
        ThemeFamily.NEON -> materialPresets["Neon emissive"]
        ThemeFamily.MATURE -> materialPresets["Skin"]
        ThemeFamily.NEUTRAL -> null
    }
}

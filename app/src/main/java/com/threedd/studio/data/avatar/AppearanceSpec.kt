package com.threedd.studio.data.avatar

/**
 * The studio's customisation model.
 *
 * Field set follows the avatar spec used by the existing Aura/AI-Girls projects, so the
 * options the editor offers are the options the product actually defines.
 */
data class AppearanceSpec(
    val style: String = STYLES.first(),
    val skinToneHex: String = "#E8C4A8",
    val hairStyle: String = HairStyle.SHORT.id,
    val hairColorHex: String = "#4A3728",
    val eyeColorHex: String = "#4A90E2",
    val faceShape: String = FaceShape.OVAL.id,
    val bodyType: String = BodyType.SLIM.id,
    val outfit: String = Outfit.NONE.id,
    val outfitColorHex: String = "#667EEA",
    val accessory: String = Accessory.NONE.id,
    val augment: String = Augment.NONE.id,
    val tattoo: String = Tattoo.NONE.id,
    val accentColorHex: String = "#22E4FF",
    val glow: Float = 0.4f
) {
    companion object {
        val STYLES = listOf("Realistic", "Stylised", "Anime", "Cyberpunk", "Minimal")
    }

    enum class HairStyle(val id: String, val label: String) {
        BALD("bald", "Bald"),
        SHORT("short", "Short"),
        BOB("bob", "Bob"),
        LONG("long", "Long"),
        PONYTAIL("ponytail", "Ponytail"),
        MOHAWK("mohawk", "Mohawk"),
        CURLY("curly", "Curly");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: SHORT }
    }

    enum class FaceShape(val id: String, val label: String) {
        OVAL("oval", "Oval"), ROUND("round", "Round"),
        SQUARE("square", "Square"), HEART("heart", "Heart");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: OVAL }
    }

    enum class BodyType(val id: String, val label: String, val width: Float) {
        SLIM("slim", "Slim", 0.88f), ATHLETIC("athletic", "Athletic", 1.0f),
        CURVY("curvy", "Curvy", 1.12f), BROAD("broad", "Broad", 1.2f);

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: SLIM }
    }

    enum class Outfit(val id: String, val label: String) {
        NONE("none", "None"),
        BODYSUIT("bodysuit", "Bodysuit"),
        DRESS("dress", "Dress"),
        JACKET("jacket", "Jacket"),
        ARMOUR("armour", "Armour");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE }
    }

    enum class Accessory(val id: String, val label: String) {
        NONE("none", "None"),
        GLASSES("glasses", "Glasses"),
        VISOR("visor", "Visor"),
        EARRINGS("earrings", "Earrings"),
        COLLAR("collar", "Collar"),
        CROWN("crown", "Crown");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE }
    }

    enum class Augment(val id: String, val label: String) {
        NONE("none", "None"),
        SHOULDER_PLATE("shoulder", "Shoulder plate"),
        ARM_CASING("arm", "Arm casing"),
        SPINE_RIG("spine", "Spine rig"),
        HEAD_IMPLANT("implant", "Head implant");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE }
    }

    enum class Tattoo(val id: String, val label: String) {
        NONE("none", "None"),
        SLEEVE("sleeve", "Sleeve"),
        BACK("back", "Back piece"),
        CHEST("chest", "Chest"),
        CIRCUIT("circuit", "Circuit lines");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE }
    }

    /** Colour swatches shared by the editor panels. */
    val palettes: Map<String, List<String>>
        get() = mapOf(
            "Skin" to listOf("#F2D6C2", "#E8C4A8", "#D8A98C", "#C68E6E", "#A9714F", "#8A5638", "#5E3A25"),
            "Hair" to listOf("#1A1410", "#4A3728", "#7A4A25", "#C8A165", "#E8D9A0", "#B03A2E", "#5B2C6F", "#22E4FF"),
            "Eyes" to listOf("#4A90E2", "#3E7A5A", "#6B4A22", "#5C5C6B", "#22C55E", "#A855F7", "#EF4444"),
            "Accent" to listOf("#22E4FF", "#FF2BD6", "#FFC24B", "#39E08B", "#FF5A6E", "#FFFFFF")
        )
}

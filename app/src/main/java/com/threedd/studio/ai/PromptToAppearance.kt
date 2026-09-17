package com.threedd.studio.ai

import com.threedd.studio.data.avatar.AppearanceSpec

/**
 * Turns a written description into an avatar configuration.
 *
 * Runs entirely on device with keyword matching, so generating an avatar from a sentence is
 * free, offline and deterministic. It is also the parse step the local model can refine:
 * whatever is recognised here is a valid starting configuration either way.
 */
object PromptToAppearance {

    fun parse(text: String, base: AppearanceSpec = AppearanceSpec()): AppearanceSpec {
        val t = text.lowercase()
        var spec = base

        HAIR_STYLES.forEach { (word, style) ->
            if (word in t) spec = spec.copy(hairStyle = style.id)
        }
        HAIR_COLOURS.forEach { (word, hex) ->
            if (word in t) spec = spec.copy(hairColorHex = hex)
        }
        EYE_COLOURS.forEach { (word, hex) ->
            if ("$word eye" in t || "$word eyes" in t || "eyes $word" in t) spec = spec.copy(eyeColorHex = hex)
        }
        SKIN_TONES.forEach { (word, hex) ->
            if ("$word skin" in t) spec = spec.copy(skinToneHex = hex)
        }
        OUTFITS.forEach { (word, outfit) ->
            if (word in t) spec = spec.copy(outfit = outfit.id)
        }
        AUGMENTS.forEach { (word, augment) ->
            if (word in t) spec = spec.copy(augment = augment.id)
        }
        TATTOOS.forEach { (word, tattoo) ->
            if (word in t) spec = spec.copy(tattoo = tattoo.id)
        }
        ACCESSORIES.forEach { (word, accessory) ->
            if (word in t) spec = spec.copy(accessory = accessory.id)
        }
        ACCENTS.forEach { (word, hex) ->
            if (word in t) spec = spec.copy(accentColorHex = hex, glow = 0.75f)
        }
        return spec
    }

    /** A one-line summary of what the parser understood, for confirmation in the UI. */
    fun summarise(spec: AppearanceSpec): String = buildString {
        append("${spec.hairStyle} ${spec.hairColorHex} hair, ${spec.eyeColorHex} eyes")
        if (spec.outfit != AppearanceSpec.Outfit.NONE.id) append(", ${spec.outfit} outfit")
        if (spec.augment != AppearanceSpec.Augment.NONE.id) append(", ${spec.augment} augments")
        if (spec.tattoo != AppearanceSpec.Tattoo.NONE.id) append(", ${spec.tattoo} tattoo")
        if (spec.accessory != AppearanceSpec.Accessory.NONE.id) append(", ${spec.accessory}")
        append(", ${spec.skinToneHex} skin")
    }

    private val HAIR_STYLES = listOf(
        "bald" to AppearanceSpec.HairStyle.BALD,
        "mohawk" to AppearanceSpec.HairStyle.MOHAWK,
        "ponytail" to AppearanceSpec.HairStyle.PONYTAIL,
        "bob" to AppearanceSpec.HairStyle.BOB,
        "curly" to AppearanceSpec.HairStyle.CURLY,
        "long hair" to AppearanceSpec.HairStyle.LONG,
        "long" to AppearanceSpec.HairStyle.LONG,
        "short" to AppearanceSpec.HairStyle.SHORT
    )

    private val HAIR_COLOURS = listOf(
        "black" to "#1A1410", "brown" to "#4A3728", "blonde" to "#E8D9A0",
        "ginger" to "#B03A2E", "red" to "#B03A2E", "purple" to "#5B2C6F",
        "pink" to "#FF2BD6", "white" to "#E8E8E8", "blue" to "#2F6FBF", "green" to "#2E8B57"
    )

    private val EYE_COLOURS = listOf(
        "blue" to "#4A90E2", "green" to "#22C55E", "brown" to "#6B4A22",
        "grey" to "#5C5C6B", "gray" to "#5C5C6B", "violet" to "#A855F7", "red" to "#EF4444"
    )

    private val SKIN_TONES = listOf(
        "pale" to "#F2D6C2", "fair" to "#E8C4A8", "olive" to "#C68E6E",
        "tan" to "#A9714F", "brown" to "#8A5638", "dark" to "#5E3A25"
    )

    private val OUTFITS = listOf(
        "bodysuit" to AppearanceSpec.Outfit.BODYSUIT,
        "dress" to AppearanceSpec.Outfit.DRESS,
        "jacket" to AppearanceSpec.Outfit.JACKET,
        "armour" to AppearanceSpec.Outfit.ARMOUR,
        "armor" to AppearanceSpec.Outfit.ARMOUR
    )

    private val AUGMENTS = listOf(
        "shoulder plate" to AppearanceSpec.Augment.SHOULDER_PLATE,
        "arm casing" to AppearanceSpec.Augment.ARM_CASING,
        "spine" to AppearanceSpec.Augment.SPINE_RIG,
        "implant" to AppearanceSpec.Augment.HEAD_IMPLANT,
        "cyborg" to AppearanceSpec.Augment.SHOULDER_PLATE,
        "augment" to AppearanceSpec.Augment.SHOULDER_PLATE
    )

    private val TATTOOS = listOf(
        "sleeve" to AppearanceSpec.Tattoo.SLEEVE,
        "back piece" to AppearanceSpec.Tattoo.BACK,
        "chest" to AppearanceSpec.Tattoo.CHEST,
        "circuit" to AppearanceSpec.Tattoo.CIRCUIT,
        "tattoo" to AppearanceSpec.Tattoo.SLEEVE
    )

    private val ACCESSORIES = listOf(
        "glasses" to AppearanceSpec.Accessory.GLASSES,
        "visor" to AppearanceSpec.Accessory.VISOR,
        "earrings" to AppearanceSpec.Accessory.EARRINGS,
        "collar" to AppearanceSpec.Accessory.COLLAR,
        "crown" to AppearanceSpec.Accessory.CROWN
    )

    private val ACCENTS = listOf(
        "neon" to "#22E4FF", "cyan" to "#22E4FF", "magenta" to "#FF2BD6",
        "amber" to "#FFC24B", "emerald" to "#39E08B", "glowing" to "#22E4FF"
    )
}

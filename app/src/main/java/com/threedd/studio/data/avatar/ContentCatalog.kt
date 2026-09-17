package com.threedd.studio.data.avatar

/**
 * The studio's content library.
 *
 * Entries are parametric definitions rather than stored meshes: each one carries a small set of
 * parameters that [PartBuilder] turns into real geometry. That is what makes a library this
 * size practical, and it means every entry produces a genuinely different shape instead of a
 * renamed duplicate.
 *
 * Counts are systematic combinations, so each entry can be named by what it actually is.
 */
object ContentCatalog {

    // ---------------- skins: 10 ----------------

    data class Skin(val id: String, val label: String, val hex: String, val roughness: Float)

    val skins: List<Skin> = listOf(
        Skin("porcelain", "Porcelain", "#F7E4D6", 0.42f),
        Skin("fair", "Fair", "#F0D3BC", 0.45f),
        Skin("light", "Light", "#E8C4A8", 0.48f),
        Skin("warm", "Warm beige", "#DFB394", 0.50f),
        Skin("olive", "Olive", "#C68E6E", 0.52f),
        Skin("tan", "Tan", "#B87F5C", 0.54f),
        Skin("bronze", "Bronze", "#A9714F", 0.56f),
        Skin("umber", "Umber", "#8A5638", 0.58f),
        Skin("espresso", "Espresso", "#6B3F28", 0.60f),
        Skin("obsidian", "Obsidian", "#4A2C1D", 0.62f)
    )

    // ---------------- bodies: 20 ----------------
    // height / muscle / weight / shoulder / hip / bust / limb, all 0..1 morph weights.

    data class Body(
        val id: String,
        val label: String,
        val height: Float,
        val muscle: Float,
        val weight: Float,
        val shoulder: Float,
        val hip: Float,
        val bust: Float,
        val limb: Float
    ) {
        fun morphWeights(): Map<String, Float> = mapOf(
            "height" to height,
            "muscle" to muscle,
            "weight" to weight,
            "shoulder_width" to shoulder,
            "hip_width" to hip,
            "bust" to bust,
            "limb_length" to limb
        )
    }

    val bodies: List<Body> = buildList {
        val frames = listOf(
            Triple("petite", 0.18f, 0.22f),
            Triple("slim", 0.34f, 0.20f),
            Triple("athletic", 0.52f, 0.62f),
            Triple("curvy", 0.44f, 0.36f),
            Triple("statuesque", 0.74f, 0.44f),
            Triple("heroic", 0.66f, 0.82f),
            Triple("broad", 0.58f, 0.70f),
            Triple("lanky", 0.80f, 0.24f),
            Triple("stocky", 0.30f, 0.66f),
            Triple("colossal", 0.88f, 0.92f)
        )
        frames.forEachIndexed { index, (name, height, muscle) ->
            val build = if (index % 2 == 0) "female" else "male"
            add(
                Body(
                    id = "$name-$build",
                    label = name.replaceFirstChar { it.uppercase() } + " (" + build + ")",
                    height = height,
                    muscle = muscle,
                    weight = (muscle * 0.6f + 0.2f).coerceIn(0f, 1f),
                    shoulder = if (build == "male") (muscle * 0.85f) else (muscle * 0.42f),
                    hip = if (build == "female") (0.45f + muscle * 0.25f) else (muscle * 0.35f),
                    bust = if (build == "female") (0.35f + muscle * 0.3f) else 0.05f,
                    limb = height * 0.55f
                )
            )
            add(
                Body(
                    id = "$name-$build-alt",
                    label = name.replaceFirstChar { it.uppercase() } + " (alt)",
                    height = height,
                    muscle = (muscle * 0.82f).coerceIn(0f, 1f),
                    weight = (muscle * 0.5f + 0.3f).coerceIn(0f, 1f),
                    shoulder = if (build == "male") (muscle * 0.6f) else (muscle * 0.3f),
                    hip = if (build == "female") (0.6f + muscle * 0.2f) else (muscle * 0.28f),
                    bust = if (build == "female") (0.5f + muscle * 0.2f) else 0.05f,
                    limb = height * 0.45f
                )
            )
        }
    }

    // ---------------- faces: 50 = shape(5) x jaw(2) x eyes(5) ----------------

    enum class FaceShape(val id: String, val label: String, val width: Float, val length: Float) {
        OVAL("oval", "Oval", 1.0f, 1.0f),
        ROUND("round", "Round", 1.08f, 0.94f),
        SQUARE("square", "Square", 1.12f, 0.98f),
        HEART("heart", "Heart", 1.04f, 1.02f),
        DIAMOND("diamond", "Diamond", 0.98f, 1.04f)
    }

    enum class Jaw(val id: String, val label: String, val width: Float) {
        SOFT("soft", "Soft jaw", 0.94f),
        DEFINED("defined", "Defined jaw", 1.06f)
    }

    enum class EyeShape(val id: String, val label: String, val scale: Float, val tilt: Float) {
        ALMOND("almond", "Almond", 1.0f, 0.0f),
        ROUND("round", "Round", 1.15f, 0.0f),
        HOODED("hooded", "Hooded", 0.92f, -0.08f),
        UPTURNED("upturned", "Upturned", 1.02f, 0.14f),
        WIDE("wide", "Wide set", 1.05f, 0.0f)
    }

    data class Face(
        val id: String,
        val label: String,
        val shape: FaceShape,
        val jaw: Jaw,
        val eyes: EyeShape
    ) {
        /** Head scale applied to the rig, and the eye geometry the builder uses. */
        val headWidth: Float get() = shape.width * jaw.width
        val headLength: Float get() = shape.length
        val eyeScale: Float get() = eyes.scale
        val eyeTilt: Float get() = eyes.tilt
        val eyeSpacing: Float get() = if (eyes == EyeShape.WIDE) 0.46f else 0.38f
    }

    val faces: List<Face> = buildList {
        FaceShape.entries.forEach { shape ->
            Jaw.entries.forEach { jaw ->
                EyeShape.entries.forEach { eyes ->
                    add(
                        Face(
                            id = "${shape.id}-${jaw.id}-${eyes.id}",
                            label = "${shape.label} - ${jaw.label} - ${eyes.label} eyes",
                            shape = shape, jaw = jaw, eyes = eyes
                        )
                    )
                }
            }
        }
    }

    // ---------------- hair: 100 = style(10) x volume(2) x fringe(5) ----------------

    enum class HairBase(val id: String, val label: String) {
        SHAVED("shaved", "Shaved"),
        CROP("crop", "Crop"),
        BOB("bob", "Bob"),
        MIDI("midi", "Midi"),
        LONG("long", "Long"),
        PONYTAIL("ponytail", "Ponytail"),
        BUN("bun", "Bun"),
        TWINTAILS("twintails", "Twintails"),
        BRAIDS("braids", "Braids"),
        MOHAWK("mohawk", "Mohawk")
    }

    enum class HairFringe(val id: String, val label: String) {
        NONE("none", "No fringe"),
        STRAIGHT("straight", "Straight fringe"),
        SIDE("side", "Side fringe"),
        SWEPT("swept", "Swept fringe"),
        CURTAIN("curtain", "Curtain fringe")
    }

    data class Hair(
        val id: String,
        val label: String,
        val base: HairBase,
        val volume: Float, // 0 = close to the skull, 1 = voluminous
        val fringe: HairFringe
    )

    val hair: List<Hair> = buildList {
        HairBase.entries.forEach { base ->
            listOf(0f to "Sleek", 1f to "Voluminous").forEach { (volume, volumeLabel) ->
                HairFringe.entries.forEach { fringe ->
                    add(
                        Hair(
                            id = "${base.id}-${if (volume > 0.5f) "vol" else "sleek"}-${fringe.id}",
                            label = "${base.label} - $volumeLabel - ${fringe.label}",
                            base = base, volume = volume, fringe = fringe
                        )
                    )
                }
            }
        }
    }

    // ---------------- outfits: 200 = top(5) x sleeve(4) x length(2) x trim(5) ----------------

    enum class Garment(val id: String, val label: String) {
        TUNIC("tunic", "Tunic"),
        BODYSUIT("bodysuit", "Bodysuit"),
        DRESS("dress", "Dress"),
        JACKET("jacket", "Jacket"),
        ARMOUR("armour", "Armour")
    }

    enum class Sleeve(val id: String, val label: String, val length: Float) {
        NONE("none", "Sleeveless", 0f),
        SHORT("short", "Short sleeve", 0.3f),
        LONG("long", "Long sleeve", 0.7f),
        FULL("full", "Full sleeve", 1f)
    }

    enum class GarmentLength(val id: String, val label: String, val toHip: Boolean) {
        CROPPED("cropped", "Cropped", false),
        FULL("full", "Full length", true)
    }

    enum class Trim(val id: String, val label: String, val emissive: Float) {
        PLAIN("plain", "Plain", 0f),
        NEON("neon", "Neon piping", 3.0f),
        STUDS("studs", "Studs", 0.4f),
        LACED("laced", "Laced", 0f),
        PLATED("plated", "Plated", 1.2f)
    }

    data class Outfit(
        val id: String,
        val label: String,
        val garment: Garment,
        val sleeve: Sleeve,
        val length: GarmentLength,
        val trim: Trim
    )

    val outfits: List<Outfit> = buildList {
        Garment.entries.forEach { garment ->
            Sleeve.entries.forEach { sleeve ->
                GarmentLength.entries.forEach { length ->
                    Trim.entries.forEach { trim ->
                        add(
                            Outfit(
                                id = "${garment.id}-${sleeve.id}-${length.id}-${trim.id}",
                                label = "${garment.label} - ${sleeve.label}, ${length.label}, ${trim.label}",
                                garment = garment, sleeve = sleeve, length = length, trim = trim
                            )
                        )
                    }
                }
            }
        }
    }

    // ---------------- accessories: 100 = kind(10) x metal(2) x gem(5) ----------------

    enum class AccessoryKind(val id: String, val label: String) {
        GLASSES("glasses", "Glasses"),
        VISOR("visor", "Visor"),
        MASK("mask", "Mask"),
        EARRINGS("earrings", "Earrings"),
        NECKLACE("necklace", "Necklace"),
        CROWN("crown", "Crown"),
        HORNS("horns", "Horns"),
        WINGS("wings", "Wings"),
        BELT("belt", "Belt"),
        BRACELET("bracelet", "Bracelet")
    }

    enum class Metal(val id: String, val label: String, val hex: String, val roughness: Float) {
        CHROME("chrome", "Chrome", "#E8F2FF", 0.08f),
        GOLD("gold", "Gold", "#F0C24B", 0.22f)
    }

    enum class Gem(val id: String, val label: String, val hex: String) {
        NONE("none", "No gem", "#000000"),
        AMBER("amber", "Amber", "#FFC24B"),
        CYAN("cyan", "Cyan", "#22E4FF"),
        MAGENTA("magenta", "Magenta", "#FF2BD6"),
        EMERALD("emerald", "Emerald", "#39E08B")
    }

    data class Accessory(
        val id: String,
        val label: String,
        val kind: AccessoryKind,
        val metal: Metal,
        val gem: Gem
    )

    val accessories: List<Accessory> = buildList {
        AccessoryKind.entries.forEach { kind ->
            Metal.entries.forEach { metal ->
                Gem.entries.forEach { gem ->
                    add(
                        Accessory(
                            id = "${kind.id}-${metal.id}-${gem.id}",
                            label = "${kind.label} - ${metal.label}" + if (gem == Gem.NONE) "" else ", ${gem.label} gem",
                            kind = kind, metal = metal, gem = gem
                        )
                    )
                }
            }
        }
    }

    // ---------------- animations: 30 ----------------

    /** A keyframed channel over a named rig node. */
    data class Key(
        val node: String,
        val axis: String,   // yaw | pitch | roll | rise | sway
        val times: FloatArray,
        val values: FloatArray
    )

    data class Clip(
        val id: String,
        val label: String,
        val duration: Float,
        val loop: Boolean,
        val keys: List<Key>
    )

    private fun key(node: String, axis: String, vararg pairs: Pair<Float, Float>) = Key(
        node = node,
        axis = axis,
        times = pairs.map { it.first }.toFloatArray(),
        values = pairs.map { it.second }.toFloatArray()
    )

    private fun clip(id: String, label: String, duration: Float, loop: Boolean, keys: List<Key>) =
        Clip(id, label, duration, loop, keys)

    val animations: List<Clip> = listOf(
        clip("idle", "Idle", 3.0f, true, listOf(
            key("torso", "pitch", 0f to 0f, 1.5f to 0.02f, 3f to 0f),
            key("head", "yaw", 0f to 0f, 1.0f to 0.06f, 2.0f to -0.05f, 3f to 0f))),
        clip("breathe", "Breathe", 2.4f, true, listOf(
            key("torso", "rise", 0f to 0f, 1.2f to 0.012f, 2.4f to 0f))),
        clip("wave", "Wave", 1.6f, false, listOf(
            key("torso", "roll", 0f to 0f, 0.4f to 0.08f, 1.6f to 0f),
            key("head", "roll", 0f to 0f, 0.5f to -0.1f, 1.6f to 0f))),
        clip("nod", "Nod", 1.0f, false, listOf(
            key("head", "pitch", 0f to 0f, 0.35f to 0.18f, 0.7f to -0.05f, 1f to 0f))),
        clip("shake-head", "Shake head", 1.2f, false, listOf(
            key("head", "yaw", 0f to 0f, 0.3f to 0.2f, 0.6f to -0.2f, 0.9f to 0.15f, 1.2f to 0f))),
        clip("look-up", "Look up", 1.0f, false, listOf(
            key("head", "pitch", 0f to 0f, 0.5f to -0.22f, 1f to 0f))),
        clip("look-down", "Look down", 1.0f, false, listOf(
            key("head", "pitch", 0f to 0f, 0.5f to 0.2f, 1f to 0f))),
        clip("look-left", "Look left", 1.0f, false, listOf(
            key("head", "yaw", 0f to 0f, 0.5f to 0.3f, 1f to 0f))),
        clip("look-right", "Look right", 1.0f, false, listOf(
            key("head", "yaw", 0f to 0f, 0.5f to -0.3f, 1f to 0f))),
        clip("turn-left", "Turn left", 1.4f, false, listOf(
            key("root", "yaw", 0f to 0f, 0.7f to 0.7f, 1.4f to 0f))),
        clip("turn-right", "Turn right", 1.4f, false, listOf(
            key("root", "yaw", 0f to 0f, 0.7f to -0.7f, 1.4f to 0f))),
        clip("spin", "Spin", 2.2f, false, listOf(
            key("root", "yaw", 0f to 0f, 2.2f to 6.2831f))),
        clip("bow", "Bow", 1.8f, false, listOf(
            key("torso", "pitch", 0f to 0f, 0.8f to 0.45f, 1.8f to 0f))),
        clip("lean-left", "Lean left", 1.6f, false, listOf(
            key("torso", "roll", 0f to 0f, 0.8f to 0.22f, 1.6f to 0f))),
        clip("lean-right", "Lean right", 1.6f, false, listOf(
            key("torso", "roll", 0f to 0f, 0.8f to -0.22f, 1.6f to 0f))),
        clip("sway", "Sway", 4.0f, true, listOf(
            key("torso", "sway", 0f to 0f, 2f to 0.05f, 4f to 0f))),
        clip("stretch", "Stretch", 2.6f, false, listOf(
            key("root", "rise", 0f to 0f, 1.3f to 0.05f, 2.6f to 0f),
            key("head", "pitch", 0f to 0f, 1.3f to -0.18f, 2.6f to 0f))),
        clip("crouch", "Crouch", 2.0f, false, listOf(
            key("root", "rise", 0f to 0f, 1.0f to -0.18f, 2f to 0f))),
        clip("jump", "Jump", 1.6f, false, listOf(
            key("root", "rise", 0f to 0f, 0.4f to -0.06f, 0.8f to 0.22f, 1.2f to 0f, 1.6f to 0f))),
        clip("float", "Float", 5.0f, true, listOf(
            key("root", "rise", 0f to 0f, 2.5f to 0.09f, 5f to 0f))),
        clip("walk-in-place", "Walk in place", 1.6f, true, listOf(
            key("torso", "roll", 0f to 0f, 0.4f to 0.05f, 0.8f to 0f, 1.2f to -0.05f, 1.6f to 0f),
            key("head", "yaw", 0f to 0f, 0.8f to 0.05f, 1.6f to -0.05f))),
        clip("run-in-place", "Run in place", 1.0f, true, listOf(
            key("torso", "roll", 0f to 0f, 0.25f to 0.09f, 0.5f to 0f, 0.75f to -0.09f, 1f to 0f),
            key("root", "rise", 0f to 0f, 0.25f to 0.03f, 0.5f to 0f, 0.75f to 0.03f, 1f to 0f))),
        clip("dance-1", "Dance - step", 2.0f, true, listOf(
            key("torso", "sway", 0f to 0f, 0.5f to 0.12f, 1f to 0f, 1.5f to -0.12f, 2f to 0f),
            key("head", "roll", 0f to 0f, 0.5f to 0.1f, 1f to 0f, 1.5f to -0.1f, 2f to 0f))),
        clip("dance-2", "Dance - pulse", 1.2f, true, listOf(
            key("root", "rise", 0f to 0f, 0.3f to 0.05f, 0.6f to 0f, 0.9f to 0.05f, 1.2f to 0f),
            key("torso", "roll", 0f to 0f, 0.3f to -0.08f, 0.6f to 0f, 0.9f to 0.08f, 1.2f to 0f))),
        clip("dance-3", "Dance - spin sway", 2.4f, true, listOf(
            key("root", "yaw", 0f to 0f, 1.2f to 0.5f, 2.4f to 0f),
            key("torso", "sway", 0f to 0f, 1.2f to 0.1f, 2.4f to 0f))),
        clip("salute", "Salute", 1.4f, false, listOf(
            key("head", "pitch", 0f to 0f, 0.6f to -0.05f, 1.4f to 0f),
            key("torso", "roll", 0f to 0f, 0.6f to 0.06f, 1.4f to 0f))),
        clip("think", "Think", 2.2f, false, listOf(
            key("head", "yaw", 0f to 0f, 1.1f to 0.22f, 2.2f to 0f),
            key("head", "roll", 0f to 0f, 1.1f to 0.12f, 2.2f to 0f))),
        clip("laugh", "Laugh", 1.2f, false, listOf(
            key("torso", "pitch", 0f to 0f, 0.3f to -0.06f, 0.6f to 0f, 0.9f to -0.06f, 1.2f to 0f),
            key("head", "pitch", 0f to 0f, 0.3f to -0.14f, 0.9f to -0.06f, 1.2f to 0f))),
        clip("cross-arms", "Cross arms", 1.4f, false, listOf(
            key("torso", "roll", 0f to 0f, 0.7f to 0.03f, 1.4f to 0f))),
        clip("point", "Point", 1.4f, false, listOf(
            key("torso", "yaw", 0f to 0f, 0.7f to 0.12f, 1.4f to 0f),
            key("head", "yaw", 0f to 0f, 0.7f to 0.1f, 1.4f to 0f)))
    )

    // ---------------- poses: 30, each a clip evaluated at a chosen instant ----------------

    data class Pose(val id: String, val label: String, val clipId: String, val at: Float)

    val poses: List<Pose> = listOf(
        Pose("rest", "Rest", "idle", 0f),
        Pose("relaxed", "Relaxed", "idle", 1.5f),
        Pose("attentive", "Attentive", "idle", 1.0f),
        Pose("exhale", "Exhale", "breathe", 0f),
        Pose("inhale", "Inhale", "breathe", 1.2f),
        Pose("greeting", "Greeting", "wave", 0.5f),
        Pose("mid-wave", "Mid wave", "wave", 0.9f),
        Pose("agree", "Agree", "nod", 0.35f),
        Pose("disagree", "Disagree", "shake-head", 0.3f),
        Pose("upward", "Looking up", "look-up", 0.5f),
        Pose("downcast", "Downcast", "look-down", 0.5f),
        Pose("left", "Looking left", "look-left", 0.5f),
        Pose("right", "Looking right", "look-right", 0.5f),
        Pose("quarter-turn", "Quarter turn", "turn-left", 0.7f),
        Pose("half-turn", "Half turn", "turn-left", 1.05f),
        Pose("full-turn", "Full turn", "spin", 2.0f),
        Pose("bowing", "Bowing", "bow", 0.8f),
        Pose("leaning-left", "Leaning left", "lean-left", 0.8f),
        Pose("leaning-right", "Leaning right", "lean-right", 0.8f),
        Pose("mid-sway", "Mid sway", "sway", 2f),
        Pose("reaching", "Reaching", "stretch", 1.3f),
        Pose("crouched", "Crouched", "crouch", 1.0f),
        Pose("mid-air", "Mid air", "jump", 0.8f),
        Pose("hovering", "Hovering", "float", 2.5f),
        Pose("stride", "Stride", "walk-in-place", 0.4f),
        Pose("sprint", "Sprint", "run-in-place", 0.25f),
        Pose("groove", "Groove", "dance-1", 0.5f),
        Pose("beat", "Beat", "dance-2", 0.3f),
        Pose("saluting", "Saluting", "salute", 0.6f),
        Pose("laughing", "Laughing", "laugh", 0.3f)
    )

    // ---------------- textures: 12 procedural pattern generators ----------------

    enum class TexturePattern(val id: String, val label: String) {
        FLAT("flat", "Flat"),
        PORE("pore", "Skin pores"),
        WEAVE("weave", "Fabric weave"),
        BRUSHED("brushed", "Brushed metal"),
        CIRCUIT("circuit", "Circuit traces"),
        CAMO("camo", "Camouflage"),
        STRIPES("stripes", "Stripes"),
        CHECKER("checker", "Checker"),
        HEX("hex", "Hex grid"),
        SCALES("scales", "Scales"),
        GRADIENT("gradient", "Gradient"),
        NOISE("noise", "Noise")
    }

    /** Totals, reported in the UI so the library size is visible. */
    data class Totals(
        val skins: Int, val bodies: Int, val faces: Int, val hair: Int,
        val outfits: Int, val accessories: Int, val animations: Int,
        val poses: Int, val textures: Int
    ) {
        val all: Int get() = skins + bodies + faces + hair + outfits + accessories + animations + poses + textures
    }

    val totals: Totals
        get() = Totals(
            skins = skins.size,
            bodies = bodies.size,
            faces = faces.size,
            hair = hair.size,
            outfits = outfits.size,
            accessories = accessories.size,
            animations = animations.size,
            poses = poses.size,
            textures = TexturePattern.entries.size
        )

    fun hairById(id: String?): Hair? = hair.firstOrNull { it.id == id }
    fun outfitById(id: String?): Outfit? = outfits.firstOrNull { it.id == id }
    fun accessoryById(id: String?): Accessory? = accessories.firstOrNull { it.id == id }
    fun faceById(id: String?): Face? = faces.firstOrNull { it.id == id }
    fun skinById(id: String?): Skin? = skins.firstOrNull { it.id == id }
    fun bodyById(id: String?): Body? = bodies.firstOrNull { it.id == id }
    fun clipById(id: String?): Clip? = animations.firstOrNull { it.id == id }
}

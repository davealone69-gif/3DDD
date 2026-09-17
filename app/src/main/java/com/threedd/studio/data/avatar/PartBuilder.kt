package com.threedd.studio.data.avatar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the wearables and body add-ons: hair, eyewear, jewellery, augments, outfits and
 * tattoos, as real triangle geometry.
 *
 * Everything is derived from the rig's proportions, so a part fits any avatar the studio can
 * load rather than being authored against one specific mesh. Pure Kotlin - no engine or
 * bitmap types - so the geometry is unit testable.
 */
object PartBuilder {

    /**
     * Proportions of the loaded avatar, in the model's own units. Head, neck, shoulder,
     * torso and hip positions are derived from the bounding box, which works for both the
     * generated rigs and an imported character.
     */
    data class RigMetrics(
        val minX: Float, val minY: Float, val minZ: Float,
        val maxX: Float, val maxY: Float, val maxZ: Float,
        val headRadius: Float,
        val headCenterY: Float,
        val neckY: Float,
        val shoulderY: Float,
        val shoulderHalfWidth: Float,
        val torsoCenterY: Float,
        val torsoHalfWidth: Float,
        val torsoHalfDepth: Float,
        val hipY: Float,
        val hipHalfWidth: Float,
        val armSpan: Float
    ) {
        companion object {
            /**
             * Derives proportions from a bounding box. The fractions match humanoid
             * proportions closely enough that parts sit correctly on any rig.
             */
            fun fromBounds(bounds: FloatArray): RigMetrics {
                val minX = bounds[0]; val minY = bounds[1]; val minZ = bounds[2]
                val maxX = bounds[3]; val maxY = bounds[4]; val maxZ = bounds[5]
                val height = (maxY - minY).coerceAtLeast(0.1f)
                val halfWidth = ((maxX - minX) / 2f).coerceAtLeast(0.05f)
                val halfDepth = ((maxZ - minZ) / 2f).coerceAtLeast(0.05f)
                val headR = height * 0.075f
                return RigMetrics(
                    minX = minX, minY = minY, minZ = minZ,
                    maxX = maxX, maxY = maxY, maxZ = maxZ,
                    headRadius = headR,
                    headCenterY = maxY - headR * 1.35f,
                    neckY = maxY - headR * 2.6f,
                    shoulderY = maxY - height * 0.22f,
                    shoulderHalfWidth = halfWidth * 0.72f,
                    torsoCenterY = minY + height * 0.62f,
                    torsoHalfWidth = halfWidth * 0.52f,
                    torsoHalfDepth = halfDepth * 0.78f,
                    hipY = minY + height * 0.47f,
                    hipHalfWidth = halfWidth * 0.5f,
                    armSpan = halfWidth
                )
            }
        }
    }

    // ---- category entry points ----

    fun hair(styleId: String, m: RigMetrics): TriangleMesh? {
        val style = AppearanceSpec.HairStyle.of(styleId)
        val acc = MeshAccumulator()
        val cx = 0f
        val cz = 0f
        val r = m.headRadius * 1.06f

        when (style) {
            AppearanceSpec.HairStyle.BALD -> return null
            AppearanceSpec.HairStyle.SHORT -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.06f, r * 1.05f, 0f, 0.62f)
                acc.partialEllipsoid(cx, m.headCenterY, cz + r * 0.35f, r * 0.95f, r * 0.9f, r * 0.6f, 0.3f, 0.75f)
            }
            AppearanceSpec.HairStyle.BOB -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.1f, r * 1.08f, 0f, 0.95f)
            }
            AppearanceSpec.HairStyle.LONG -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.12f, r * 1.1f, 0f, 1.35f)
                acc.partialEllipsoid(cx, m.neckY - m.headRadius * 0.9f, cz - r * 0.3f, r * 1.15f, r * 1.5f, r * 0.55f, 0.25f, 1.1f)
            }
            AppearanceSpec.HairStyle.PONYTAIL -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.05f, r * 1.05f, 0f, 0.7f)
                acc.capsule(
                    floatArrayOf(0f, m.headCenterY + r * 0.35f, -r * 0.75f),
                    floatArrayOf(0f, m.headCenterY - r * 2.4f, -r * 1.5f),
                    r * 0.34f
                )
            }
            AppearanceSpec.HairStyle.MOHAWK -> {
                acc.box(
                    floatArrayOf(0f, m.headCenterY + r * 0.75f, 0f),
                    floatArrayOf(r * 0.16f, r * 0.62f, r * 1.15f)
                )
            }
            AppearanceSpec.HairStyle.CURLY -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.08f, r * 1.05f, 0f, 0.95f)
                val blobs = 10
                for (i in 0 until blobs) {
                    val a = PI * i / (blobs - 1)
                    acc.ellipsoid(
                        (cos(a) * r * 1.05f).toFloat(),
                        (m.headCenterY + sin(a) * r * 0.9f).toFloat(),
                        (sin(a * 2f) * r * 0.35f).toFloat(),
                        r * 0.30f, r * 0.30f, r * 0.30f
                    )
                }
            }
        }
        return acc.build()
    }

    /** Irises drawn in front of the rig's own eye geometry. */
    fun eyes(eyeColorUnused: String, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val r = m.headRadius
        val eyeOffsetX = r * 0.38f
        val eyeY = m.headCenterY + r * 0.12f
        val eyeZ = r * 0.92f
        for (side in intArrayOf(-1, 1)) {
            acc.ellipsoid(
                side * eyeOffsetX, eyeY, eyeZ,
                r * 0.13f, r * 0.13f, r * 0.06f, seg = 12, rings = 8
            )
        }
        return acc.build()
    }

    fun accessory(id: String, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val r = m.headRadius
        val eyeY = m.headCenterY + r * 0.12f
        val eyeZ = r * 0.92f
        when (AppearanceSpec.Accessory.of(id)) {
            AppearanceSpec.Accessory.NONE -> return null
            AppearanceSpec.Accessory.GLASSES -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.torus(side * r * 0.38f, eyeY, eyeZ, r * 0.24f, r * 0.035f)
                }
                acc.box(floatArrayOf(0f, eyeY, eyeZ), floatArrayOf(r * 0.16f, r * 0.02f, r * 0.02f))
            }
            AppearanceSpec.Accessory.VISOR -> {
                acc.partialEllipsoid(0f, eyeY + r * 0.05f, r * 0.1f, r * 1.02f, r * 0.34f, r * 1.0f, 0.32f, 0.52f)
            }
            AppearanceSpec.Accessory.EARRINGS -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.ellipsoid(side * r * 0.95f, m.headCenterY - r * 0.05f, 0f, r * 0.09f, r * 0.14f, r * 0.09f, seg = 10, rings = 8)
                }
            }
            AppearanceSpec.Accessory.COLLAR -> {
                acc.torus(0f, m.neckY + m.headRadius * 0.35f, 0f, r * 0.52f, r * 0.09f, seg = 20)
            }
            AppearanceSpec.Accessory.CROWN -> {
                acc.torus(0f, m.headCenterY + r * 0.78f, 0f, r * 0.72f, r * 0.05f, seg = 22)
                for (i in 0 until 6) {
                    val a = 2.0 * PI * i / 6
                    acc.box(
                        floatArrayOf((cos(a) * r * 0.72f).toFloat(), m.headCenterY + r * 0.94f, (sin(a) * r * 0.72f).toFloat()),
                        floatArrayOf(r * 0.05f, r * 0.16f, r * 0.05f)
                    )
                }
            }
        }
        return acc.build()
    }

    fun augment(id: String, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val r = m.headRadius
        when (AppearanceSpec.Augment.of(id)) {
            AppearanceSpec.Augment.NONE -> return null
            AppearanceSpec.Augment.SHOULDER_PLATE -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.ellipsoid(
                        side * m.shoulderHalfWidth, m.shoulderY + m.headRadius * 0.15f, 0f,
                        m.shoulderHalfWidth * 0.42f, m.headRadius * 0.55f, m.torsoHalfDepth * 0.75f,
                        seg = 14, rings = 10
                    )
                }
            }
            AppearanceSpec.Augment.ARM_CASING -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.capsule(
                        floatArrayOf(side * m.shoulderHalfWidth * 0.95f, m.shoulderY, 0f),
                        floatArrayOf(side * m.armSpan * 0.92f, m.hipY + m.headRadius * 0.4f, 0f),
                        m.headRadius * 0.30f
                    )
                }
            }
            AppearanceSpec.Augment.SPINE_RIG -> {
                var y = m.shoulderY - m.headRadius * 0.3f
                while (y > m.hipY) {
                    acc.box(
                        floatArrayOf(0f, y, -m.torsoHalfDepth * 0.92f),
                        floatArrayOf(m.headRadius * 0.22f, m.headRadius * 0.10f, m.headRadius * 0.10f)
                    )
                    y -= m.headRadius * 0.42f
                }
            }
            AppearanceSpec.Augment.HEAD_IMPLANT -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.box(
                        floatArrayOf(side * r * 0.92f, m.headCenterY + r * 0.22f, 0f),
                        floatArrayOf(r * 0.06f, r * 0.16f, r * 0.26f)
                    )
                }
            }
        }
        return acc.build()
    }

    fun outfit(id: String, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        when (AppearanceSpec.Outfit.of(id)) {
            AppearanceSpec.Outfit.NONE -> return null
            AppearanceSpec.Outfit.BODYSUIT -> {
                acc.partialEllipsoid(0f, m.torsoCenterY, 0f,
                    m.torsoHalfWidth * 1.12f, (m.shoulderY - m.hipY) * 0.62f, m.torsoHalfDepth * 1.1f, 0f, 1.0f)
                for (side in intArrayOf(-1, 1)) {
                    acc.capsule(
                        floatArrayOf(side * m.hipHalfWidth * 0.58f, m.hipY, 0f),
                        floatArrayOf(side * m.hipHalfWidth * 0.62f, m.minY + (m.maxY - m.minY) * 0.05f, 0f),
                        m.torsoHalfWidth * 0.30f
                    )
                }
            }
            AppearanceSpec.Outfit.DRESS -> {
                acc.partialEllipsoid(0f, m.torsoCenterY, 0f,
                    m.torsoHalfWidth * 1.1f, (m.shoulderY - m.hipY) * 0.6f, m.torsoHalfDepth * 1.08f, 0f, 1.0f)
                acc.cone(
                    floatArrayOf(0f, m.hipY, 0f),
                    m.torsoHalfWidth * 1.05f,
                    m.torsoHalfWidth * 1.75f,
                    (m.maxY - m.minY) * 0.22f
                )
            }
            AppearanceSpec.Outfit.JACKET -> {
                acc.partialEllipsoid(0f, m.torsoCenterY + m.headRadius * 0.15f, 0f,
                    m.torsoHalfWidth * 1.18f, (m.shoulderY - m.hipY) * 0.56f, m.torsoHalfDepth * 1.16f, 0f, 0.95f)
                for (side in intArrayOf(-1, 1)) {
                    acc.ellipsoid(
                        side * m.shoulderHalfWidth, m.shoulderY + m.headRadius * 0.1f, 0f,
                        m.shoulderHalfWidth * 0.34f, m.headRadius * 0.42f, m.torsoHalfDepth * 0.68f,
                        seg = 12, rings = 8
                    )
                }
            }
            AppearanceSpec.Outfit.ARMOUR -> {
                acc.partialEllipsoid(0f, m.torsoCenterY, 0f,
                    m.torsoHalfWidth * 1.22f, (m.shoulderY - m.hipY) * 0.6f, m.torsoHalfDepth * 1.2f, 0f, 1.0f)
                acc.box(floatArrayOf(0f, m.torsoCenterY + m.headRadius * 0.2f, m.torsoHalfDepth * 1.18f),
                    floatArrayOf(m.torsoHalfWidth * 1.0f, m.headRadius * 0.5f, m.headRadius * 0.12f))
                for (side in intArrayOf(-1, 1)) {
                    acc.ellipsoid(
                        side * m.shoulderHalfWidth * 1.05f, m.shoulderY + m.headRadius * 0.18f, 0f,
                        m.shoulderHalfWidth * 0.46f, m.headRadius * 0.55f, m.torsoHalfDepth * 0.8f,
                        seg = 14, rings = 10
                    )
                }
            }
        }
        return acc.build()
    }

    fun tattoo(id: String, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        when (AppearanceSpec.Tattoo.of(id)) {
            AppearanceSpec.Tattoo.NONE -> return null
            AppearanceSpec.Tattoo.SLEEVE -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.capsule(
                        floatArrayOf(side * m.shoulderHalfWidth * 0.9f, m.shoulderY - m.headRadius * 0.2f, 0f),
                        floatArrayOf(side * m.armSpan * 0.86f, m.hipY + m.headRadius * 0.7f, 0f),
                        m.headRadius * 0.235f
                    )
                }
            }
            AppearanceSpec.Tattoo.BACK -> {
                acc.box(floatArrayOf(0f, m.torsoCenterY + m.headRadius * 0.2f, -m.torsoHalfDepth * 1.06f),
                    floatArrayOf(m.torsoHalfWidth * 0.72f, (m.shoulderY - m.hipY) * 0.38f, m.headRadius * 0.04f))
            }
            AppearanceSpec.Tattoo.CHEST -> {
                acc.box(floatArrayOf(0f, m.torsoCenterY + m.headRadius * 0.25f, m.torsoHalfDepth * 1.04f),
                    floatArrayOf(m.torsoHalfWidth * 0.5f, m.headRadius * 0.42f, m.headRadius * 0.04f))
            }
            AppearanceSpec.Tattoo.CIRCUIT -> {
                var y = m.shoulderY - m.headRadius * 0.4f
                var i = 0
                while (y > m.hipY) {
                    val w = if (i % 2 == 0) m.torsoHalfWidth * 0.78f else m.torsoHalfWidth * 0.44f
                    acc.box(floatArrayOf(0f, y, m.torsoHalfDepth * 1.03f),
                        floatArrayOf(w, m.headRadius * 0.045f, m.headRadius * 0.03f))
                    y -= m.headRadius * 0.5f
                    i++
                }
            }
        }
        return acc.build()
    }


    // ---- content-library variants: one builder per catalog entry ----

    /** 100 hairstyles: base shape, volume and fringe combine into distinct geometry. */
    fun hair(spec: ContentCatalog.Hair, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val r = m.headRadius * (1.02f + spec.volume * 0.16f)
        val cx = 0f
        val cz = 0f
        when (spec.base) {
            ContentCatalog.HairBase.SHAVED -> return null
            ContentCatalog.HairBase.CROP ->
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.04f, r * 1.03f, 0f, 0.58f)
            ContentCatalog.HairBase.BOB ->
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.06f, r * 1.05f, 0f, 0.98f)
            ContentCatalog.HairBase.MIDI ->
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.08f, r * 1.06f, 0f, 1.18f)
            ContentCatalog.HairBase.LONG -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.1f, r * 1.08f, 0f, 1.35f)
                acc.partialEllipsoid(cx, m.neckY - m.headRadius * 0.8f, cz - r * 0.28f,
                    r * 1.1f, r * 1.45f, r * 0.6f, 0.25f, 1.1f)
            }
            ContentCatalog.HairBase.PONYTAIL -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.04f, r * 1.04f, 0f, 0.66f)
                acc.capsule(
                    floatArrayOf(0f, m.headCenterY + r * 0.3f, -r * 0.72f),
                    floatArrayOf(0f, m.headCenterY - r * (2.0f + spec.volume), -r * 1.4f),
                    r * (0.28f + spec.volume * 0.1f)
                )
            }
            ContentCatalog.HairBase.BUN -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.04f, r * 1.04f, 0f, 0.72f)
                acc.ellipsoid(cx, m.headCenterY + r * 1.0f, -r * 0.35f,
                    r * (0.42f + spec.volume * 0.14f), r * 0.4f, r * 0.4f, seg = 12, rings = 9)
            }
            ContentCatalog.HairBase.TWINTAILS -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.04f, r * 1.04f, 0f, 0.8f)
                for (side in intArrayOf(-1, 1)) {
                    acc.capsule(
                        floatArrayOf(side * r * 0.9f, m.headCenterY + r * 0.15f, -r * 0.3f),
                        floatArrayOf(side * r * (1.3f + spec.volume), m.neckY, -r * 0.6f),
                        r * (0.24f + spec.volume * 0.1f)
                    )
                }
            }
            ContentCatalog.HairBase.BRAIDS -> {
                acc.partialEllipsoid(cx, m.headCenterY, cz, r, r * 1.05f, r * 1.05f, 0f, 1.05f)
                for (side in intArrayOf(-1, 1)) {
                    var y = m.neckY + m.headRadius * 0.2f
                    var step = 0
                    while (step < 6) {
                        acc.ellipsoid(side * r * 0.85f, y, -r * 0.5f, r * 0.2f, r * 0.16f, r * 0.2f, seg = 8, rings = 6)
                        y -= r * 0.3f
                        step++
                    }
                }
            }
            ContentCatalog.HairBase.MOHAWK -> {
                acc.box(floatArrayOf(0f, m.headCenterY + r * 0.72f, 0f),
                    floatArrayOf(r * (0.12f + spec.volume * 0.1f), r * (0.5f + spec.volume * 0.35f), r * 1.12f))
            }
        }
        // fringe adds geometry in front of the brow
        if (spec.fringe != ContentCatalog.HairFringe.NONE && spec.base != ContentCatalog.HairBase.SHAVED) {
            val y = m.headCenterY + r * 0.72f
            val z = r * 0.86f
            when (spec.fringe) {
                ContentCatalog.HairFringe.STRAIGHT ->
                    acc.box(floatArrayOf(0f, y, z), floatArrayOf(r * 0.86f, r * 0.3f, r * 0.16f))
                ContentCatalog.HairFringe.SIDE ->
                    acc.box(floatArrayOf(r * 0.34f, y - r * 0.16f, z), floatArrayOf(r * 0.6f, r * 0.5f, r * 0.16f))
                ContentCatalog.HairFringe.SWEPT ->
                    acc.box(floatArrayOf(-r * 0.2f, y - r * 0.1f, z), floatArrayOf(r * 0.72f, r * 0.36f, r * 0.15f))
                ContentCatalog.HairFringe.CURTAIN -> {
                    for (side in intArrayOf(-1, 1)) {
                        acc.box(floatArrayOf(side * r * 0.62f, y - r * 0.3f, z * 0.94f),
                            floatArrayOf(r * 0.24f, r * 0.62f, r * 0.14f))
                    }
                }
                ContentCatalog.HairFringe.NONE -> Unit
            }
        }
        return acc.build()
    }

    /** 200 outfits: garment, sleeve length, hem and trim all contribute geometry. */
    fun outfit(spec: ContentCatalog.Outfit, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val torsoHalf = (m.shoulderY - m.hipY) * 0.6f
        val hemScale = if (spec.length.toHip) 1.0f else 0.78f
        val garmentScale = when (spec.garment) {
            ContentCatalog.Garment.TUNIC -> 1.06f
            ContentCatalog.Garment.BODYSUIT -> 1.02f
            ContentCatalog.Garment.DRESS -> 1.08f
            ContentCatalog.Garment.JACKET -> 1.14f
            ContentCatalog.Garment.ARMOUR -> 1.2f
        }

        acc.partialEllipsoid(0f, m.torsoCenterY, 0f,
            m.torsoHalfWidth * garmentScale, torsoHalf * hemScale, m.torsoHalfDepth * garmentScale,
            0f, 1.0f)

        if (spec.garment == ContentCatalog.Garment.DRESS || spec.garment == ContentCatalog.Garment.TUNIC) {
            acc.cone(floatArrayOf(0f, m.hipY, 0f),
                m.torsoHalfWidth * 1.05f,
                m.torsoHalfWidth * (if (spec.length.toHip) 1.7f else 1.35f),
                (m.maxY - m.minY) * 0.2f)
        }
        if (spec.garment == ContentCatalog.Garment.ARMOUR) {
            acc.box(floatArrayOf(0f, m.torsoCenterY + m.headRadius * 0.2f, m.torsoHalfDepth * 1.16f),
                floatArrayOf(m.torsoHalfWidth * 0.98f, m.headRadius * 0.48f, m.headRadius * 0.12f))
            for (side in intArrayOf(-1, 1)) {
                acc.ellipsoid(side * m.shoulderHalfWidth, m.shoulderY + m.headRadius * 0.16f, 0f,
                    m.shoulderHalfWidth * 0.44f, m.headRadius * 0.52f, m.torsoHalfDepth * 0.78f, seg = 12, rings = 9)
            }
        }
        if (spec.sleeve != ContentCatalog.Sleeve.NONE) {
            val startY = m.shoulderY
            val endY = startY - (startY - (m.hipY + m.headRadius * 0.6f)) * spec.sleeve.length
            for (side in intArrayOf(-1, 1)) {
                acc.capsule(
                    floatArrayOf(side * m.shoulderHalfWidth * 0.95f, startY, 0f),
                    floatArrayOf(side * (m.armSpan * 0.88f), endY, 0f),
                    m.headRadius * 0.3f
                )
            }
        }
        if (spec.trim != ContentCatalog.Trim.PLAIN) {
            val r = m.headRadius * (if (spec.trim == ContentCatalog.Trim.STUDS) 0.07f else 0.12f)
            var y = m.shoulderY - m.headRadius * 0.5f
            var i = 0
            while (y > m.hipY && i < 5) {
                acc.torus(0f, y, 0f, m.torsoHalfWidth * garmentScale * 0.98f, r, seg = 18)
                y -= m.headRadius * 0.7f
                i++
            }
        }
        return acc.build()
    }

    /** 100 accessories: kind chooses the shape, the gem adds a stone. */
    fun accessory(spec: ContentCatalog.Accessory, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val r = m.headRadius
        val eyeY = m.headCenterY + r * 0.12f
        val eyeZ = r * 0.92f
        when (spec.kind) {
            ContentCatalog.AccessoryKind.GLASSES -> {
                for (side in intArrayOf(-1, 1)) acc.torus(side * r * 0.38f, eyeY, eyeZ, r * 0.24f, r * 0.035f)
                acc.box(floatArrayOf(0f, eyeY, eyeZ), floatArrayOf(r * 0.16f, r * 0.02f, r * 0.02f))
            }
            ContentCatalog.AccessoryKind.VISOR ->
                acc.partialEllipsoid(0f, eyeY + r * 0.05f, r * 0.1f, r * 1.02f, r * 0.34f, r * 1.0f, 0.32f, 0.52f)
            ContentCatalog.AccessoryKind.MASK ->
                acc.partialEllipsoid(0f, m.headCenterY, r * 0.06f, r * 1.02f, r * 0.72f, r * 0.9f, 0.42f, 1.0f)
            ContentCatalog.AccessoryKind.EARRINGS -> {
                for (side in intArrayOf(-1, 1))
                    acc.ellipsoid(side * r * 0.95f, m.headCenterY - r * 0.05f, 0f, r * 0.09f, r * 0.14f, r * 0.09f, seg = 10, rings = 8)
            }
            ContentCatalog.AccessoryKind.NECKLACE -> {
                acc.torus(0f, m.neckY, 0f, r * 0.5f, r * 0.05f, seg = 18)
                acc.ellipsoid(0f, m.neckY - r * 0.42f, r * 0.3f, r * 0.1f, r * 0.12f, r * 0.05f, seg = 10, rings = 8)
            }
            ContentCatalog.AccessoryKind.CROWN -> {
                acc.torus(0f, m.headCenterY + r * 0.78f, 0f, r * 0.72f, r * 0.05f, seg = 22)
                for (i in 0 until 6) {
                    val a = 2.0 * PI * i / 6
                    acc.box(floatArrayOf((cos(a) * r * 0.72f).toFloat(), m.headCenterY + r * 0.94f, (sin(a) * r * 0.72f).toFloat()),
                        floatArrayOf(r * 0.05f, r * 0.16f, r * 0.05f))
                }
            }
            ContentCatalog.AccessoryKind.HORNS -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.capsule(
                        floatArrayOf(side * r * 0.55f, m.headCenterY + r * 0.7f, 0f),
                        floatArrayOf(side * r * 0.95f, m.headCenterY + r * 1.7f, -r * 0.35f),
                        r * 0.1f
                    )
                }
            }
            ContentCatalog.AccessoryKind.WINGS -> {
                for (side in intArrayOf(-1, 1)) {
                    acc.box(floatArrayOf(side * m.shoulderHalfWidth * 1.5f, m.shoulderY + m.headRadius * 0.3f, -m.torsoHalfDepth * 0.9f),
                        floatArrayOf(m.shoulderHalfWidth * 0.85f, m.headRadius * 0.9f, m.headRadius * 0.06f))
                }
            }
            ContentCatalog.AccessoryKind.BELT -> {
                acc.torus(0f, m.hipY + m.headRadius * 0.1f, 0f, m.hipHalfWidth * 1.06f, m.headRadius * 0.09f, seg = 20)
                acc.box(floatArrayOf(0f, m.hipY + m.headRadius * 0.1f, m.torsoHalfDepth * 1.02f),
                    floatArrayOf(m.headRadius * 0.16f, m.headRadius * 0.14f, m.headRadius * 0.05f))
            }
            ContentCatalog.AccessoryKind.BRACELET -> {
                for (side in intArrayOf(-1, 1))
                    acc.torus(side * m.armSpan * 0.82f, m.hipY + m.headRadius * 1.1f, 0f, m.headRadius * 0.24f, m.headRadius * 0.06f, seg = 14)
            }
        }
        if (spec.gem != ContentCatalog.Gem.NONE) {
            acc.ellipsoid(0f, m.neckY - m.headRadius * 0.42f, m.headRadius * 0.32f,
                m.headRadius * 0.09f, m.headRadius * 0.09f, m.headRadius * 0.05f, seg = 10, rings = 8)
        }
        return acc.build()
    }

    /** 50 faces: the head is scaled and the irises are sized, spaced and tilted per face. */
    fun faceGeometry(face: ContentCatalog.Face, m: RigMetrics): TriangleMesh? {
        val acc = MeshAccumulator()
        val r = m.headRadius
        val eyeY = m.headCenterY + r * 0.12f
        val eyeZ = r * 0.92f
        val half = r * face.eyeSpacing
        for (side in intArrayOf(-1, 1)) {
            val y = eyeY + side * face.eyeTilt * r * 0.5f
            acc.ellipsoid(
                side * half, y, eyeZ,
                r * 0.13f * face.eyeScale, r * 0.13f * face.eyeScale, r * 0.06f,
                seg = 12, rings = 8
            )
        }
        // brows follow the jaw definition, so a defined jaw reads as a stronger brow
        val browWidth = r * 0.3f * (1f + (face.jaw.width - 1f) * 2f)
        for (side in intArrayOf(-1, 1)) {
            acc.box(
                floatArrayOf(side * half, eyeY + r * (0.3f + face.eyeTilt * 0.3f), eyeZ * 0.96f),
                floatArrayOf(browWidth, r * 0.045f, r * 0.05f)
            )
        }
        return acc.build()
    }

    // ---- primitive accumulation ----

    private class MeshAccumulator {
        private val positions = ArrayList<Float>()
        private val uvs = ArrayList<Float>()
        private val indices = ArrayList<Int>()

        fun build(): TriangleMesh? {
            if (indices.isEmpty()) return null
            val pos = positions.toFloatArray()
            return TriangleMesh(pos, PhotoMeshBuilder.rebuildNormals(pos, indices.toIntArray()), uvs.toFloatArray(), indices.toIntArray())
        }

        private fun addVertex(x: Float, y: Float, z: Float, u: Float, v: Float): Int {
            val index = positions.size / 3
            positions.add(x); positions.add(y); positions.add(z)
            uvs.add(u); uvs.add(v)
            return index
        }

        private fun quad(a: Int, b: Int, c: Int, d: Int) {
            indices.add(a); indices.add(b); indices.add(c)
            indices.add(a); indices.add(c); indices.add(d)
        }

        fun ellipsoid(
            cx: Float, cy: Float, cz: Float,
            rx: Float, ry: Float, rz: Float,
            seg: Int = 14, rings: Int = 10
        ) {
            val grid = ArrayList<Int>(seg * (rings + 1))
            for (ri in 0..rings) {
                val phi = PI * ri / rings
                val sp = sin(phi)
                for (si in 0 until seg) {
                    val th = 2 * PI * si / seg
                    grid.add(
                        addVertex(
                            cx + (sp * cos(th) * rx).toFloat(),
                            cy + (cos(phi) * ry).toFloat(),
                            cz + (sp * sin(th) * rz).toFloat(),
                            si.toFloat() / seg, ri.toFloat() / rings
                        )
                    )
                }
            }
            for (ri in 0 until rings) {
                for (si in 0 until seg) {
                    val s2 = (si + 1) % seg
                    val a = grid[ri * seg + si]
                    val b = grid[ri * seg + s2]
                    val c = grid[(ri + 1) * seg + s2]
                    val d = grid[(ri + 1) * seg + si]
                    quad(a, b, c, d)
                }
            }
        }

        /** Upper cap of an ellipsoid, from the top down to [phiEnd] radians. */
        fun partialEllipsoid(
            cx: Float, cy: Float, cz: Float,
            rx: Float, ry: Float, rz: Float,
            phiStart: Float, phiEnd: Float,
            seg: Int = 16, rings: Int = 8
        ) {
            val grid = ArrayList<Int>(seg * (rings + 1))
            for (ri in 0..rings) {
                val phi = phiStart + (phiEnd - phiStart) * ri / rings
                val sp = sin(phi)
                for (si in 0 until seg) {
                    val th = 2 * PI * si / seg
                    grid.add(
                        addVertex(
                            cx + (sp * cos(th) * rx).toFloat(),
                            cy + (cos(phi) * ry).toFloat(),
                            cz + (sp * sin(th) * rz).toFloat(),
                            si.toFloat() / seg, ri.toFloat() / rings
                        )
                    )
                }
            }
            for (ri in 0 until rings) {
                for (si in 0 until seg) {
                    val s2 = (si + 1) % seg
                    quad(
                        grid[ri * seg + si], grid[ri * seg + s2],
                        grid[(ri + 1) * seg + s2], grid[(ri + 1) * seg + si]
                    )
                }
            }
        }

        fun box(centre: FloatArray, half: FloatArray) {
            val base = ArrayList<Int>(8)
            for (sy in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    for (sx in intArrayOf(-1, 1)) {
                        base.add(
                            addVertex(
                                centre[0] + sx * half[0], centre[1] + sy * half[1], centre[2] + sz * half[2],
                                (sx + 1) / 2f, (sy + 1) / 2f
                            )
                        )
                    }
                }
            }
            // consistent outward winding for each of the six faces
            val faces = arrayOf(
                intArrayOf(1, 3, 7, 5), intArrayOf(0, 4, 6, 2),
                intArrayOf(2, 6, 7, 3), intArrayOf(0, 1, 5, 4),
                intArrayOf(4, 5, 7, 6), intArrayOf(0, 2, 3, 1)
            )
            faces.forEach { f -> quad(base[f[0]], base[f[1]], base[f[2]], base[f[3]]) }
        }

        fun capsule(p0: FloatArray, p1: FloatArray, radius: Float, seg: Int = 12, capRings: Int = 4) {
            val dx = p1[0] - p0[0]; val dy = p1[1] - p0[1]; val dz = p1[2] - p0[2]
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            if (length < 1e-5f) return
            val ax = dx / length; val ay = dy / length; val az = dz / length
            val upX = if (abs(ay) < 0.9f) 0f else 1f
            val upY = if (abs(ay) < 0.9f) 1f else 0f
            var ex = upY * az - 0f * ay
            var ey = 0f * ax - upX * az
            var ez = upX * ay - upY * ax
            val el = sqrt(ex * ex + ey * ey + ez * ez).coerceAtLeast(1e-6f)
            ex /= el; ey /= el; ez /= el
            val fx = ay * ez - az * ey
            val fy = az * ex - ax * ez
            val fz = ax * ey - ay * ex

            val stations = ArrayList<Pair<Float, Float>>()
            for (i in 0..capRings) {
                val a = (PI / 2) * i / capRings
                stations.add(Pair((-radius * cos(a)).toFloat(), (radius * sin(a)).toFloat()))
            }
            stations.add(Pair(0f, radius))
            for (i in 0..capRings) {
                val a = (PI / 2) * i / capRings
                stations.add(Pair((length + radius * sin(a)).toFloat(), (radius * cos(a)).toFloat()))
            }

            val grid = ArrayList<Int>()
            stations.forEachIndexed { si, (s, rr) ->
                val bx = p0[0] + ax * s; val by = p0[1] + ay * s; val bz = p0[2] + az * s
                for (k in 0 until seg) {
                    val th = 2 * PI * k / seg
                    grid.add(
                        addVertex(
                            bx + (ex * cos(th) + fx * sin(th)).toFloat() * rr,
                            by + (ey * cos(th) + fy * sin(th)).toFloat() * rr,
                            bz + (ez * cos(th) + fz * sin(th)).toFloat() * rr,
                            k.toFloat() / seg, si.toFloat() / stations.size
                        )
                    )
                }
            }
            for (si in 0 until stations.size - 1) {
                for (k in 0 until seg) {
                    val k2 = (k + 1) % seg
                    quad(
                        grid[si * seg + k], grid[si * seg + k2],
                        grid[(si + 1) * seg + k2], grid[(si + 1) * seg + k]
                    )
                }
            }
        }

        fun torus(cx: Float, cy: Float, cz: Float, major: Float, minor: Float, seg: Int = 20, ringSeg: Int = 8) {
            val grid = ArrayList<Int>(seg * ringSeg)
            for (i in 0 until seg) {
                val u = 2 * PI * i / seg
                val cu = cos(u).toFloat(); val su = sin(u).toFloat()
                for (j in 0 until ringSeg) {
                    val v = 2 * PI * j / ringSeg
                    val cv = cos(v).toFloat(); val sv = sin(v).toFloat()
                    grid.add(
                        addVertex(
                            cx + (major + minor * cv) * cu,
                            cy + minor * sv,
                            cz + (major + minor * cv) * su,
                            i.toFloat() / seg, j.toFloat() / ringSeg
                        )
                    )
                }
            }
            for (i in 0 until seg) {
                for (j in 0 until ringSeg) {
                    val i2 = (i + 1) % seg
                    val j2 = (j + 1) % ringSeg
                    quad(
                        grid[i * ringSeg + j], grid[i2 * ringSeg + j],
                        grid[i2 * ringSeg + j2], grid[i * ringSeg + j2]
                    )
                }
            }
        }

        fun cone(baseCentre: FloatArray, topRadius: Float, bottomRadius: Float, height: Float, seg: Int = 18) {
            val topY = baseCentre[1] + height
            val topRing = ArrayList<Int>(seg)
            val bottomRing = ArrayList<Int>(seg)
            for (i in 0 until seg) {
                val th = 2 * PI * i / seg
                val cx = cos(th).toFloat(); val cz = sin(th).toFloat()
                topRing.add(addVertex(baseCentre[0] + cx * topRadius, topY, baseCentre[2] + cz * topRadius, i.toFloat() / seg, 0f))
            }
            for (i in 0 until seg) {
                val th = 2 * PI * i / seg
                val cx = cos(th).toFloat(); val cz = sin(th).toFloat()
                bottomRing.add(addVertex(baseCentre[0] + cx * bottomRadius, baseCentre[1], baseCentre[2] + cz * bottomRadius, i.toFloat() / seg, 1f))
            }
            for (i in 0 until seg) {
                val i2 = (i + 1) % seg
                quad(topRing[i], bottomRing[i], bottomRing[i2], topRing[i2])
            }
        }
    }
}

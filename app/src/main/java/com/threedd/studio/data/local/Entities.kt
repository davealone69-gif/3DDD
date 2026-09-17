package com.threedd.studio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "avatar_designs")
data class AvatarDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val modelId: String,
    val bodyPresetId: String,
    val baseColorHex: String,
    val metallic: Float,
    val roughness: Float,
    val reflectance: Float,
    val emissiveHex: String,
    val emissiveIntensity: Float,
    val clearCoat: Float,
    val clearCoatRoughness: Float,
    val environmentIntensity: Float,
    val keyIntensity: Float,
    val fillIntensity: Float,
    val rimIntensity: Float,
    val keyColorHex: String,
    val fillColorHex: String,
    val rimColorHex: String,
    val shadowsEnabled: Boolean,
    val morphWeightsJson: String,
    val mature: Boolean,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "library_models")
data class LibraryModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val source: String,
    val location: String,
    val thumbnailPath: String?,
    val sizeBytes: Long,
    val importedAtEpochMs: Long,
    val license: String,
    val mature: Boolean
)

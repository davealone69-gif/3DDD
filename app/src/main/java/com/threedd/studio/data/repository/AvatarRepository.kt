package com.threedd.studio.data.repository

import com.threedd.studio.data.local.AvatarDesignEntity
import com.threedd.studio.data.local.JsonCodec
import com.threedd.studio.data.local.StudioDao
import com.threedd.studio.data.model.AvatarDesign
import com.threedd.studio.data.model.LightState
import com.threedd.studio.data.model.MaterialState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarRepository @Inject constructor(private val dao: StudioDao) {

    val designs: Flow<List<AvatarDesign>> = dao.observeDesigns().map { list -> list.map { it.toDomain() } }

    suspend fun save(design: AvatarDesign): Long = dao.insertDesign(design.toEntity())

    suspend fun delete(id: Long) = dao.deleteDesign(id)

    private fun AvatarDesignEntity.toDomain() = AvatarDesign(
        id = id,
        name = name,
        modelId = modelId,
        bodyPresetId = bodyPresetId,
        material = MaterialState(
            baseColorHex = baseColorHex,
            metallic = metallic,
            roughness = roughness,
            reflectance = reflectance,
            emissiveHex = emissiveHex,
            emissiveIntensity = emissiveIntensity,
            clearCoat = clearCoat,
            clearCoatRoughness = clearCoatRoughness
        ),
        light = LightState(
            environmentIntensity = environmentIntensity,
            keyIntensity = keyIntensity,
            fillIntensity = fillIntensity,
            rimIntensity = rimIntensity,
            keyColorHex = keyColorHex,
            fillColorHex = fillColorHex,
            rimColorHex = rimColorHex,
            shadowsEnabled = shadowsEnabled
        ),
        morphWeights = JsonCodec.decodeFloats(morphWeightsJson),
        updatedAtEpochMs = updatedAtEpochMs,
        mature = mature
    )

    private fun AvatarDesign.toEntity() = AvatarDesignEntity(
        id = id,
        name = name,
        modelId = modelId,
        bodyPresetId = bodyPresetId,
        baseColorHex = material.baseColorHex,
        metallic = material.metallic,
        roughness = material.roughness,
        reflectance = material.reflectance,
        emissiveHex = material.emissiveHex,
        emissiveIntensity = material.emissiveIntensity,
        clearCoat = material.clearCoat,
        clearCoatRoughness = material.clearCoatRoughness,
        environmentIntensity = light.environmentIntensity,
        keyIntensity = light.keyIntensity,
        fillIntensity = light.fillIntensity,
        rimIntensity = light.rimIntensity,
        keyColorHex = light.keyColorHex,
        fillColorHex = light.fillColorHex,
        rimColorHex = light.rimColorHex,
        shadowsEnabled = light.shadowsEnabled,
        morphWeightsJson = JsonCodec.encodeFloats(morphWeights),
        mature = mature,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

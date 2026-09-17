package com.threedd.studio.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.threedd.studio.data.local.LibraryModelEntity
import com.threedd.studio.data.local.StudioDao
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.model.ModelSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StudioDao
) {

    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    private val scansDir: File get() = File(context.filesDir, "scans").apply { mkdirs() }

    /** Built-in models are procedural rigs; they carry no external file. */
    val builtInModels: List<AvatarModel> = listOf(
        AvatarModel("builtin_female", "Female base model", ModelSource.BUILTIN, "rig://female"),
        AvatarModel("builtin_male", "Male base model", ModelSource.BUILTIN, "rig://male"),
        AvatarModel("builtin_cyborg", "Cyborg base model", ModelSource.BUILTIN, "rig://cyborg"),
        AvatarModel("builtin_androgynous", "Androgynous base model", ModelSource.BUILTIN, "rig://androgynous")
    )

    val importedModels: Flow<List<AvatarModel>> = dao.observeModels().map { rows ->
        rows.map { row ->
            AvatarModel(
                id = row.id,
                displayName = row.displayName,
                source = runCatching { ModelSource.valueOf(row.source) }.getOrDefault(ModelSource.IMPORTED),
                location = row.location,
                thumbnailPath = row.thumbnailPath,
                sizeBytes = row.sizeBytes,
                importedAtEpochMs = row.importedAtEpochMs,
                license = row.license,
                mature = row.mature
            )
        }
    }

    fun findModel(id: String): AvatarModel? = builtInModels.firstOrNull { it.id == id }

    /**
     * Copies a picked glTF/GLB into app storage and registers it in the library.
     * Returns the new model, or null when the source could not be read.
     */
    suspend fun import(uri: Uri, mature: Boolean = false): AvatarModel? = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryDisplayName(uri) ?: "imported-${System.currentTimeMillis()}"
            val extension = name.substringAfterLast('.', "glb").lowercase()
            if (extension !in SUPPORTED_EXTENSIONS) return@runCatching null

            val id = "import_${UUID.randomUUID()}"
            val target = File(modelsDir, "$id.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: return@runCatching null

            // A .gltf side-car .bin must be imported alongside for the asset to load.
            if (extension == "gltf") {
                rewriteGltfBufferUri(target)
            }

            val model = AvatarModel(
                id = id,
                displayName = name.substringBeforeLast('.'),
                source = ModelSource.IMPORTED,
                location = Uri.fromFile(target).toString(),
                sizeBytes = target.length(),
                importedAtEpochMs = System.currentTimeMillis(),
                license = "User supplied",
                mature = mature
            )
            dao.upsertModel(model.toEntity())
            model
        }.getOrNull()
    }

    /** Registers a completed scan session as a first-class model in the library. */
    suspend fun registerScan(id: String, displayName: String, file: File, mature: Boolean = false): AvatarModel {
        val model = AvatarModel(
            id = id,
            displayName = displayName,
            source = ModelSource.SCANNED,
            location = Uri.fromFile(file).toString(),
            sizeBytes = file.length(),
            importedAtEpochMs = System.currentTimeMillis(),
            license = "Generated on device",
            mature = mature
        )
        dao.upsertModel(model.toEntity())
        return model
    }

    suspend fun delete(model: AvatarModel) = withContext(Dispatchers.IO) {
        dao.deleteModel(model.id)
        if (!model.isAsset) {
            runCatching { File(model.toUri().path ?: return@runCatching).delete() }
        }
    }

    fun scanOutputFile(name: String): File = File(scansDir, name)

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    /**
     * glTF JSON points at its binary buffer with a relative uri. When we import a lone
     * .gltf we rewrite the buffer reference to the sibling .bin the user picked, if any,
     * so the loader can resolve it from the same directory.
     */
    private fun rewriteGltfBufferUri(gltfFile: File) {
        val json = runCatching { gltfFile.readText() }.getOrNull() ?: return
        val siblingBins = modelsDir.listFiles { f -> f.extension.equals("bin", true) } ?: return
        if (siblingBins.isEmpty()) return
        var patched = json
        siblingBins.forEach { bin ->
            patched = patched.replace(Regex("\"uri\"\\s*:\\s*\"[^\"]+\\.bin\""), "\"uri\": \"${bin.name}\"")
        }
        if (patched != json) gltfFile.writeText(patched)
    }

    private fun AvatarModel.toEntity() = LibraryModelEntity(
        id = id,
        displayName = displayName,
        source = source.name,
        location = location,
        thumbnailPath = thumbnailPath,
        sizeBytes = sizeBytes,
        importedAtEpochMs = importedAtEpochMs,
        license = license,
        mature = mature
    )

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("glb", "gltf")
    }
}

package com.threedd.studio.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.audio.AudioEngine
import com.threedd.studio.data.model.AvatarDesign
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.repository.AvatarRepository
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.data.settings.SettingsStore
import com.threedd.studio.scan.PhotoAvatarBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryUiState(
    val builtIn: List<AvatarModel> = emptyList(),
    val imported: List<AvatarModel> = emptyList(),
    val scanned: List<AvatarModel> = emptyList(),
    val photos: List<AvatarModel> = emptyList(),
    val designs: List<AvatarDesign> = emptyList(),
    val busy: Boolean = false,
    val matureUnlocked: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Set while the user is choosing how thick a photo avatar should be. */
    val pendingPhotoUri: Uri? = null,
    val photoDepth: Float = 0.22f,
    val buildStage: String = "",
    val buildProgress: Float = 0f
) {
    val building: Boolean get() = buildStage.isNotEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val models: ModelRepository,
    private val avatars: AvatarRepository,
    private val settings: SettingsStore,
    private val photoBuilder: PhotoAvatarBuilder,
    private val audio: AudioEngine
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            models.importedModels.collect { list ->
                _state.update {
                    it.copy(
                        imported = list.filter { m -> m.source.name == "IMPORTED" },
                        scanned = list.filter { m -> m.source.name == "SCANNED" },
                        photos = list.filter { m -> m.source.name == "PHOTO" }
                    )
                }
            }
        }
        _state.update { it.copy(builtIn = ModelRepository.builtInModels) }
        viewModelScope.launch { avatars.designs.collect { list -> _state.update { it.copy(designs = list) } } }
        viewModelScope.launch { settings.snapshot.collect { s -> _state.update { it.copy(matureUnlocked = s.ageVerified) } } }
    }

    /**
     * One entry point for both kinds of upload: glTF models are imported as-is, while
     * JPEG/PNG/WebP images are routed to the photo avatar builder.
     */
    fun onDocumentPicked(uri: Uri) {
        if (models.isImage(uri)) {
            _state.update { it.copy(pendingPhotoUri = uri, error = null, message = null) }
            audio.play(AudioEngine.Cue.SELECT)
        } else {
            importModel(uri, _state.value.matureUnlocked)
        }
    }

    fun setPhotoDepth(depth: Float) {
        _state.update { it.copy(photoDepth = depth.coerceIn(0.05f, 0.5f)) }
    }

    fun cancelPendingPhoto() {
        _state.update { it.copy(pendingPhotoUri = null) }
    }

    fun buildAvatarFromPhoto() {
        val uri = _state.value.pendingPhotoUri ?: return
        val depth = _state.value.photoDepth
        val name = "Photo ${System.currentTimeMillis() % 100000}"
        _state.update { it.copy(error = null, buildStage = "Starting", buildProgress = 0f) }
        viewModelScope.launch {
            val built = withContext(Dispatchers.Default) {
                photoBuilder.build(uri, name, depth) { p ->
                    _state.update { it.copy(buildStage = p.stage, buildProgress = p.fraction) }
                }
            }
            if (built == null) {
                _state.update {
                    it.copy(buildStage = "", buildProgress = 0f, pendingPhotoUri = null,
                        error = "Could not build an avatar from that image.")
                }
                audio.play(AudioEngine.Cue.ERROR)
                return@launch
            }
            models.registerPhoto(
                id = "photo_${built.file.nameWithoutExtension}",
                displayName = name,
                file = built.file,
                mature = _state.value.matureUnlocked
            )
            _state.update {
                it.copy(
                    buildStage = "", buildProgress = 0f, pendingPhotoUri = null,
                    message = "Built \"$name\" with ${built.triangleCount} triangles" +
                        if (built.usedSilhouette) "" else " (used fallback silhouette)"
                )
            }
            audio.play(AudioEngine.Cue.CONFIRM)
        }
    }

    fun importModel(uri: Uri, mature: Boolean) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val model = models.import(uri, mature)
            _state.update {
                it.copy(busy = false, message = model?.let { m -> "Imported ${m.displayName}" },
                    error = if (model == null) "Could not import that file. Use a .glb, .gltf, .jpg, .png or .webp." else null)
            }
        }
    }

    /**
     * Opens the tapped model in the Studio. The id travels as a navigation argument so the
     * Studio screen loads it - there is no shared mutable flag pretending a load happened.
     */
    fun openInStudio(model: AvatarModel, navigate: (String) -> Unit) {
        navigate(model.id)
    }

    fun deleteModel(model: AvatarModel) {
        viewModelScope.launch { models.delete(model) }
    }

    fun deleteDesign(design: AvatarDesign) {
        viewModelScope.launch { avatars.delete(design.id) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }
}

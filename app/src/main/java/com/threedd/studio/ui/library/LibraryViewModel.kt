package com.threedd.studio.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.data.model.AvatarDesign
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.repository.AvatarRepository
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val builtIn: List<AvatarModel> = emptyList(),
    val imported: List<AvatarModel> = emptyList(),
    val scanned: List<AvatarModel> = emptyList(),
    val designs: List<AvatarDesign> = emptyList(),
    val busy: Boolean = false,
    val matureUnlocked: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val models: ModelRepository,
    private val avatars: AvatarRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            models.importedModels.collect { list ->
                _state.update {
                    it.copy(
                        imported = list.filter { m -> m.source.name == "IMPORTED" },
                        scanned = list.filter { m -> m.source.name == "SCANNED" }
                    )
                }
            }
        }
        _state.update { it.copy(builtIn = models.builtInModels) }
        viewModelScope.launch { avatars.designs.collect { list -> _state.update { it.copy(designs = list) } } }
        viewModelScope.launch { settings.snapshot.collect { s -> _state.update { it.copy(matureUnlocked = s.ageVerified) } } }
    }

    fun importModel(uri: Uri, mature: Boolean) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val model = models.import(uri, mature)
            _state.update {
                it.copy(busy = false, message = model?.let { m -> "Imported ${m.displayName}" },
                    error = if (model == null) "Could not import that file." else null)
            }
        }
    }

    fun deleteModel(model: AvatarModel) {
        viewModelScope.launch { models.delete(model) }
    }

    fun deleteDesign(design: AvatarDesign) {
        viewModelScope.launch { avatars.delete(design.id) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }
}

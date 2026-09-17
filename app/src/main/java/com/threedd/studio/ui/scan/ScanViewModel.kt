package com.threedd.studio.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.audio.AudioEngine
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.scan.ScanPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ScanUiState(
    val session: File? = null,
    val frames: List<File> = emptyList(),
    val capturing: Boolean = false,
    val reconstructing: Boolean = false,
    val stage: String = "",
    val progress: Float = 0f,
    val resultName: String? = null,
    val error: String? = null
) {
    val canBuild: Boolean get() = frames.size >= ScanPipeline.MIN_FRAMES && !reconstructing
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val pipeline: ScanPipeline,
    private val models: ModelRepository,
    private val audio: AudioEngine
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun ensureSession(): File {
        val existing = _state.value.session
        if (existing != null && existing.exists()) return existing
        val created = pipeline.newSession()
        _state.update { it.copy(session = created) }
        return created
    }

    fun onFrameCaptured(file: File) {
        _state.update { it.copy(frames = it.frames + file) }
        audio.play(AudioEngine.Cue.TAP)
    }

    fun captureFailed(reason: String) {
        _state.update { it.copy(error = reason) }
        audio.play(AudioEngine.Cue.ERROR)
    }

    fun buildAvatar(name: String) {
        val frames = _state.value.frames
        if (frames.size < ScanPipeline.MIN_FRAMES) {
            _state.update { it.copy(error = "Capture at least ${ScanPipeline.MIN_FRAMES} frames.") }
            return
        }
        _state.update { it.copy(reconstructing = true, error = null, progress = 0f) }
        viewModelScope.launch {
            val output = withContext(Dispatchers.Default) {
                pipeline.reconstruct(frames, name) { p ->
                    _state.update { it.copy(stage = p.stage, progress = p.fraction) }
                }
            }
            if (output == null) {
                _state.update { it.copy(reconstructing = false, error = "Reconstruction failed — try more frames with a plain background.") }
                audio.play(AudioEngine.Cue.ERROR)
                return@launch
            }
            models.registerScan(id = "scan_${output.nameWithoutExtension}", displayName = name, file = output)
            _state.update { it.copy(reconstructing = false, resultName = name, progress = 1f) }
            audio.play(AudioEngine.Cue.CONFIRM)
        }
    }

    fun resetSession() {
        val session = _state.value.session
        viewModelScope.launch(Dispatchers.IO) { session?.let { pipeline.clearSession(it) } }
        _state.value = ScanUiState()
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

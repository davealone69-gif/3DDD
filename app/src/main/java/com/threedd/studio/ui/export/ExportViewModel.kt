package com.threedd.studio.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.audio.AudioEngine
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.export.ExportManager
import com.threedd.studio.render.StudioRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val lastOutput: File? = null,
    val error: String? = null,
    val width: Int = 1080,
    val height: Int = 1920,
    val gifFrames: Int = 48,
    val videoSeconds: Int = 8
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val renderer: StudioRenderer,
    private val exports: ExportManager,
    private val audio: AudioEngine
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun setWidth(value: Int) = _state.update { it.copy(width = value.coerceIn(256, 4096)) }
    fun setHeight(value: Int) = _state.update { it.copy(height = value.coerceIn(256, 4096)) }
    fun setGifFrames(value: Int) = _state.update { it.copy(gifFrames = value.coerceIn(8, 120)) }
    fun setVideoSeconds(value: Int) = _state.update { it.copy(videoSeconds = value.coerceIn(3, 30)) }

    fun exportPng() = run("PNG still") { exports.exportPng(renderer, _state.value.width, _state.value.height) }

    fun exportGlb(model: AvatarModel?, weights: Map<String, Float>) = run("glTF binary") {
        if (model == null) null else exports.exportGlb(model, weights)
    }

    fun exportMp4() = run("Turntable MP4") {
        exports.exportMp4(
            renderer = renderer,
            width = _state.value.width,
            height = _state.value.height,
            seconds = _state.value.videoSeconds,
            onProgress = { p -> _state.update { it.copy(progress = p) } }
        )
    }

    fun exportGif() = run("Turntable GIF") {
        exports.exportGif(
            renderer = renderer,
            frames = _state.value.gifFrames,
            onProgress = { p -> _state.update { it.copy(progress = p) } }
        )
    }

    private fun run(stage: String, block: suspend () -> File?) {
        if (_state.value.running) return
        _state.update { it.copy(running = true, progress = 0f, stage = stage, error = null) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (t: Throwable) {
                _state.update { it.copy(running = false, error = t.message ?: "Export failed.") }
                return@launch
            }
            _state.update {
                it.copy(
                    running = false,
                    progress = 1f,
                    lastOutput = result,
                    error = if (result == null) "Export failed." else null
                )
            }
            audio.play(if (result == null) AudioEngine.Cue.ERROR else AudioEngine.Cue.CONFIRM)
        }
    }

    fun clearExports() {
        exports.clearExports()
        _state.update { it.copy(lastOutput = null) }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

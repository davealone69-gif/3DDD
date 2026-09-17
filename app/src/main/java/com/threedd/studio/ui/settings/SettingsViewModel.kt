package com.threedd.studio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.audio.AudioEngine
import com.threedd.studio.content.AgeGate
import com.threedd.studio.data.model.QualityPreset
import com.threedd.studio.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val audio: AudioEngine,
    private val ageGate: AgeGate
) : ViewModel() {

    private val _settings = MutableStateFlow(SettingsStore.Snapshot())
    val settingsState: StateFlow<SettingsStore.Snapshot> = _settings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            settings.snapshot.collect { snapshot ->
                _settings.value = snapshot
                audio.sfxEnabled = snapshot.sfxEnabled
                audio.volume = snapshot.volume
                if (snapshot.musicEnabled) audio.startAmbient() else audio.stopAmbient()
            }
        }
    }

    fun setQuality(preset: QualityPreset) = viewModelScope.launch { settings.setQuality(preset) }
    fun setMusic(enabled: Boolean) = viewModelScope.launch { settings.setMusicEnabled(enabled) }
    fun setSfx(enabled: Boolean) = viewModelScope.launch { settings.setSfxEnabled(enabled) }
    fun setVolume(value: Float) = viewModelScope.launch { settings.setVolume(value) }
    fun setLoop(loop: Boolean) = viewModelScope.launch { settings.setLoopAnimation(loop) }
    fun setSpeed(speed: Float) = viewModelScope.launch { settings.setAnimationSpeed(speed) }

    fun lockMature() {
        viewModelScope.launch {
            ageGate.lock()
            _message.value = "Mature module locked."
        }
    }

    fun consumeMessage() { _message.value = null }
}

package com.threedd.studio.ui.agegate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.audio.AudioEngine
import com.threedd.studio.content.AgeGate
import com.threedd.studio.content.AgeVerification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgeGateUiState(
    val dateOfBirth: String = "",
    val unlocked: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class AgeGateViewModel @Inject constructor(
    private val ageGate: AgeGate,
    private val audio: AudioEngine
) : ViewModel() {

    private val _state = MutableStateFlow(AgeGateUiState())
    val state: StateFlow<AgeGateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { ageGate.isUnlocked.collect { u -> _state.update { it.copy(unlocked = u) } } }
    }

    fun setDateOfBirth(value: String) {
        if (value.length <= 10) _state.update { it.copy(dateOfBirth = value, message = null) }
    }

    fun confirm() {
        val value = _state.value.dateOfBirth
        when (val result = ageGate.verify(value)) {
            is AgeVerification.Result.Unlocked -> {
                viewModelScope.launch {
                    ageGate.commit(result, value)
                    _state.update { it.copy(message = "Verified: ${result.age}. Mature content unlocked.", busy = false) }
                    audio.play(AudioEngine.Cue.UNLOCK)
                }
            }
            AgeVerification.Result.Underage -> {
                _state.update { it.copy(message = "Access denied - you must be 18 or older.") }
                audio.play(AudioEngine.Cue.ERROR)
            }
            AgeVerification.Result.InvalidDate -> {
                _state.update { it.copy(message = "Enter a valid date as YYYY-MM-DD.") }
                audio.play(AudioEngine.Cue.ERROR)
            }
            AgeVerification.Result.FutureDate -> {
                _state.update { it.copy(message = "That date is in the future.") }
                audio.play(AudioEngine.Cue.ERROR)
            }
            AgeVerification.Result.ImplausibleDate -> {
                _state.update { it.copy(message = "That date of birth is not plausible.") }
                audio.play(AudioEngine.Cue.ERROR)
            }
        }
    }

    fun lock() {
        viewModelScope.launch {
            ageGate.lock()
            _state.update { it.copy(message = "Mature module locked.", dateOfBirth = "") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

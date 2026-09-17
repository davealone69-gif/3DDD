package com.threedd.studio.ui.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threedd.studio.ai.AiRouter
import com.threedd.studio.render.StudioRenderer
import com.threedd.studio.repair.RepairSupervisor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val backend: String = "unknown",
    val materialDegraded: Boolean = false,
    val lastLoadError: String? = null,
    val failures: List<RepairSupervisor.Failure> = emptyList(),
    val learned: Map<String, String> = emptyMap(),
    val repairsApplied: Int = 0,
    val advisorQuestion: String = "",
    val advisorAnswer: String? = null,
    val advisorProvider: String = "rules",
    val advising: Boolean = false,
    val localModelReachable: Boolean = false
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val renderer: StudioRenderer,
    private val supervisor: RepairSupervisor,
    private val advisor: AiRouter
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            supervisor.state.collect { s ->
                _state.update {
                    it.copy(
                        failures = s.failures,
                        learned = s.learned,
                        repairsApplied = s.repairsApplied
                    )
                }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(localModelReachable = advisor.localAvailable()) }
        }
    }

    fun refresh() {
        _state.update {
            it.copy(
                backend = renderer.backendName,
                materialDegraded = renderer.materials.degraded,
                lastLoadError = renderer.lastError
            )
        }
    }

    fun setQuestion(value: String) = _state.update { it.copy(advisorQuestion = value) }

    fun ask() {
        val question = _state.value.advisorQuestion.ifBlank { "What should I check?" }
        _state.update { it.copy(advising = true, advisorAnswer = null) }
        viewModelScope.launch {
            val context = buildContext()
            val answer = advisor.ask(question, context)
            _state.update {
                it.copy(
                    advising = false,
                    advisorAnswer = answer ?: "No answer available.",
                    advisorProvider = advisor.lastProvider,
                    localModelReachable = advisor.localAvailable()
                )
            }
        }
    }

    /** The advisor is given the real diagnostics, so its answer is grounded in this device. */
    private fun buildContext(): String = buildString {
        append("backend=").append(renderer.backendName)
        append("; pbr_shader=").append(if (renderer.materials.degraded) "simplified fallback" else "compiled")
        renderer.lastError?.let { append("; last_load_error=").append(it) }
        val recent = supervisor.state.value.failures.take(5)
        if (recent.isNotEmpty()) {
            append("; recent_failures=")
            append(recent.joinToString(" | ") { "${it.stage}:${it.message}->${it.repairedBy ?: "unrepaired"}" })
        }
        val learned = supervisor.state.value.learned
        if (learned.isNotEmpty()) {
            append("; learned_fixes=")
            append(learned.entries.take(5).joinToString(" | ") { "${it.key}=${it.value}" })
        }
    }

    fun clearHistory() = supervisor.clearHistory()
    fun forgetLearned() = supervisor.forgetLearned()
}

package com.threedd.studio.ui.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.threedd.studio.ai.AiRouter
import com.threedd.studio.ai.LocalModelLauncher
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
    val localModelReachable: Boolean = false,
    val server: LocalModelLauncher.Status = LocalModelLauncher.Status(),
    val installedModels: List<String> = emptyList(),
    val manualCommand: String = "",
    val exportedPath: String? = null,
    val endpointDraft: String = "",
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = ""
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val renderer: StudioRenderer,
    private val supervisor: RepairSupervisor,
    private val advisor: AiRouter,
    private val launcher: LocalModelLauncher
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
            launcher.status.collect { status ->
                _state.update {
                    it.copy(
                        server = status,
                        installedModels = launcher.installedModels().map { file -> file.name },
                        manualCommand = launcher.manualCommand(),
                        localModelReachable = status.running
                    )
                }
            }
        }
        viewModelScope.launch { launcher.probe() }
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

    /** Launches the local model server autonomously, then points the advisor at it. */
    fun startServer() {
        viewModelScope.launch {
            val status = launcher.start()
            advisor.configureLocal(status.endpoint, _state.value.selectedModel)
            _state.update { it.copy(localModelReachable = status.running) }
            if (status.running) refreshModels()
        }
    }

    fun stopServer() {
        launcher.stop()
        _state.update { it.copy(localModelReachable = false) }
    }

    fun setEndpointDraft(value: String) = _state.update { it.copy(endpointDraft = value) }

    /** Point the app at any OpenAI-compatible server: on this device, or a PC on the LAN. */
    fun applyEndpoint() {
        val value = _state.value.endpointDraft.trim()
        if (value.isBlank()) return
        launcher.setEndpoint(if (value.startsWith("http")) value else "http://$value")
        viewModelScope.launch {
            val status = launcher.start()
            advisor.configureLocal(status.endpoint, _state.value.selectedModel)
            _state.update { it.copy(server = status, manualCommand = launcher.manualCommand()) }
            if (status.running) refreshModels()
        }
    }

    fun probeServer() {
        viewModelScope.launch {
            val alive = launcher.probe()
            if (alive) refreshModels()
        }
    }

    /** Reads the live model list from the server; nothing is hard coded. */
    fun refreshModels() {
        viewModelScope.launch {
            val models = launcher.fetchModels()
            _state.update {
                it.copy(
                    availableModels = models,
                    selectedModel = it.selectedModel.takeIf { m -> m in models } ?: models.firstOrNull() ?: ""
                )
            }
        }
    }

    fun selectModel(name: String) {
        _state.update { it.copy(selectedModel = name) }
        viewModelScope.launch { advisor.configureLocal(_state.value.server.endpoint, name) }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            launcher.importModel(uri)
            _state.update { it.copy(installedModels = launcher.installedModels().map { f -> f.name }) }
        }
    }

    /** Puts the newest model where Termux can read it, and updates the command shown. */
    fun exportModelForTermux() {
        val model = launcher.installedModels().firstOrNull()
        if (model == null) {
            _state.update { it.copy(manualCommand = "Import a .gguf first.") }
            return
        }
        val shared = launcher.exportModelToDownloads(model)
        _state.update {
            it.copy(
                exportedPath = shared,
                manualCommand = launcher.manualCommand(shared)
            )
        }
    }

    fun clearHistory() = supervisor.clearHistory()
    fun forgetLearned() = supervisor.forgetLearned()
}

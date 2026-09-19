package com.threedd.studio.ui.repair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.theme.NeonCyan
import com.threedd.studio.ui.theme.Surface2
import com.threedd.studio.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the app knows about its own health: the graphics backend, whether the PBR shader
 * compiled, every failure it has seen, which repair fixed it, and a free on-device assistant
 * that answers questions about all of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Diagnostics & repair", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            SectionTitle("3D engine")
            Text("Graphics backend: ${state.backend}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (state.materialDegraded) "PBR shader: simplified fallback in use (the driver rejected the full shader)"
                else "PBR shader: compiled",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.materialDegraded) MaterialTheme.colorScheme.error else NeonCyan
            )
            state.lastLoadError?.let {
                Text("Last load error: $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Repairs applied: ${state.repairsApplied}",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp)) {
                Text("Re-check engine")
            }

            SectionTitle("Learned fixes")
            if (state.learned.isEmpty()) {
                Text(
                    "Nothing learned yet. When a failure is repaired, the fix that worked is remembered here and tried first next time.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
            } else {
                state.learned.forEach { (signature, repair) ->
                    Card(colors = CardDefaults.cardColors(containerColor = Surface2),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(repair, style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                            Text(signature, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                OutlinedButton(onClick = viewModel::forgetLearned) { Text("Forget learned fixes") }
            }

            SectionTitle("Failure history")
            if (state.failures.isEmpty()) {
                Text("No failures recorded.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            } else {
                state.failures.take(12).forEach { failure ->
                    Card(colors = CardDefaults.cardColors(containerColor = Surface2),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "${failure.stage} - ${if (failure.repaired) "repaired by ${failure.repairedBy}" else "unrepaired"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (failure.repaired) NeonCyan else MaterialTheme.colorScheme.error
                            )
                            Text(failure.message, style = MaterialTheme.typography.bodySmall)
                            Text(
                                SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(failure.at)),
                                style = MaterialTheme.typography.labelSmall, color = TextSecondary
                            )
                        }
                    }
                }
                OutlinedButton(onClick = viewModel::clearHistory) { Text("Clear history") }
            }

            SectionTitle("Local model server")
            Text("Status: ${state.server.phase} — ${state.server.detail}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.server.running) NeonCyan else TextSecondary)
            state.server.pid?.let { Text("pid $it", style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
            state.server.modelPath?.let {
                Text("model: ${it.substringAfterLast('/')}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (state.server.running) {
                    Button(onClick = viewModel::stopServer) { Text("Stop server") }
                } else {
                    Button(onClick = viewModel::startServer) { Text("Start server") }
                }
                OutlinedButton(onClick = viewModel::probeServer) { Text("Probe") }
                val ggufPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) viewModel.importModel(uri) }
                OutlinedButton(onClick = { ggufPicker.launch(arrayOf("*/*")) }) { Text("Import .gguf") }
            }
            if (state.installedModels.isNotEmpty()) {
                Text("Models: " + state.installedModels.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp))
            }
            OutlinedTextField(
                value = state.endpointDraft,
                onValueChange = viewModel::setEndpointDraft,
                label = { Text("Server address (default 127.0.0.1:8088, or your PC: 192.168.1.50:8088)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedButton(onClick = viewModel::applyEndpoint, modifier = Modifier.padding(top = 4.dp)) {
                Text("Use this address")
            }

            OutlinedButton(
                onClick = viewModel::exportModelForTermux,
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Copy model to Downloads (for Termux)") }
            state.exportedPath?.let {
                Text("Copied to $it", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            }

            if (!state.server.running) {
                Text(
                    "Termux cannot read the app's private storage, so the model must sit in Downloads. " +
                        "In Termux: pkg install llama-cpp, then run:",
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(state.manualCommand, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            }

            if (state.availableModels.isNotEmpty()) {
                SectionTitle("Model (live from /v1/models)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.availableModels) { name ->
                        com.threedd.studio.ui.components.PresetChip(
                            label = name,
                            selected = name == state.selectedModel,
                            onClick = { viewModel.selectModel(name) }
                        )
                    }
                }
            }

            SectionTitle("Assistant")
            Text(
                if (state.localModelReachable)
                    "Answering with the on-device model (llama.cpp on 127.0.0.1:8088)."
                else "No local model detected - answering with the built-in offline rules. Free either way.",
                style = MaterialTheme.typography.labelSmall, color = TextSecondary
            )
            OutlinedTextField(
                value = state.advisorQuestion,
                onValueChange = viewModel::setQuestion,
                label = { Text("Ask about a problem") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = viewModel::ask, enabled = !state.advising) {
                    Text(if (state.advising) "Thinking…" else "Ask")
                }
                Text("model: ${state.selectedModel.ifBlank { "none" }} | provider: ${state.advisorProvider}", modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            state.advisorAnswer?.let {
                Card(colors = CardDefaults.cardColors(containerColor = Surface2),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("", modifier = Modifier.padding(bottom = 32.dp))
        }
    }
}

package com.threedd.studio.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threedd.studio.ui.components.sessionViewModel
import com.threedd.studio.data.model.Presets
import com.threedd.studio.ui.components.FilamentViewport
import com.threedd.studio.ui.components.PresetChip
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.theme.NeonCyan
import com.threedd.studio.ui.theme.NeonMagenta
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    onOpenLibrary: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAgeGate: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenExport: () -> Unit,
    viewModel: StudioViewModel = sessionViewModel<StudioViewModel>()
) {
    val state by viewModel.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var designName by remember { mutableStateOf("Untitled avatar") }

    LaunchedEffect(state.error) { /* banner shows it */ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = {
                    Column {
                        Text("3Double D", fontWeight = FontWeight.Bold)
                        Text(
                            state.model?.displayName ?: "No model loaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenScan) { Icon(Icons.Filled.CameraAlt, "Scan") }
                    IconButton(onClick = onOpenLighting) { Icon(Icons.Filled.LightMode, "Lighting") }
                    IconButton(onClick = viewModel::resetCamera) { Icon(Icons.Filled.CenterFocusStrong, "Reset camera") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Save avatar") }, leadingIcon = {
                            Icon(Icons.Filled.Save, null)
                        }, onClick = { menuOpen = false; saveDialog = true })
                        DropdownMenuItem(text = { Text("Model library") }, leadingIcon = {
                            Icon(Icons.Filled.ArrowBack, null)
                        }, onClick = { menuOpen = false; onOpenLibrary() })
                        DropdownMenuItem(text = { Text("Export") }, onClick = { menuOpen = false; onOpenExport() })
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = {
                            Icon(Icons.Filled.Settings, null)
                        }, onClick = { menuOpen = false; onOpenSettings() })
                        DropdownMenuItem(
                            text = { Text(if (state.matureUnlocked) "18+ module unlocked" else "Unlock 18+ module") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            onClick = { menuOpen = false; onOpenAgeGate() }
                        )
                        DropdownMenuItem(text = { Text("About") }, leadingIcon = {
                            Icon(Icons.Filled.Info, null)
                        }, onClick = { menuOpen = false; onOpenAbout() })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                FilamentViewport(
                    renderer = viewModel.renderer,
                    modifier = Modifier.fillMaxSize(),
                    onFrame = { delta -> viewModel.advanceAnimation(delta) }
                )
                if (state.loading) {
                    Text(
                        "Building scene…",
                        color = NeonCyan,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(onClick = {}, label = {
                        Text(if (state.model?.source?.name == "BUILTIN") "PROCEDURAL RIG" else state.model?.source?.name ?: "—")
                    })
                    if (state.matureUnlocked) {
                        AssistChip(onClick = onOpenAgeGate, label = { Text("18+") })
                    }
                }
            }

            StatusBanner(
                message = state.error ?: state.status,
                isError = state.error != null,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onDismiss = { viewModel.consumeError(); viewModel.consumeStatus() }
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) { Text("Models") }
                Button(onClick = onOpenExport, modifier = Modifier.weight(1f)) { Text("Export") }
            }

            Row(Modifier.padding(start = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BODY PRESETS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                if (!state.matureUnlocked) {
                    Text(
                        "  · 18+ presets locked",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonMagenta
                    )
                }
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Presets.bodyPresets) { preset ->
                    if (preset.mature && !state.matureUnlocked) return@items
                    PresetChip(
                        label = preset.label,
                        selected = state.bodyPresetId == preset.id,
                        onClick = { viewModel.selectBodyPreset(preset.id) }
                    )
                }
            }
        }
    }

    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Save avatar") },
            text = {
                OutlinedTextField(
                    value = designName,
                    onValueChange = { designName = it },
                    label = { Text("Design name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveDesign(designName.ifBlank { "Untitled avatar" })
                    saveDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Cancel") } }
        )
    }
}

package com.threedd.studio.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threedd.studio.data.model.AvatarDesign
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.theme.NeonCyan
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenScan: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri, mature = state.matureUnlocked)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Model library", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatusBanner(
                message = state.error ?: state.message,
                isError = state.error != null,
                modifier = Modifier.padding(16.dp),
                onDismiss = viewModel::consumeMessage
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        picker.launch(arrayOf("model/gltf-binary", "model/gltf+json", "application/octet-stream"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Filled.Add, null); Text("  Import glTF/GLB") }
                OutlinedButton(onClick = onOpenScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null); Text("  Scan")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { SectionTitle("Built-in rigs") }
                items(state.builtIn, key = { it.id }) { model ->
                    ModelRow(model) { }
                }
                item { SectionTitle("Imported") }
                if (state.imported.isEmpty()) {
                    item { Text("Nothing imported yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.imported, key = { it.id }) { model ->
                        ModelRow(model) { viewModel.deleteModel(model) }
                    }
                }
                item { SectionTitle("Scanned") }
                if (state.scanned.isEmpty()) {
                    item { Text("No scans yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.scanned, key = { it.id }) { model ->
                        ModelRow(model) { viewModel.deleteModel(model) }
                    }
                }
                item { SectionTitle("Saved avatars") }
                if (state.designs.isEmpty()) {
                    item { Text("No saved avatars yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.designs, key = { it.id }) { design -> DesignRow(design) { viewModel.deleteDesign(design) } }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: AvatarModel, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(model.source.name.lowercase())
                        if (model.sizeBytes > 0) append(" · ${model.sizeBytes / 1024} KiB")
                        if (model.mature) append(" · 18+")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }
            if (!model.isAsset) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
    }
}

@Composable
private fun DesignRow(design: AvatarDesign, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface2), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Column {
                    Text(design.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${design.bodyPresetId} · ${design.material.baseColorHex}" +
                            if (design.mature) " · 18+" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

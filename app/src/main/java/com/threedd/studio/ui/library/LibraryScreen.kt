package com.threedd.studio.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.threedd.studio.data.model.AvatarDesign
import com.threedd.studio.data.model.AvatarModel
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.theme.NeonCyan
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenScan: () -> Unit,
    onOpenStudio: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // One picker for both upload kinds: glTF models and still images.
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onDocumentPicked(uri)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onDocumentPicked(uri) }

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
                    onClick = { documentPicker.launch(ModelRepository.IMPORT_MIME_TYPES) },
                    enabled = !state.building,
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Filled.Add, null); Text("  Upload") }
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !state.building,
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Filled.Image, null); Text("  From photo") }
                OutlinedButton(
                    onClick = onOpenScan,
                    enabled = !state.building,
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Filled.PhotoCamera, null); Text("  Scan") }
            }

            Text(
                "Upload accepts .glb, .gltf, .jpg, .jpeg, .png and .webp. Images are cut out, " +
                    "inflated into a 3D body and textured with the photo itself.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.building) {
                Text(
                    "${state.buildStage}… ${(state.buildProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LinearProgressIndicator(
                    progress = { state.buildProgress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { SectionTitle("Built-in rigs") }
                items(state.builtIn, key = { it.id }) { model ->
                    ModelRow(model, onLoad = { viewModel.openInStudio(model, onOpenStudio) }, onDelete = null)
                }

                item { SectionTitle("From image") }
                if (state.photos.isEmpty()) {
                    item { Text("No photo avatars yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.photos, key = { it.id }) { model ->
                        ModelRow(model, onLoad = { viewModel.openInStudio(model, onOpenStudio) }, onDelete = { viewModel.deleteModel(model) })
                    }
                }

                item { SectionTitle("Imported") }
                if (state.imported.isEmpty()) {
                    item { Text("Nothing imported yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.imported, key = { it.id }) { model ->
                        ModelRow(model, onLoad = { viewModel.openInStudio(model, onOpenStudio) }, onDelete = { viewModel.deleteModel(model) })
                    }
                }

                item { SectionTitle("Scanned") }
                if (state.scanned.isEmpty()) {
                    item { Text("No scans yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.scanned, key = { it.id }) { model ->
                        ModelRow(model, onLoad = { viewModel.openInStudio(model, onOpenStudio) }, onDelete = { viewModel.deleteModel(model) })
                    }
                }

                item { SectionTitle("Saved avatars") }
                if (state.designs.isEmpty()) {
                    item { Text("No saved avatars yet.", color = NeonCyan.copy(alpha = 0.7f)) }
                } else {
                    items(state.designs, key = { it.id }) { design ->
                        DesignRow(design) { viewModel.deleteDesign(design) }
                    }
                }
            }
        }
    }

    if (state.pendingPhotoUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingPhoto,
            title = { Text("Build avatar from image") },
            text = {
                Column {
                    Text(
                        "The subject is cut from the background and inflated into a 3D body, " +
                            "with the photo used as its texture.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LabeledSlider(
                        label = "Body depth",
                        value = state.photoDepth,
                        range = 0.05f..0.5f,
                        onValueChange = viewModel::setPhotoDepth
                    ) { "%.2f".format(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::buildAvatarFromPhoto) { Text("Build avatar") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingPhoto) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModelRow(model: AvatarModel, onLoad: () -> Unit, onDelete: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLoad)
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
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
            }
            Icon(Icons.Filled.PlayArrow, "Load into studio", tint = NeonCyan)
        }
    }
}

@Composable
private fun DesignRow(design: AvatarDesign, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface2), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(design.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${design.bodyPresetId} · ${design.material.baseColorHex}" +
                        if (design.mature) " · 18+" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

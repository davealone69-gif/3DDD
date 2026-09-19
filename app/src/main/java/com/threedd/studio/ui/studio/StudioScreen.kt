package com.threedd.studio.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threedd.studio.data.avatar.AppearanceSpec
import com.threedd.studio.data.model.Presets
import com.threedd.studio.data.repository.ModelRepository
import com.threedd.studio.ui.components.FilamentViewport
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.PresetChip
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.components.sessionViewModel
import com.threedd.studio.ui.theme.Ink
import com.threedd.studio.ui.theme.NeonCyan
import com.threedd.studio.ui.theme.NeonMagenta
import com.threedd.studio.ui.theme.Surface1
import com.threedd.studio.ui.theme.Surface2
import com.threedd.studio.ui.theme.TextSecondary

/** Left rail entries. Each maps to a real capability rather than a decorative label. */
private enum class StudioCategory(val label: String, val icon: ImageVector) {
    APPEARANCE("Appearance", Icons.Filled.Person),
    HAIR("Hair", Icons.Filled.Brush),
    FACE("Face", Icons.Filled.Face),
    EYES("Eyes", Icons.Filled.RemoveRedEye),
    BODY("Body", Icons.Filled.Accessibility),
    MATERIAL("Material", Icons.Filled.Tune),
    LIGHT("Light", Icons.Filled.LightMode),
    CLOTHING("Clothing", Icons.Filled.Checkroom),
    ACCESSORIES("Accessories", Icons.Filled.Diamond),
    AUGMENTS("Augments", Icons.Filled.Bolt),
    TATTOOS("Tattoos", Icons.Filled.Palette),
    MOTION("Motion", Icons.Filled.Movie),
    IMPORTS("Import", Icons.Filled.FolderOpen),
    SCAN("Scan", Icons.Filled.PhotoCamera),
    EXPORT("Export", Icons.Filled.FileDownload),
    SETTINGS("Settings", Icons.Filled.Settings)
}

private enum class StudioTab(val label: String) { BUILDER("Builder"), PRESETS("Presets"), IMPORTS("Import") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    onOpenLibrary: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAgeGate: () -> Unit,
    initialModelId: String = "",
    onOpenAbout: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenExport: () -> Unit,
    viewModel: StudioViewModel = sessionViewModel<StudioViewModel>()
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(StudioTab.BUILDER) }
    var category by remember { mutableStateOf(StudioCategory.APPEARANCE) }
    var inspectorOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var designName by remember { mutableStateOf("MATRIX_07") }

    // a model chosen in the library is loaded here, once
    androidx.compose.runtime.LaunchedEffect(initialModelId) {
        if (initialModelId.isNotBlank()) viewModel.loadModelById(initialModelId)
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            Column(Modifier.background(Surface1)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AVATAR DESIGN", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("CREATE YOUR IDENTITY", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
                    }
                    IconButton(onClick = viewModel::resetCamera) { Icon(Icons.Filled.CenterFocusStrong, "Fit") }
                    IconButton(onClick = viewModel::randomize) { Icon(Icons.Filled.Casino, "Randomise") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Save avatar") }, leadingIcon = { Icon(Icons.Filled.Save, null) },
                            onClick = { menuOpen = false; saveDialog = true })
                        DropdownMenuItem(text = { Text("18+ module") }, leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            onClick = { menuOpen = false; onOpenAgeGate() })
                        DropdownMenuItem(text = { Text("Diagnostics & repair") }, leadingIcon = { Icon(Icons.Filled.Build, null) },
                            onClick = { menuOpen = false; onOpenDiagnostics() })
                        DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menuOpen = false; onOpenAbout() })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioTab.entries.forEach { entry ->
                        FilterChip(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            label = { Text(entry.label) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Surface1).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AVATAR ID", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(designName, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { designName = "MATRIX_${(10..99).random()}" }) { Text("Regenerate") }
                Button(onClick = { saveDialog = true }) { Text("Save avatar") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatusBanner(
                message = state.error ?: state.status,
                isError = state.error != null,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                onDismiss = { viewModel.consumeError(); viewModel.consumeStatus() }
            )

            Row(Modifier.weight(1f)) {
                LeftRail(
                    selected = category,
                    onSelect = { picked ->
                        when (picked) {
                            StudioCategory.IMPORTS -> onOpenLibrary()
                            StudioCategory.SCAN -> onOpenScan()
                            StudioCategory.EXPORT -> onOpenExport()
                            StudioCategory.SETTINGS -> onOpenSettings()
                            else -> {
                                category = picked
                                inspectorOpen = true
                                            }
                        }
                    }
                )

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    FilamentViewport(
                        renderer = viewModel.renderer,
                        modifier = Modifier.fillMaxSize(),
                        onFrame = { delta -> viewModel.advanceAnimation(delta) }
                    )

                    // viewport status / controls
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ViewportBadge("FIT", Icons.Filled.CenterFocusStrong) { viewModel.resetCamera() }
                        ViewportBadge("RND", Icons.Filled.Casino) { viewModel.randomize() }
                    }

                    Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        AssistChip(
                            onClick = onOpenLibrary,
                            label = { Text(state.model?.displayName ?: "No model — tap to open the library") }
                        )
                        if (state.loading) {
                            Text("Building scene…", style = MaterialTheme.typography.labelSmall, color = NeonCyan,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        val engineError = viewModel.renderer.lastError
                        if (engineError != null) {
                            Text(
                                "Renderer: $engineError",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp).width(200.dp)
                            )
                        }
                        if (viewModel.renderer.materials.degraded) {
                            Text(
                                "Simplified material (driver refused the PBR shader)",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonMagenta,
                                modifier = Modifier.padding(top = 4.dp).width(200.dp)
                            )
                        }
                    }

                    if (inspectorOpen) {
                        InspectorPanel(
                            category = category,
                            state = state,
                            viewModel = viewModel,
                            onClose = { inspectorOpen = false },
                            onOpenLibrary = onOpenLibrary,
                            onOpenLighting = onOpenLighting,
                            onOpenExport = onOpenExport,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }

            QuickTray(tab = tab, state = state, viewModel = viewModel, onOpenLibrary = onOpenLibrary, onOpenScan = onOpenScan)
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

@Composable
private fun LeftRail(selected: StudioCategory, onSelect: (StudioCategory) -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(Surface1)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StudioCategory.entries.forEach { entry ->
            val active = entry == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) NeonCyan.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    entry.icon,
                    contentDescription = entry.label,
                    tint = if (active) NeonCyan else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    entry.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) NeonCyan else TextSecondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ViewportBadge(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(CircleShape)
            .background(Surface2.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = NeonCyan, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun InspectorPanel(
    category: StudioCategory,
    state: StudioUiState,
    viewModel: StudioViewModel,
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(Surface1.copy(alpha = 0.97f))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(category.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = NeonCyan,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        }

        when (category) {
            StudioCategory.APPEARANCE -> AppearancePanel(state, viewModel, onOpenLibrary)
            StudioCategory.HAIR -> HairPanel(state, viewModel)
            StudioCategory.FACE -> FacePanel(state, viewModel)
            StudioCategory.EYES -> EyesPanel(state, viewModel)
            StudioCategory.BODY -> BodyPanel(state, viewModel)
            StudioCategory.MATERIAL -> MaterialPanel(state, viewModel)
            StudioCategory.LIGHT -> LightPanel(state, viewModel, onOpenLighting)
            StudioCategory.CLOTHING -> ClothingPanel(state, viewModel)
            StudioCategory.ACCESSORIES -> AccessoriesPanel(state, viewModel)
            StudioCategory.AUGMENTS -> AugmentsPanel(state, viewModel)
            StudioCategory.TATTOOS -> TattoosPanel(state, viewModel)
            StudioCategory.MOTION -> MotionPanel(state, viewModel)
            else -> {
                Text("This entry opens another screen.", color = TextSecondary)
                Button(onClick = onOpenExport, modifier = Modifier.padding(top = 8.dp)) { Text("Open export") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppearancePanel(state: StudioUiState, viewModel: StudioViewModel, onOpenLibrary: () -> Unit) {
    SectionTitle("Rig")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ModelRepository.builtInModels) { model ->
            PresetChip(model.displayName.substringBefore(" base"), state.model?.id == model.id, onClick = {
                viewModel.loadModel(model)
            })
        }
    }
    Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Upload or scan a model")
    }

    SectionTitle("Skin tone")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SKIN_TONES) { hex ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(
                        if (state.material.baseColorHex.equals(hex, true)) 2.dp else 1.dp,
                        if (state.material.baseColorHex.equals(hex, true)) NeonCyan else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
                    .clickable { viewModel.updateMaterial(state.material.copy(baseColorHex = hex)) }
            )
        }
    }

    SectionTitle("Describe an avatar")
    OutlinedTextField(
        value = state.prompt,
        onValueChange = viewModel::setPrompt,
        label = { Text("e.g. cyborg, pink hair, visor, neon") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = viewModel::generateFromPrompt,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("Generate avatar") }
    state.promptSummary?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp))
    }

    SectionTitle("Appearance presets")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Presets.materialPresets.keys.toList()) { name ->
            PresetChip(name, false, onClick = { viewModel.applyMaterialPreset(name) })
        }
    }
}

@Composable
private fun BodyPanel(state: StudioUiState, viewModel: StudioViewModel) {
    SectionTitle("Body presets")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Presets.bodyPresets) { preset ->
            if (preset.mature && !state.matureUnlocked) return@items
            PresetChip(preset.label, state.bodyPresetId == preset.id, onClick = { viewModel.selectBodyPreset(preset.id) })
        }
    }
    if (state.morphNames.isEmpty()) {
        SectionTitle("Shape")
        Text("This model has no morph targets.", color = TextSecondary)
    } else {
        state.morphNames.forEach { name ->
            SectionTitle(name.replaceFirstChar { it.uppercase() })
            LabeledSlider(
                label = name,
                value = state.morphWeights[name] ?: 0f,
                range = 0f..1f,
                onValueChange = { viewModel.setMorphWeight(name, it) }
            )
        }
    }
}

@Composable
private fun MaterialPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val material = state.material
    SectionTitle("Surface")
    LabeledSlider("Metallic", material.metallic, 0f..1f, onValueChange = {
        viewModel.updateMaterial(material.copy(metallic = it))
    })
    LabeledSlider("Roughness", material.roughness, 0.02f..1f, onValueChange = {
        viewModel.updateMaterial(material.copy(roughness = it))
    })
    LabeledSlider("Reflectance", material.reflectance, 0f..1f, onValueChange = {
        viewModel.updateMaterial(material.copy(reflectance = it))
    })
    LabeledSlider("Clear coat", material.clearCoat, 0f..1f, onValueChange = {
        viewModel.updateMaterial(material.copy(clearCoat = it))
    })
    LabeledSlider("Normal strength", material.normalScale, 0f..3f, onValueChange = {
        viewModel.updateMaterial(material.copy(normalScale = it))
    })
    LabeledSlider("Occlusion", material.occlusionStrength, 0f..1f, onValueChange = {
        viewModel.updateMaterial(material.copy(occlusionStrength = it))
    })
    SectionTitle("Emission")
    LabeledSlider("Intensity", material.emissiveIntensity, 0f..12f, onValueChange = {
        viewModel.updateMaterial(material.copy(emissiveIntensity = it))
    })
}

@Composable
private fun LightPanel(state: StudioUiState, viewModel: StudioViewModel, onOpenLighting: () -> Unit) {
    SectionTitle("Rigs")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Presets.lightPresets.keys.toList()) { name ->
            PresetChip(name, false, onClick = { viewModel.applyLightPreset(name) })
        }
    }
    SectionTitle("Quick adjust")
    LabeledSlider("Environment", state.light.environmentIntensity, 0f..80_000f, onValueChange = {
        viewModel.updateLight(state.light.copy(environmentIntensity = it))
    })
    LabeledSlider("Key", state.light.keyIntensity, 0f..250_000f, onValueChange = {
        viewModel.updateLight(state.light.copy(keyIntensity = it))
    })
    Button(onClick = onOpenLighting, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Full lighting controls")
    }
}

@Composable
private fun MotionPanel(state: StudioUiState, viewModel: StudioViewModel) {
    SectionTitle("Playback")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = viewModel::togglePlayback) {
            Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
            Text(if (state.playing) "  Pause" else "  Play")
        }
        TextButton(onClick = viewModel::stopPlayback) {
            Icon(Icons.Filled.Stop, null); Text("  Stop")
        }
    }
    LabeledSlider("Speed", state.speed, 0.1f..3f, onValueChange = viewModel::setSpeed) { "%.2fx".format(it) }

    SectionTitle("Voice")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.recording) {
            Button(onClick = viewModel::stopVocalRecording) {
                Icon(Icons.Filled.Stop, null); Text("  Stop recording")
            }
        } else {
            Button(onClick = { viewModel.refreshVocals(); viewModel.startVocalRecording() }) {
                Icon(Icons.Filled.Mic, null); Text("  Record a take")
            }
        }
    }
    if (state.vocals.isEmpty()) {
        Text("No takes yet.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.vocals) { sample ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(sample.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.playVocal(sample, 0f) }) { Text("Play") }
                    TextButton(onClick = { viewModel.playVocal(sample, 4f) }) { Text("+4") }
                    TextButton(onClick = { viewModel.playVocal(sample, -4f) }) { Text("-4") }
                }
            }
        }
    }

    SectionTitle("Clips")
    if (state.animations.isEmpty()) {
        Text(
            "No animation clips in this model. Import an animated .glb to see them here.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.animations) { clip ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(clip.name, style = MaterialTheme.typography.bodyMedium)
                        Text("%.2fs".format(clip.durationSeconds), style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan)
                    }
                    TextButton(onClick = {
                        viewModel.selectAnimation(clip.index)
                        viewModel.restartAnimation()
                    }) { Text("Play") }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    SectionTitle(label)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { (id, text) ->
            PresetChip(text, selected == id, onClick = { onPick(id) })
        }
    }
}

@Composable
private fun ColourRow(label: String, colours: List<String>, selected: String, onPick: (String) -> Unit) {
    SectionTitle(label)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(colours) { hex ->
            val active = selected.equals(hex, ignoreCase = true)
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(
                        if (active) 2.dp else 1.dp,
                        if (active) NeonCyan else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
                    .clickable { onPick(hex) }
            )
        }
    }
}

@Composable
private fun HairPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    OptionRow("Hair style", AppearanceSpec.HairStyle.entries.map { it.id to it.label },
        a.hairStyle) { viewModel.updateAppearance(a.copy(hairStyle = it)) }
    ColourRow("Hair colour", a.palettes.getValue("Hair"), a.hairColorHex) {
        viewModel.updateAppearance(a.copy(hairColorHex = it))
    }
}

@Composable
private fun FacePanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    OptionRow("Face shape", AppearanceSpec.FaceShape.entries.map { it.id to it.label },
        a.faceShape) { viewModel.updateAppearance(a.copy(faceShape = it)) }
    OptionRow("Style", AppearanceSpec.STYLES.map { it to it }, a.style) {
        viewModel.updateAppearance(a.copy(style = it))
    }
    SectionTitle("Skin tone")
    ColourRow("Skin", a.palettes.getValue("Skin"), a.skinToneHex) {
        viewModel.updateAppearance(a.copy(skinToneHex = it))
    }
}

@Composable
private fun EyesPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    ColourRow("Eye colour", a.palettes.getValue("Eyes"), a.eyeColorHex) {
        viewModel.updateAppearance(a.copy(eyeColorHex = it))
    }
    ColourRow("Accent / glow colour", a.palettes.getValue("Accent"), a.accentColorHex) {
        viewModel.updateAppearance(a.copy(accentColorHex = it))
    }
    LabeledSlider("Glow strength", a.glow, 0f..1f, onValueChange = {
        viewModel.updateAppearance(a.copy(glow = it))
    })
}

@Composable
private fun ClothingPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    OptionRow("Outfit", AppearanceSpec.Outfit.entries.map { it.id to it.label },
        a.outfit) { viewModel.updateAppearance(a.copy(outfit = it)) }
    ColourRow("Fabric colour", a.palettes.getValue("Accent") + a.palettes.getValue("Hair").take(4),
        a.outfitColorHex) { viewModel.updateAppearance(a.copy(outfitColorHex = it)) }
}

@Composable
private fun AccessoriesPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    OptionRow("Accessory", AppearanceSpec.Accessory.entries.map { it.id to it.label },
        a.accessory) { viewModel.updateAppearance(a.copy(accessory = it)) }
}

@Composable
private fun AugmentsPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    OptionRow("Augment", AppearanceSpec.Augment.entries.map { it.id to it.label },
        a.augment) { viewModel.updateAppearance(a.copy(augment = it)) }
    LabeledSlider("Glow strength", a.glow, 0f..1f, onValueChange = {
        viewModel.updateAppearance(a.copy(glow = it))
    })
}

@Composable
private fun TattoosPanel(state: StudioUiState, viewModel: StudioViewModel) {
    val a = state.appearance
    OptionRow("Tattoo", AppearanceSpec.Tattoo.entries.map { it.id to it.label },
        a.tattoo) { viewModel.updateAppearance(a.copy(tattoo = it)) }
    ColourRow("Ink colour", a.palettes.getValue("Accent"), a.accentColorHex) {
        viewModel.updateAppearance(a.copy(accentColorHex = it))
    }
}

@Composable
private fun QuickTray(
    tab: StudioTab,
    state: StudioUiState,
    viewModel: StudioViewModel,
    onOpenLibrary: () -> Unit,
    onOpenScan: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface1)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        when (tab) {
            StudioTab.BUILDER -> {
                Text("BODY PRESETS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                LazyRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(Presets.bodyPresets) { preset ->
                        if (preset.mature && !state.matureUnlocked) return@items
                        PresetChip(preset.label, state.bodyPresetId == preset.id, onClick = {
                            viewModel.selectBodyPreset(preset.id)
                        })
                    }
                }
            }
            StudioTab.PRESETS -> {
                Text("APPEARANCE PRESETS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Presets.materialPresets.keys.toList()) { name ->
                        PresetChip(name, false, onClick = { viewModel.applyMaterialPreset(name) })
                    }
                }
            }
            StudioTab.IMPORTS -> {
                Text("BRING IN A SUBJECT", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenLibrary) { Icon(Icons.Filled.Add, null); Text("  Upload") }
                    Button(onClick = onOpenScan) { Icon(Icons.Filled.PhotoCamera, null); Text("  Scan") }
                }
            }
        }
    }
}

/** Skin tone swatches offered in the appearance panel. */
private val SKIN_TONES = listOf(
    "#F2D6C2", "#E8C4A8", "#D8A98C", "#C68E6E", "#A9714F", "#8A5638", "#5E3A25", "#3A2418"
)

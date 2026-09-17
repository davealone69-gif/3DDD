package com.threedd.studio.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.threedd.studio.ui.components.sessionViewModel
import com.threedd.studio.data.model.MaterialState
import com.threedd.studio.data.model.Presets
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.PresetChip
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.studio.StudioViewModel
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialScreen(viewModel: StudioViewModel = sessionViewModel<StudioViewModel>()) {
    val state by viewModel.state.collectAsState()
    val material = state.material

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Material editor", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("Presets")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Presets.materialPresets.keys.toList()) { name ->
                    PresetChip(name, selected = false) { viewModel.applyMaterialPreset(name) }
                }
            }

            SectionTitle("Studio material")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Override model materials")
                    Text(
                        "Replaces every primitive with the studio PBR material so edits apply everywhere.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.overrideModelMaterials,
                    onCheckedChange = viewModel::setOverrideModelMaterials
                )
            }

            SectionTitle("Base")
            OutlinedTextField(
                value = material.baseColorHex,
                onValueChange = { viewModel.updateMaterial(material.copy(baseColorHex = it)) },
                label = { Text("Base colour (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = material.emissiveHex,
                onValueChange = { viewModel.updateMaterial(material.copy(emissiveHex = it)) },
                label = { Text("Emissive colour (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            SectionTitle("Surface")
            LabeledSlider("Metallic", material.metallic, 0f..1f) {
                viewModel.updateMaterial(material.copy(metallic = it))
            }
            LabeledSlider("Roughness", material.roughness, 0.02f..1f) {
                viewModel.updateMaterial(material.copy(roughness = it))
            }
            LabeledSlider("Reflectance", material.reflectance, 0f..1f) {
                viewModel.updateMaterial(material.copy(reflectance = it))
            }
            LabeledSlider("Emissive intensity", material.emissiveIntensity, 0f..12f) {
                viewModel.updateMaterial(material.copy(emissiveIntensity = it))
            }
            LabeledSlider("Clear coat", material.clearCoat, 0f..1f) {
                viewModel.updateMaterial(material.copy(clearCoat = it))
            }
            LabeledSlider("Clear coat roughness", material.clearCoatRoughness, 0.02f..1f) {
                viewModel.updateMaterial(material.copy(clearCoatRoughness = it))
            }
            Text(
                "",
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

package com.threedd.studio.ui.lighting

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
import com.threedd.studio.data.model.Presets
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.PresetChip
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.studio.StudioViewModel
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightingScreen(viewModel: StudioViewModel = sessionViewModel<StudioViewModel>()) {
    val state by viewModel.state.collectAsState()
    val light = state.light

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Lighting", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            SectionTitle("Rigs")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Presets.lightPresets.keys.toList()) { name ->
                    PresetChip(name, selected = false, onClick = { viewModel.applyLightPreset(name) })
                }
            }

            SectionTitle("Environment")
            LabeledSlider("Indirect light intensity", light.environmentIntensity, 0f..80_000f, onValueChange = {
                viewModel.updateLight(light.copy(environmentIntensity = it))
            })

            SectionTitle("Key light")
            LabeledSlider("Intensity", light.keyIntensity, 0f..250_000f, onValueChange = {
                viewModel.updateLight(light.copy(keyIntensity = it))
            })
            OutlinedTextField(
                value = light.keyColorHex,
                onValueChange = { viewModel.updateLight(light.copy(keyColorHex = it)) },
                label = { Text("Colour (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Fill light")
            LabeledSlider("Intensity", light.fillIntensity, 0f..150_000f, onValueChange = {
                viewModel.updateLight(light.copy(fillIntensity = it))
            })
            OutlinedTextField(
                value = light.fillColorHex,
                onValueChange = { viewModel.updateLight(light.copy(fillColorHex = it)) },
                label = { Text("Colour (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Rim light")
            LabeledSlider("Intensity", light.rimIntensity, 0f..250_000f, onValueChange = {
                viewModel.updateLight(light.copy(rimIntensity = it))
            })
            OutlinedTextField(
                value = light.rimColorHex,
                onValueChange = { viewModel.updateLight(light.copy(rimColorHex = it)) },
                label = { Text("Colour (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Shadows")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Cast shadows from the key light", modifier = Modifier.weight(1f))
                Switch(
                    checked = light.shadowsEnabled,
                    onCheckedChange = { viewModel.updateLight(light.copy(shadowsEnabled = it)) }
                )
            }
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

package com.threedd.studio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.threedd.studio.data.model.QualityPreset
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAgeGate: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settingsState.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            StatusBanner(message = message, modifier = Modifier.padding(top = 16.dp), onDismiss = viewModel::consumeMessage)

            SectionTitle("3D engine")
            val engine = viewModel.engineInfo
            Text("Graphics backend: ${engine.backend}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (engine.materialDegraded) "PBR shader: simplified fallback in use"
                else "PBR shader: compiled",
                style = MaterialTheme.typography.bodyMedium,
                color = if (engine.materialDegraded) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            engine.lastLoadError?.let {
                Text("Last load error: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }

            SectionTitle("Render quality")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.quality == preset,
                        onClick = { viewModel.setQuality(preset) },
                        label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            SectionTitle("Audio")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ambient music", modifier = Modifier.weight(1f))
                Switch(checked = settings.musicEnabled, onCheckedChange = viewModel::setMusic)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Interface sounds", modifier = Modifier.weight(1f))
                Switch(checked = settings.sfxEnabled, onCheckedChange = viewModel::setSfx)
            }
            LabeledSlider("Volume", settings.volume, 0f..1f, viewModel::setVolume) {
                "${(it * 100).toInt()}%"
            }

            SectionTitle("Animation defaults")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Loop by default", modifier = Modifier.weight(1f))
                Switch(checked = settings.loopAnimation, onCheckedChange = viewModel::setLoop)
            }
            LabeledSlider("Default speed", settings.animationSpeed, 0.1f..3f, viewModel::setSpeed) {
                "%.2fx".format(it)
            }

            SectionTitle("Content")
            Text(
                if (settings.ageVerified) "18+ module is unlocked on this device."
                else "18+ module is locked.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onOpenAgeGate) { Text(if (settings.ageVerified) "Manage age gate" else "Unlock 18+ module") }
                if (settings.ageVerified) {
                    Button(onClick = viewModel::lockMature) { Text("Lock now") }
                }
            }
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

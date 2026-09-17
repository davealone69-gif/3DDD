package com.threedd.studio.ui.export

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.components.sessionViewModel
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.studio.StudioViewModel
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    exportViewModel: ExportViewModel = hiltViewModel(),
    studioViewModel: StudioViewModel = sessionViewModel()
) {
    val state by exportViewModel.state.collectAsState()
    val studio by studioViewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Export", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            StatusBanner(
                message = state.error,
                isError = true,
                modifier = Modifier.padding(vertical = 8.dp),
                onDismiss = exportViewModel::consumeError
            )

            if (state.running) {
                Text("${state.stage}… ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }

            SectionTitle("Output resolution")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportViewModel.setWidth(1080); exportViewModel.setHeight(1920) }) { Text("1080x1920") }
                Button(onClick = { exportViewModel.setWidth(1440); exportViewModel.setHeight(2560) }) { Text("1440x2560") }
                Button(onClick = { exportViewModel.setWidth(2160); exportViewModel.setHeight(3840) }) { Text("4K") }
            }
            Text(
                "${state.width} x ${state.height}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )

            SectionTitle("Turntable")
            LabeledSlider("Video length", state.videoSeconds.toFloat(), 3f..30f, {
                exportViewModel.setVideoSeconds(it.toInt())
            }) { "${it.toInt()}s" }
            LabeledSlider("GIF frames", state.gifFrames.toFloat(), 8f..120f, {
                exportViewModel.setGifFrames(it.toInt())
            }) { "${it.toInt()}" }

            SectionTitle("Render")
            Button(
                onClick = { exportViewModel.exportPng() },
                enabled = !state.running && studio.model != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("Export PNG still") }
            Button(
                onClick = { exportViewModel.exportMp4() },
                enabled = !state.running && studio.model != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("Export turntable MP4") }
            Button(
                onClick = { exportViewModel.exportGif() },
                enabled = !state.running && studio.model != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("Export turntable GIF") }
            OutlinedButton(
                onClick = { exportViewModel.exportGlb(studio.model, studio.morphWeights) },
                enabled = !state.running && studio.model != null && studio.model?.isAsset == false,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("Export baked GLB") }
            if (studio.model?.isAsset == true) {
                Text(
                    "GLB re-export applies to imported or scanned models — the built-in rigs are generated at build time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.lastOutput?.let { file ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface2),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Last export", style = MaterialTheme.typography.titleMedium)
                        Text(file.absolutePath, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text("${file.length() / 1024} KiB", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

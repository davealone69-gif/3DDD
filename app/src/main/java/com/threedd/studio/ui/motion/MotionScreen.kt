package com.threedd.studio.ui.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.threedd.studio.ui.components.LabeledSlider
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.studio.StudioViewModel
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionScreen(viewModel: StudioViewModel = sessionViewModel<StudioViewModel>()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Motion & animation", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = viewModel::togglePlayback) {
                    Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                    Text(if (state.playing) "  Pause" else "  Play")
                }
                OutlinedButton(onClick = viewModel::stopPlayback) {
                    Icon(Icons.Filled.Stop, null); Text("  Stop")
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                LabeledSlider("Speed", state.speed, 0.1f..3f, viewModel::setSpeed) { "%.2fx".format(it) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop", modifier = Modifier.weight(1f))
                    Switch(checked = state.loop, onCheckedChange = viewModel::setLoop)
                }
            }

            SectionTitle("Clips", Modifier.padding(horizontal = 16.dp))
            if (state.animations.isEmpty()) {
                Text(
                    "This model has no glTF animation clips. Import an animated .glb to see them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.animations) { index, clip ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == state.animationIndex) Surface2 else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp).let { it },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(clip.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "%.2fs".format(clip.durationSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedButton(onClick = {
                                viewModel.selectAnimation(index)
                                viewModel.restartAnimation()
                            }) { Text("Play") }
                        }
                    }
                }
            }
        }
    }
}

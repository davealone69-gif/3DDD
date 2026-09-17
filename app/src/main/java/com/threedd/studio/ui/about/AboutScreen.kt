package com.threedd.studio.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threedd.studio.BuildConfig
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("About", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("3Double D", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "3Double D is a photorealistic avatar design studio built with Jetpack Compose and Google Filament. " +
                    "The renderer, material compiler (filamat), glTF loader (gltfio), shape-from-silhouette scanner, " +
                    "audio synthesiser and GIF/MP4 encoders all run on device.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            SectionTitle("Licences")
            Text("Filament  -  Apache License 2.0", style = MaterialTheme.typography.bodyMedium)
            Text("gltfio / filamat  -  Apache License 2.0", style = MaterialTheme.typography.bodyMedium)
            Text("Base rigs  -  generated in-project, CC0", style = MaterialTheme.typography.bodyMedium)
            SectionTitle("Privacy")
            Text(
                "Scans, imports, recordings and exports stay on the device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

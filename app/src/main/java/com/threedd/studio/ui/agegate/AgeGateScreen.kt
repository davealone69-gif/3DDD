package com.threedd.studio.ui.agegate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threedd.studio.content.AdultContentPolicy
import com.threedd.studio.ui.components.SectionTitle
import com.threedd.studio.ui.components.StatusBanner
import com.threedd.studio.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeGateScreen(
    next: String,
    onUnlocked: (String) -> Unit,
    viewModel: AgeGateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.unlocked) {
        if (state.unlocked && next.isNotBlank()) onUnlocked(next)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface2),
                title = { Text("Age verification", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            StatusBanner(
                message = state.message,
                isError = state.message?.contains("denied", true) == true,
                modifier = Modifier.padding(bottom = 12.dp),
                onDismiss = viewModel::consumeMessage
            )
            Text(
                "This module contains mature content and is restricted to adults aged 18 and over. " +
                    "Confirm your date of birth to continue.",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                value = state.dateOfBirth,
                onValueChange = viewModel::setDateOfBirth,
                label = { Text("Date of birth (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Button(
                onClick = viewModel::confirm,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Confirm and unlock") }

            if (state.unlocked) {
                Button(
                    onClick = viewModel::lock,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Lock module") }
            }

            SectionTitle("Policy")
            Text(
                "Allowed themes: " + AdultContentPolicy.allowedThemes.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium
            )
            AdultContentPolicy.disallowedCapabilities.forEach { rule ->
                Text("• Not supported: $rule", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "The gate fails closed: an unreadable or absent date of birth always results in a locked module.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

package com.example.codeonly.ui.shell.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codeonly.domain.model.ConnectionState
import com.example.codeonly.feature.connection.ConnectionIntent
import com.example.codeonly.feature.connection.ConnectionViewModel

@Composable
fun SettingsScreen(viewModel: ConnectionViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val selectedProvider = state.providers.firstOrNull { it.id == state.selectedProviderId }
    val selectedModel = selectedProvider?.models?.firstOrNull { it.id == state.selectedModelId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Connection", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = { viewModel.onIntent(ConnectionIntent.UrlChanged(it)) },
            label = { Text("Server URL") },
            placeholder = { Text("https://opencode.local") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.onIntent(ConnectionIntent.TestConnectionClicked) },
            enabled = state.canSubmit
        ) {
            Text("Test Connection")
        }

        if (state.isConnected) {
            Text(
                text = "Connected base URL: ${state.serverUrl}",
                style = MaterialTheme.typography.bodySmall
            )
            state.version?.let {
                Text(
                    text = "Server version: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.refreshProviders() },
                    enabled = !state.isLoadingProviders
                ) {
                    Text(if (state.isLoadingProviders) "Loading models..." else "Refresh Models")
                }
                Button(onClick = { viewModel.onIntent(ConnectionIntent.DisconnectClicked) }) {
                    Text("Disconnect")
                }
            }

            if (state.providers.isNotEmpty()) {
                Text("Default Model", style = MaterialTheme.typography.titleSmall)

                Button(onClick = { providerMenuExpanded = true }) {
                    Text(selectedProvider?.name ?: "Select provider")
                }
                DropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    state.providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                providerMenuExpanded = false
                                viewModel.onProviderSelected(provider.id)
                            }
                        )
                    }
                }

                Button(onClick = { modelMenuExpanded = true }) {
                    Text(selectedModel?.name ?: "Select model")
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    (selectedProvider?.models ?: emptyList()).forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                modelMenuExpanded = false
                                viewModel.onModelSelected(model.id)
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = when (val cs = state.connectionState) {
                ConnectionState.Connected -> "Status: connected"
                ConnectionState.Connecting -> "Status: connecting"
                ConnectionState.Disconnected -> "Status: disconnected"
                is ConnectionState.Error -> "Status: error (${cs.message})"
                is ConnectionState.Reconnecting -> "Status: reconnecting attempt ${cs.attempt}"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        state.message?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

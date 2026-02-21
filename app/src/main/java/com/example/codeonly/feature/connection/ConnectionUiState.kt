package com.example.codeonly.feature.connection

import com.example.codeonly.domain.model.ConnectionState

data class ConnectionUiState(
    val serverUrl: String = "",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isBootstrapping: Boolean = true,
    val isConnected: Boolean = false,
    val version: String? = null,
    val providers: List<ProviderOption> = emptyList(),
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val isLoadingProviders: Boolean = false,
    val message: String? = null,
    val canSubmit: Boolean = true
)

package com.example.codeonly.feature.connection

import com.example.codeonly.domain.model.ConnectionState

internal fun reduceConnectionState(
    state: ConnectionUiState,
    intent: ConnectionIntent
): ConnectionUiState {
    return when (intent) {
        is ConnectionIntent.UrlChanged -> state.copy(serverUrl = intent.value)
        ConnectionIntent.TestConnectionClicked -> {
            if (state.serverUrl.isBlank()) {
                state.copy(message = "Please enter a server URL", isConnected = false)
            } else {
                state.copy(
                    connectionState = ConnectionState.Connecting,
                    isConnected = false,
                    version = null,
                    message = null,
                    canSubmit = false
                )
            }
        }
        ConnectionIntent.DisconnectClicked -> state.copy(
            isConnected = false,
            connectionState = ConnectionState.Disconnected,
            version = null,
            message = "Disconnected"
        )
        ConnectionIntent.ClearMessage -> state.copy(message = null)
    }
}

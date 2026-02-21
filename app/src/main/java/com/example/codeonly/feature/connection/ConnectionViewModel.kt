package com.example.codeonly.feature.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codeonly.api.OpenCodeClient
import com.example.codeonly.domain.model.ConnectionState
import com.example.codeonly.util.Preferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = Preferences(application)

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        bootstrap()
    }

    fun onIntent(intent: ConnectionIntent) {
        _uiState.update { reduceConnectionState(it, intent) }

        when (intent) {
            ConnectionIntent.TestConnectionClicked -> {
                if (_uiState.value.connectionState is ConnectionState.Connecting) {
                    testConnection()
                }
            }
            ConnectionIntent.DisconnectClicked -> disconnect()
            else -> Unit
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val savedUrl = preferences.baseUrl
            if (savedUrl.isBlank()) {
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isConnected = false,
                        connectionState = ConnectionState.Disconnected,
                        canSubmit = true
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    serverUrl = savedUrl,
                    connectionState = ConnectionState.Connecting,
                    isBootstrapping = true,
                    canSubmit = false,
                    message = null
                )
            }
            testConnection()
        }
    }

    private fun testConnection() {
        viewModelScope.launch {
            delay(150)
            val normalized = normalizeUrl(_uiState.value.serverUrl)
            if (normalized == null) {
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isConnected = false,
                        connectionState = ConnectionState.Error("URL must start with http:// or https://"),
                        message = "Invalid URL format",
                        canSubmit = true
                    )
                }
                return@launch
            }

            try {
                val health = OpenCodeClient(normalized).healthCheck()
                if (!health.healthy) {
                    _uiState.update {
                        it.copy(
                            isBootstrapping = false,
                            isConnected = false,
                            connectionState = ConnectionState.Error("Server reported unhealthy"),
                            message = "Server returned unhealthy status",
                            canSubmit = true
                        )
                    }
                    return@launch
                }

                preferences.baseUrl = normalized
                preferences.hasCompletedSetup = true

                _uiState.update {
                    it.copy(
                        serverUrl = normalized,
                        isBootstrapping = false,
                        isConnected = true,
                        version = health.version,
                        connectionState = ConnectionState.Connected,
                        message = "Connected to OpenCode ${health.version}",
                        canSubmit = true
                    )
                }
                refreshProviders()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isConnected = false,
                        connectionState = ConnectionState.Error(e.message ?: "Connection failed"),
                        message = "Connection failed: ${e.message}",
                        canSubmit = true
                    )
                }
            }
        }
    }

    private fun normalizeUrl(input: String): String? {
        var url = input.trim()
        if (url.isBlank()) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    private fun disconnect() {
        preferences.baseUrl = ""
        preferences.hasCompletedSetup = false
        _uiState.update {
            it.copy(
                serverUrl = "",
                isConnected = false,
                connectionState = ConnectionState.Disconnected,
                version = null,
                providers = emptyList(),
                selectedProviderId = null,
                selectedModelId = null,
                isLoadingProviders = false,
                canSubmit = true,
                isBootstrapping = false,
                message = "Disconnected. Enter a server URL to reconnect."
            )
        }
    }

    fun refreshProviders() {
        val baseUrl = preferences.baseUrl
        if (baseUrl.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProviders = true) }
            try {
                val response = OpenCodeClient(baseUrl).getProviders()
                val options = response.all.map { provider ->
                    ProviderOption(
                        id = provider.id,
                        name = provider.name.ifBlank { provider.id },
                        models = provider.models.values.map { model ->
                            ModelOption(
                                id = model.id,
                                name = model.name?.ifBlank { null } ?: model.id
                            )
                        }
                    )
                }

                val preferredProvider = preferences.lastProviderId
                    .takeIf { it.isNotBlank() }
                    ?: response.default["providerID"]
                    ?: response.default["provider"]
                    ?: options.firstOrNull()?.id

                val selectedProvider = options.firstOrNull { it.id == preferredProvider }
                    ?: options.firstOrNull()

                val preferredModel = preferences.lastModelId
                    .takeIf { it.isNotBlank() }
                    ?: response.default["modelID"]
                    ?: response.default["model"]
                    ?: selectedProvider?.models?.firstOrNull()?.id

                val selectedModel = selectedProvider?.models?.firstOrNull { it.id == preferredModel }
                    ?: selectedProvider?.models?.firstOrNull()

                if (selectedProvider != null) preferences.lastProviderId = selectedProvider.id
                if (selectedModel != null) preferences.lastModelId = selectedModel.id

                _uiState.update {
                    it.copy(
                        providers = options,
                        selectedProviderId = selectedProvider?.id,
                        selectedModelId = selectedModel?.id,
                        isLoadingProviders = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingProviders = false,
                        message = "Failed to load providers: ${e.message}"
                    )
                }
            }
        }
    }

    fun onProviderSelected(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId }
        val model = provider?.models?.firstOrNull()
        if (provider != null) preferences.lastProviderId = provider.id
        if (model != null) preferences.lastModelId = model.id

        _uiState.update {
            it.copy(
                selectedProviderId = provider?.id,
                selectedModelId = model?.id
            )
        }
    }

    fun onModelSelected(modelId: String) {
        preferences.lastModelId = modelId
        _uiState.update { it.copy(selectedModelId = modelId) }
    }
}

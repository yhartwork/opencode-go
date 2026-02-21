package com.example.codeonly.feature.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codeonly.api.OpenCodeClient
import com.example.codeonly.api.Part
import com.example.codeonly.domain.model.SessionListItem
import com.example.codeonly.util.AppLog
import com.example.codeonly.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = Preferences(application)

    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onIntent(intent: ChatsIntent) {
        _uiState.update { reduceChatsState(it, intent) }
        when (intent) {
            ChatsIntent.Refresh -> refresh()
            ChatsIntent.NewSession -> createSession()
            is ChatsIntent.SelectSession -> Unit
            is ChatsIntent.DeleteSession -> deleteSession(intent.sessionId)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val baseUrl = preferences.baseUrl
            if (baseUrl.isBlank()) {
                AppLog.warn("ChatsViewModel", "Refresh skipped: base URL not set")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = emptyList(),
                        selectedSessionId = null,
                        message = "No server connected. Configure URL in Settings."
                    )
                }
                return@launch
            }

            try {
                val client = OpenCodeClient(baseUrl)
                val sessions = client.listSessions()
                AppLog.info("ChatsViewModel", "Loaded ${sessions.size} sessions")
                val includeDetails = sessions.size <= 20
                val items = sessions.map { session ->
                    val summary = session.summary
                    val title = session.title?.ifBlank { null } ?: session.slug.ifBlank { "Untitled session" }
                    val details = if (includeDetails) {
                        fetchLatestDetails(client, session.id)
                    } else {
                        SessionDetails("No preview available yet", "model unknown")
                    }
                    SessionListItem(
                        id = session.id,
                        title = title,
                        preview = details.preview,
                        updatedAtLabel = formatTime(session.time.updated),
                        modelLabel = details.modelLabel,
                        changeSummary = "+${summary?.additions ?: 0} -${summary?.deletions ?: 0} (${summary?.files ?: 0} files)",
                        isStreaming = false,
                        hasError = false
                    )
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = items,
                        selectedSessionId = it.selectedSessionId ?: items.firstOrNull()?.id,
                        message = if (items.isEmpty()) "No sessions yet. Create one to start." else null
                    )
                }
            } catch (e: Exception) {
                AppLog.error("ChatsViewModel", "Failed to load sessions", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = emptyList(),
                        selectedSessionId = null,
                        message = "Failed to load sessions: ${e.message}"
                    )
                }
            }
        }
    }

    private fun createSession() {
        viewModelScope.launch {
            val baseUrl = preferences.baseUrl
            if (baseUrl.isBlank()) {
                AppLog.warn("ChatsViewModel", "Create session skipped: base URL not set")
                _uiState.update {
                    it.copy(isLoading = false, message = "No server connected. Configure URL in Settings.")
                }
                return@launch
            }

            try {
                val created = OpenCodeClient(baseUrl).createSession()
                AppLog.info("ChatsViewModel", "Created session ${created.id}")
                _uiState.update { it.copy(message = "Created session: ${created.title ?: created.slug}") }
                refresh()
            } catch (e: Exception) {
                AppLog.error("ChatsViewModel", "Failed to create session", e)
                _uiState.update {
                    it.copy(isLoading = false, message = "Failed to create session: ${e.message}")
                }
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val baseUrl = preferences.baseUrl
            if (baseUrl.isBlank()) {
                AppLog.warn("ChatsViewModel", "Delete session skipped: base URL not set")
                _uiState.update {
                    it.copy(isLoading = false, message = "No server connected. Configure URL in Settings.")
                }
                return@launch
            }

            try {
                val ok = OpenCodeClient(baseUrl).deleteSession(sessionId)
                AppLog.info("ChatsViewModel", "Delete session $sessionId: ${if (ok) "ok" else "failed"}")
                _uiState.update {
                    it.copy(message = if (ok) "Session deleted." else "Delete failed.")
                }
                refresh()
            } catch (e: Exception) {
                AppLog.error("ChatsViewModel", "Failed to delete session $sessionId", e)
                _uiState.update {
                    it.copy(isLoading = false, message = "Failed to delete session: ${e.message}")
                }
            }
        }
    }

    private fun formatTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "unknown time"
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }

    private suspend fun fetchLatestDetails(
        client: OpenCodeClient,
        sessionId: String
    ): SessionDetails {
        return try {
            val messages = withContext(Dispatchers.IO) { client.getSessionMessages(sessionId) }
            val lastMessage = messages.lastOrNull()
            val lastText = lastMessage?.parts
                ?.filterIsInstance<Part.Text>()
                ?.lastOrNull()
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?: "No preview available yet"

            val lastAssistant = messages.lastOrNull { it.info.role == "assistant" }
            val modelLabel = lastAssistant?.info?.model?.let { model ->
                val provider = model.providerID ?: "provider"
                val modelId = model.modelID ?: "model"
                "$provider / $modelId"
            } ?: "model unknown"

            SessionDetails(lastText, modelLabel)
        } catch (e: Exception) {
            SessionDetails("No preview available yet", "model unknown")
        }
    }
}

private data class SessionDetails(
    val preview: String,
    val modelLabel: String
)

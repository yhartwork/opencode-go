package com.example.codeonly.feature.chatdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.codeonly.api.ChatMessage
import com.example.codeonly.api.OpenCodeClient
import com.example.codeonly.api.Part
import com.example.codeonly.domain.model.TimelineEvent
import com.example.codeonly.domain.model.ToolStatus
import com.example.codeonly.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatDetailViewModel(
    application: Application,
    private val sessionId: String
) : AndroidViewModel(application) {
    private val preferences = Preferences(application)
    private val client by lazy { OpenCodeClient(preferences.baseUrl) }

    private val _uiState = MutableStateFlow(
        ChatDetailUiState(sessionId = sessionId, title = "Session $sessionId")
    )
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    private val streamingBuffer = mutableMapOf<String, String>()

    init {
        updateModelLabels()
        loadMessages()
        connectStream()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            if (preferences.baseUrl.isBlank()) {
                _uiState.update { it.copy(isLoading = false, message = "No server connected.") }
                return@launch
            }

            try {
                val messages = withContext(Dispatchers.IO) { client.getSessionMessages(sessionId) }
                val sessions = withContext(Dispatchers.IO) { client.listSessions() }
                val session = sessions.firstOrNull { it.id == sessionId }
                val title = session?.title?.ifBlank { null }
                    ?: session?.slug?.ifBlank { null }
                    ?: "Session $sessionId"
                val events = messages.flatMap { mapMessageToEvents(it) }
                _uiState.update {
                    it.copy(isLoading = false, events = events, title = title, message = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, message = "Failed to load messages: ${e.message}")
                }
            }
        }
    }

    private fun connectStream() {
        if (preferences.baseUrl.isBlank()) return

        if (!_uiState.value.streamEnabled) return

        client.connectEventSource(
            onDelta = { eventSessionId, messageId, delta ->
                if (eventSessionId != sessionId) return@connectEventSource
                appendStreamingDelta(messageId, delta)
            },
            onIdle = { eventSessionId ->
                if (eventSessionId != sessionId) return@connectEventSource
                addSystemEvent("Session idle")
            },
            onError = { eventSessionId, error ->
                if (eventSessionId != sessionId) return@connectEventSource
                addSystemEvent("Session error: $error")
            },
            onConnected = {
                _uiState.update { it.copy(streamConnected = true) }
                addSystemEvent("Connected to stream")
            },
            onDisconnected = {
                _uiState.update { it.copy(streamConnected = false) }
                addSystemEvent("Stream disconnected")
            }
        )
    }

    fun stopStreaming() {
        client.disconnectEventSource()
        _uiState.update { it.copy(streamEnabled = false, streamConnected = false) }
        addSystemEvent("Streaming paused")
    }

    fun startStreaming() {
        if (_uiState.value.streamEnabled) return
        _uiState.update { it.copy(streamEnabled = true) }
        connectStream()
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            val baseUrl = preferences.baseUrl
            if (baseUrl.isBlank()) {
                _uiState.update { it.copy(message = "No server connected.") }
                return@launch
            }

            val messageId = UUID.randomUUID().toString()
            val providerId = preferences.lastProviderId.takeIf { it.isNotBlank() }
            val modelId = preferences.lastModelId.takeIf { it.isNotBlank() }

            val userEvent = TimelineEvent.UserText(
                id = "local:$messageId",
                sessionId = sessionId,
                messageId = messageId,
                timestampMs = System.currentTimeMillis(),
                text = text
            )

            _uiState.update {
                it.copy(
                    inputText = "",
                    isSending = true,
                    message = null,
                    events = it.events + userEvent
                )
            }

            try {
                updateModelLabels()
                val ok = withContext(Dispatchers.IO) {
                    client.sendMessageAsync(
                        sessionId = sessionId,
                        text = text,
                        providerId = providerId,
                        modelId = modelId,
                        messageId = messageId
                    )
                }
                if (!ok) {
                    _uiState.update { it.copy(message = "Send failed.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed to send: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun appendStreamingDelta(messageId: String, delta: String) {
        val updated = (streamingBuffer[messageId] ?: "") + delta
        streamingBuffer[messageId] = updated

        _uiState.update { state ->
            val eventId = "stream:$messageId"
            val existing = state.events.indexOfFirst { it.id == eventId }
            val nextEvent = TimelineEvent.AssistantText(
                id = eventId,
                sessionId = sessionId,
                messageId = messageId,
                timestampMs = null,
                text = updated,
                isStreaming = true
            )

            val nextEvents = if (existing >= 0) {
                state.events.toMutableList().apply { set(existing, nextEvent) }
            } else {
                state.events + nextEvent
            }

            state.copy(events = nextEvents)
        }
    }

    private fun addSystemEvent(text: String) {
        _uiState.update { state ->
            val event = TimelineEvent.SystemEvent(
                id = "system:${System.currentTimeMillis()}",
                sessionId = sessionId,
                messageId = null,
                timestampMs = System.currentTimeMillis(),
                text = text
            )
            state.copy(events = state.events + event)
        }
    }

    private fun mapMessageToEvents(message: ChatMessage): List<TimelineEvent> {
        val role = message.info.role ?: "assistant"
        return message.parts.map { part ->
            val eventId = "${message.info.id ?: message.info.sessionID}:${part.id}"
            when (part) {
                is Part.Text -> {
                    if (role == "user") {
                        TimelineEvent.UserText(
                            id = eventId,
                            sessionId = message.info.sessionID ?: sessionId,
                            messageId = message.info.id,
                            timestampMs = part.time?.start,
                            text = part.text
                        )
                    } else {
                        TimelineEvent.AssistantText(
                            id = eventId,
                            sessionId = message.info.sessionID ?: sessionId,
                            messageId = message.info.id,
                            timestampMs = part.time?.start,
                            text = part.text,
                            isStreaming = false
                        )
                    }
                }
                is Part.Reasoning -> TimelineEvent.Reasoning(
                    id = eventId,
                    sessionId = message.info.sessionID ?: sessionId,
                    messageId = message.info.id,
                    timestampMs = part.time?.start,
                    text = part.text
                )
                is Part.Tool -> TimelineEvent.ToolCall(
                    id = eventId,
                    sessionId = message.info.sessionID ?: sessionId,
                    messageId = message.info.id,
                    timestampMs = part.time?.start,
                    name = part.name ?: "tool",
                    input = part.input,
                    status = when (part.status) {
                        "pending" -> ToolStatus.Pending
                        "running" -> ToolStatus.Running
                        "completed" -> ToolStatus.Completed
                        "error" -> ToolStatus.Error
                        else -> ToolStatus.Completed
                    }
                )
                is Part.ToolResult -> TimelineEvent.ToolResult(
                    id = eventId,
                    sessionId = message.info.sessionID ?: sessionId,
                    messageId = message.info.id,
                    timestampMs = part.time?.start,
                    toolCallId = part.toolCallID ?: "",
                    output = part.result,
                    isError = part.isError == true
                )
                is Part.Other -> TimelineEvent.SystemEvent(
                    id = eventId,
                    sessionId = message.info.sessionID ?: sessionId,
                    messageId = message.info.id,
                    timestampMs = null,
                    text = "Unsupported part: ${part.type}"
                )
            }
        }
    }

    override fun onCleared() {
        client.disconnectEventSource()
        super.onCleared()
    }

    private fun updateModelLabels() {
        val provider = preferences.lastProviderId.takeIf { it.isNotBlank() } ?: "provider unknown"
        val model = preferences.lastModelId.takeIf { it.isNotBlank() } ?: "model unknown"
        _uiState.update {
            it.copy(providerLabel = provider, modelLabel = model)
        }
    }
}

class ChatDetailViewModelFactory(
    private val application: Application,
    private val sessionId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatDetailViewModel(application, sessionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

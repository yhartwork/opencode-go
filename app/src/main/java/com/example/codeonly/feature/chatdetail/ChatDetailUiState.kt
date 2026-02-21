package com.example.codeonly.feature.chatdetail

import com.example.codeonly.domain.model.TimelineEvent

data class ChatDetailUiState(
    val sessionId: String,
    val title: String,
    val isLoading: Boolean = true,
    val events: List<TimelineEvent> = emptyList(),
    val message: String? = null,
    val inputText: String = "",
    val isSending: Boolean = false,
    val streamEnabled: Boolean = true,
    val streamConnected: Boolean = false,
    val providerLabel: String = "provider unknown",
    val modelLabel: String = "model unknown"
)

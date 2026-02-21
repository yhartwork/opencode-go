package com.example.codeonly.feature.chats

import com.example.codeonly.domain.model.SessionListItem

data class ChatsUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionListItem> = emptyList(),
    val selectedSessionId: String? = null,
    val message: String? = null
)

package com.example.codeonly.feature.chats

sealed interface ChatsIntent {
    data object Refresh : ChatsIntent
    data object NewSession : ChatsIntent
    data class SelectSession(val sessionId: String) : ChatsIntent
    data class DeleteSession(val sessionId: String) : ChatsIntent
}

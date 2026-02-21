package com.example.codeonly.feature.chats

internal fun reduceChatsState(state: ChatsUiState, intent: ChatsIntent): ChatsUiState {
    return when (intent) {
        ChatsIntent.Refresh -> state.copy(isLoading = true, message = null)
        ChatsIntent.NewSession -> state.copy(message = "Creating session...", isLoading = true)
        is ChatsIntent.SelectSession -> state.copy(selectedSessionId = intent.sessionId)
        is ChatsIntent.DeleteSession -> state.copy(message = "Deleting session...", isLoading = true)
    }
}

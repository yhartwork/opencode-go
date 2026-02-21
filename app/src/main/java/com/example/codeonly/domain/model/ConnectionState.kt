package com.example.codeonly.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int, val nextDelayMs: Long) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

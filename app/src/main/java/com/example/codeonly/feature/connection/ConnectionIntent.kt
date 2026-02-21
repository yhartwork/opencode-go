package com.example.codeonly.feature.connection

sealed interface ConnectionIntent {
    data class UrlChanged(val value: String) : ConnectionIntent
    data object TestConnectionClicked : ConnectionIntent
    data object DisconnectClicked : ConnectionIntent
    data object ClearMessage : ConnectionIntent
}

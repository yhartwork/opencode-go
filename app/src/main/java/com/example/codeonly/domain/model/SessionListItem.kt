package com.example.codeonly.domain.model

data class SessionListItem(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtLabel: String,
    val modelLabel: String,
    val changeSummary: String,
    val isStreaming: Boolean,
    val hasError: Boolean
)

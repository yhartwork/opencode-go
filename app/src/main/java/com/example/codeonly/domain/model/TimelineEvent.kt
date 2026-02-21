package com.example.codeonly.domain.model

sealed interface TimelineEvent {
    val id: String
    val sessionId: String
    val messageId: String?
    val timestampMs: Long?

    data class UserText(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val text: String
    ) : TimelineEvent

    data class AssistantText(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val text: String,
        val isStreaming: Boolean
    ) : TimelineEvent

    data class Reasoning(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val text: String,
        val collapsedByDefault: Boolean = true
    ) : TimelineEvent

    data class ToolCall(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val name: String,
        val input: String?,
        val status: ToolStatus
    ) : TimelineEvent

    data class ToolResult(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val toolCallId: String,
        val output: String?,
        val isError: Boolean
    ) : TimelineEvent

    data class PatchSummary(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val filesChanged: Int,
        val additions: Int,
        val deletions: Int
    ) : TimelineEvent

    data class ErrorEvent(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val title: String,
        val details: String?
    ) : TimelineEvent

    data class SystemEvent(
        override val id: String,
        override val sessionId: String,
        override val messageId: String?,
        override val timestampMs: Long?,
        val text: String
    ) : TimelineEvent
}

enum class ToolStatus {
    Pending,
    Running,
    Completed,
    Error
}

package com.example.codeonly.data

import com.example.codeonly.domain.model.SessionListItem

object SampleDataRepository {
    fun sessionList(): List<SessionListItem> {
        return listOf(
            SessionListItem(
                id = "s_1",
                title = "Fix flaky Android CI job",
                preview = "I traced the timeout to gradle cache misses on cold runners.",
                updatedAtLabel = "2m ago",
                modelLabel = "openai / gpt-5",
                changeSummary = "+42 -9 (3 files)",
                isStreaming = true,
                hasError = false
            ),
            SessionListItem(
                id = "s_2",
                title = "Refactor SSE reconnection",
                preview = "Added jitter to exponential backoff and surfaced status in UI.",
                updatedAtLabel = "18m ago",
                modelLabel = "anthropic / claude",
                changeSummary = "+18 -12 (2 files)",
                isStreaming = false,
                hasError = false
            ),
            SessionListItem(
                id = "s_3",
                title = "Investigate provider auth mismatch",
                preview = "Server returned unauthorized for provider profile fallback.",
                updatedAtLabel = "1h ago",
                modelLabel = "xai / grok",
                changeSummary = "+6 -3 (1 file)",
                isStreaming = false,
                hasError = true
            )
        )
    }
}

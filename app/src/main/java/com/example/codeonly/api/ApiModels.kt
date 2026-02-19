package com.example.codeonly.api

data class HealthResponse(
    val healthy: Boolean,
    val version: String
)

data class Project(
    val id: String,
    val worktree: String,
    val vcs: String,
    val name: String?,
    val icon: Icon?,
    val commands: Commands?,
    val time: ProjectTime,
    val sandboxes: List<String>
)

data class Icon(
    val url: String?,
    val override: String?,
    val color: String?
)

data class Commands(
    val start: String?
)

data class ProjectTime(
    val created: Long,
    val updated: Long,
    val initialized: Long?
)

data class PathInfo(
    val home: String,
    val state: String,
    val config: String,
    val worktree: String,
    val directory: String
)

data class Session(
    val id: String,
    val slug: String,
    val projectID: String?,
    val directory: String?,
    val parentID: String?,
    val summary: SessionSummary?,
    val share: Share?,
    val title: String?,
    val version: String,
    val time: SessionTime,
    val permission: List<PermissionRule>?,
    val revert: RevertInfo?
)

data class SessionSummary(
    val additions: Int,
    val deletions: Int,
    val files: Int,
    val diffs: List<FileDiff>?
)

data class FileDiff(
    val path: String?,
    val hunks: List<DiffHunk>?
)

data class DiffHunk(
    val header: String?,
    val lines: List<DiffLine>?
)

data class DiffLine(
    val prefix: String?,
    val content: String?
)

data class Share(
    val url: String
)

data class SessionTime(
    val created: Long,
    val updated: Long,
    val compacting: Long?,
    val archived: Long?
)

data class PermissionRule(
    val id: String?,
    val rule: String?,
    val description: String?
)

data class RevertInfo(
    val messageID: String?,
    val partID: String?,
    val snapshot: String?,
    val diff: String?
)

data class ChatMessage(
    val info: MessageInfo,
    val parts: List<Part>
)

data class MessageInfo(
    val id: String?,
    val sessionID: String?,
    val role: String?,
    val variant: String?,
    val model: ModelInfo?,
    val time: MessageTime?
)

data class ModelInfo(
    val providerID: String?,
    val modelID: String?
)

data class MessageTime(
    val start: Long,
    val end: Long?
)

sealed class Part {
    abstract val id: String
    abstract val sessionID: String?
    abstract val messageID: String?

    data class Text(
        override val id: String,
        override val sessionID: String?,
        override val messageID: String?,
        val text: String,
        val synthetic: Boolean?,
        val ignored: Boolean?,
        val time: PartTime?
    ) : Part()

    data class Reasoning(
        override val id: String,
        override val sessionID: String?,
        override val messageID: String?,
        val text: String,
        val metadata: Map<String, Any>?,
        val time: PartTime?
    ) : Part()

    data class Tool(
        override val id: String,
        override val sessionID: String?,
        override val messageID: String?,
        val toolCallID: String?,
        val input: String?,
        val name: String?,
        val status: String?,
        val result: String?,
        val time: PartTime?
    ) : Part()

    data class ToolResult(
        override val id: String,
        override val sessionID: String?,
        override val messageID: String?,
        val toolCallID: String?,
        val result: String?,
        val isError: Boolean?,
        val time: PartTime?
    ) : Part()

    data class Other(
        override val id: String,
        override val sessionID: String?,
        override val messageID: String?,
        val type: String,
        val data: Map<String, Any>?
    ) : Part()
}

data class PartTime(
    val start: Long,
    val end: Long?
)

data class ProviderInfo(
    val id: String,
    val name: String,
    val source: String,
    val env: List<String>,
    val key: String?,
    val options: Map<String, Any>,
    val models: Map<String, Model>
)

data class Model(
    val id: String,
    val providerID: String?,
    val api: ModelApi?,
    val name: String?,
    val family: String?,
    val capabilities: Capabilities?,
    val cost: Cost?,
    val releaseDate: String?,
    val attachment: Boolean?,
    val reasoning: Boolean?,
    val temperature: Boolean?,
    val toolCall: Boolean?,
    val limit: Limit?,
    val modalities: Modalities?,
    val experimental: Boolean?,
    val status: String?,
    val options: Map<String, Any>?,
    val headers: Map<String, String>?,
    val provider: ModelProvider?,
    val variants: Map<String, Map<String, Any>>?
)

data class ModelApi(
    val id: String?,
    val url: String?,
    val npm: String?
)

data class Capabilities(
    val temperature: Boolean?,
    val reasoning: Boolean?,
    val attachment: Boolean?,
    val toolcall: Boolean?,
    val input: InputOutput?,
    val output: InputOutput?,
    val interleaved: Any?
)

data class InputOutput(
    val text: Boolean?,
    val audio: Boolean?,
    val image: Boolean?,
    val video: Boolean?,
    val pdf: Boolean?
)

data class Cost(
    val input: Double?,
    val output: Double?,
    val cache: CacheCost?
)

data class CacheCost(
    val read: Double?,
    val write: Double?
)

data class Limit(
    val context: Int?,
    val input: Int?,
    val output: Int?
)

data class Modalities(
    val input: List<String>?,
    val output: List<String>?
)

data class ModelProvider(
    val npm: String?,
    val api: String?
)

data class ProvidersResponse(
    val all: List<ProviderInfo>,
    val default: Map<String, String>,
    val connected: List<String>
)

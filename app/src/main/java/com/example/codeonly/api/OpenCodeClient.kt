package com.example.codeonly.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenCodeClient(private var baseUrl: String) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var eventSource: EventSource? = null

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getBaseUrl(): String = baseUrl

    private fun buildUrl(path: String): String = "$baseUrl$path"

    suspend fun healthCheck(): HealthResponse = withContext(Dispatchers.IO) {
        val response = get(buildUrl("/global/health"))
        HealthResponse(
            healthy = response.optBoolean("healthy", false),
            version = response.optString("version", "")
        )
    }

    suspend fun getCurrentProject(): Project? = withContext(Dispatchers.IO) {
        try {
            val response = get(buildUrl("/project/current"))
            Project(
                id = response.optString("id", ""),
                worktree = response.optString("worktree", ""),
                vcs = response.optString("vcs", ""),
                name = response.optString("name"),
                icon = null,
                commands = null,
                time = ProjectTime(
                    created = response.optJSONObject("time")?.optLong("created") ?: 0L,
                    updated = response.optJSONObject("time")?.optLong("updated") ?: 0L,
                    initialized = response.optJSONObject("time")?.optLong("initialized")
                ),
                sandboxes = response.optJSONArray("sandboxes")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPathInfo(): PathInfo? = withContext(Dispatchers.IO) {
        try {
            val response = get(buildUrl("/path"))
            PathInfo(
                home = response.optString("home", ""),
                state = response.optString("state", ""),
                config = response.optString("config", ""),
                worktree = response.optString("worktree", ""),
                directory = response.optString("directory", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun listSessions(): List<Session> = withContext(Dispatchers.IO) {
        val response = get(buildUrl("/session"))
        val arr = response as? JSONArray ?: JSONArray()
        (0 until arr.length()).map { parseSession(arr.getJSONObject(it)) }
    }

    suspend fun createSession(title: String? = null): Session = withContext(Dispatchers.IO) {
        val body = JSONObject()
        title?.let { body.put("title", it) }
        val response = post(buildUrl("/session"), body)
        parseSession(response)
    }

    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val response = delete(buildUrl("/session/$sessionId"))
        response.optBoolean("success", false) || response.toString().isEmpty()
    }

    suspend fun getSessionMessages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val response = get(buildUrl("/session/$sessionId/message"))
        val arr = response as? JSONArray ?: JSONArray()
        (0 until arr.length()).map { parseChatMessage(arr.getJSONObject(it)) }
    }

    suspend fun initSession(
        sessionId: String,
        providerId: String,
        modelId: String,
        messageId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("providerID", providerId)
            .put("modelID", modelId)
            .put("messageID", messageId)
        val response = post(buildUrl("/session/$sessionId/init"), body)
        response.optBoolean("success", false) || response.toString().isEmpty()
    }

    suspend fun sendMessageAsync(
        sessionId: String,
        text: String,
        providerId: String?,
        modelId: String?,
        messageId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
            .put("messageID", messageId)

        if (providerId != null && modelId != null) {
            body.put("model", JSONObject()
                .put("providerID", providerId)
                .put("modelID", modelId))
        }

        post(buildUrl("/session/$sessionId/prompt_async"), body)
        true
    }

    suspend fun getProviders(): ProvidersResponse = withContext(Dispatchers.IO) {
        val response = get(buildUrl("/provider"))
        val allArr = response.optJSONArray("all") ?: JSONArray()
        val all = (0 until allArr.length()).map { i ->
            val obj = allArr.getJSONObject(i)
            val modelsObj = obj.optJSONObject("models") ?: JSONObject()
            val models = (0 until modelsObj.length()).associate { j ->
                val key = modelsObj.keys().asSequence().toList()[j]
                key to parseModel(modelsObj.getJSONObject(key), key)
            }
            ProviderInfo(
                id = obj.optString("id", ""),
                name = obj.optString("name", ""),
                source = obj.optString("source", ""),
                env = obj.optJSONArray("env")?.let { (0 until it.length()).map { k -> it.getString(k) } } ?: emptyList(),
                key = obj.optString("key"),
                options = obj.optJSONObject("options")?.toMap() ?: emptyMap(),
                models = models
            )
        }
        val defaultMap = response.optJSONObject("default")?.toMap() ?: emptyMap()
        val connected = response.optJSONArray("connected")?.let { (0 until it.length()).map { k -> it.getString(k) } } ?: emptyList()
        ProvidersResponse(all, defaultMap, connected)
    }

    fun connectEventSource(
        onDelta: (sessionId: String, messageId: String, delta: String) -> Unit,
        onIdle: (sessionId: String) -> Unit,
        onError: (sessionId: String, error: String) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ) {
        disconnectEventSource()
        val httpUrl = okhttp3.HttpUrl.parse(buildUrl("/event")) 
            ?: throw IOException("Invalid event URL")
        val request = Request.Builder().url(httpUrl).get().build()
        eventSource = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val payload = JSONObject(data)
                    val eventType = payload.optString("type")
                    val props = payload.optJSONObject("properties") ?: return
                    val sessionId = props.optString("sessionID")

                    when (eventType) {
                        "message.part.delta" -> {
                            val msgId = props.optString("messageID")
                            val delta = props.optString("delta")
                            if (delta.isNotEmpty()) {
                                onDelta(sessionId, msgId, delta)
                            }
                        }
                        "session.idle" -> onIdle(sessionId)
                        "session.error" -> {
                            val error = props.optString("error", "Unknown error")
                            onError(sessionId, error)
                        }
                    }
                } catch (e: Exception) {
                    // ignore parse errors
                }
            }

            override fun onOpen(eventSource: EventSource) {
                onConnected()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                onDisconnected()
            }
        })
    }

    fun disconnectEventSource() {
        eventSource?.cancel()
        eventSource = null
    }

    fun isEventSourceConnected(): Boolean = eventSource?.isClosed == false

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            try {
                JSONObject(raw)
            } catch (e: Exception) {
                JSONObject()
            }
        }
    }

    private suspend fun post(url: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            try {
                JSONObject(raw)
            } catch (e: Exception) {
                JSONObject()
            }
        }
    }

    private suspend fun delete(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).delete().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful && response.code != 204) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            try {
                if (raw.isNotEmpty()) JSONObject(raw) else JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }
        }
    }

    private fun parseSession(json: JSONObject): Session {
        return Session(
            id = json.optString("id", ""),
            slug = json.optString("slug", ""),
            projectID = json.optString("projectID"),
            directory = json.optString("directory"),
            parentID = json.optString("parentID"),
            summary = json.optJSONObject("summary")?.let { s ->
                SessionSummary(
                    additions = s.optInt("additions", 0),
                    deletions = s.optInt("deletions", 0),
                    files = s.optInt("files", 0),
                    diffs = s.optJSONArray("diffs")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val d = arr.getJSONObject(i)
                            FileDiff(
                                path = d.optString("path"),
                                hunks = d.optJSONArray("hunks")?.let { harr ->
                                    (0 until harr.length()).map { h ->
                                        val hunk = harr.getJSONObject(h)
                                        DiffHunk(
                                            header = hunk.optString("header"),
                                            lines = hunk.optJSONArray("lines")?.let { larr ->
                                                (0 until larr.length()).map { l ->
                                                    val line = larr.getJSONObject(l)
                                                    DiffLine(line.optString("prefix"), line.optString("content"))
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            },
            share = json.optJSONObject("share")?.let { Share(it.optString("url", "")) },
            title = json.optString("title"),
            version = json.optString("version", ""),
            time = SessionTime(
                created = json.optJSONObject("time")?.optLong("created") ?: 0L,
                updated = json.optJSONObject("time")?.optLong("updated") ?: 0L,
                compacting = json.optJSONObject("time")?.optLong("compacting"),
                archived = json.optJSONObject("time")?.optLong("archived")
            ),
            permission = json.optJSONArray("permission")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    PermissionRule(p.optString("id"), p.optString("rule"), p.optString("description"))
                }
            },
            revert = json.optJSONObject("revert")?.let { r ->
                RevertInfo(r.optString("messageID"), r.optString("partID"), r.optString("snapshot"), r.optString("diff"))
            }
        )
    }

    private fun parseChatMessage(json: JSONObject): ChatMessage {
        val info = json.optJSONObject("info") ?: JSONObject()
        val partsArr = json.optJSONArray("parts") ?: JSONArray()
        val parts = (0 until partsArr.length()).map { parsePart(partsArr.getJSONObject(it)) }
        return ChatMessage(
            info = MessageInfo(
                id = info.optString("id"),
                sessionID = info.optString("sessionID"),
                role = info.optString("role"),
                variant = info.optString("variant"),
                model = info.optJSONObject("model")?.let { m ->
                    ModelInfo(m.optString("providerID"), m.optString("modelID"))
                },
                time = info.optJSONObject("time")?.let { t ->
                    MessageTime(t.optLong("start", 0L), t.optLong("end"))
                }
            ),
            parts = parts
        )
    }

    private fun parsePart(json: JSONObject): Part {
        val id = json.optString("id", "")
        val sessionID = json.optString("sessionID")
        val messageID = json.optString("messageID")
        val type = json.optString("type", "")
        return when (type) {
            "text" -> Part.Text(
                id, sessionID, messageID,
                json.optString("text", ""),
                json.optBoolean("synthetic", false),
                json.optBoolean("ignored", false),
                json.optJSONObject("time")?.let { PartTime(it.optLong("start", 0L), it.optLong("end")) }
            )
            "reasoning" -> Part.Reasoning(
                id, sessionID, messageID,
                json.optString("text", ""),
                json.optJSONObject("metadata")?.toMap(),
                json.optJSONObject("time")?.let { PartTime(it.optLong("start", 0L), it.optLong("end")) }
            )
            "tool_call" -> Part.Tool(
                id, sessionID, messageID,
                json.optString("toolCallID"),
                json.optString("input"),
                json.optString("name"),
                json.optString("status"),
                json.optString("result"),
                json.optJSONObject("time")?.let { PartTime(it.optLong("start", 0L), it.optLong("end")) }
            )
            "tool_result" -> Part.ToolResult(
                id, sessionID, messageID,
                json.optString("toolCallID"),
                json.optString("result"),
                json.optBoolean("isError", false),
                json.optJSONObject("time")?.let { PartTime(it.optLong("start", 0L), it.optLong("end")) }
            )
            else -> Part.Other(id, sessionID, messageID, type, json.toMap())
        }
    }

    private fun parseModel(json: JSONObject, id: String): Model {
        return Model(
            id = id,
            providerID = json.optString("providerID"),
            api = json.optJSONObject("api")?.let {
                ModelApi(it.optString("id"), it.optString("url"), it.optString("npm"))
            },
            name = json.optString("name"),
            family = json.optString("family"),
            capabilities = json.optJSONObject("capabilities")?.let { c ->
                Capabilities(
                    c.optBoolean("temperature"),
                    c.optBoolean("reasoning"),
                    c.optBoolean("attachment"),
                    c.optBoolean("toolcall"),
                    c.optJSONObject("input")?.let { InputOutput(it.optBoolean("text"), it.optBoolean("audio"), it.optBoolean("image"), it.optBoolean("video"), it.optBoolean("pdf")) },
                    c.optJSONObject("output")?.let { InputOutput(it.optBoolean("text"), it.optBoolean("audio"), it.optBoolean("image"), it.optBoolean("video"), it.optBoolean("pdf")) },
                    c.opt("interleaved")
                )
            },
            cost = json.optJSONObject("cost")?.let { co ->
                Cost(co.optDouble("input"), co.optDouble("output"), co.optJSONObject("cache")?.let { CacheCost(it.optDouble("read"), it.optDouble("write")) })
            },
            releaseDate = json.optString("release_date"),
            attachment = json.optBoolean("attachment"),
            reasoning = json.optBoolean("reasoning"),
            temperature = json.optBoolean("temperature"),
            toolCall = json.optBoolean("tool_call"),
            limit = json.optJSONObject("limit")?.let { Limit(it.optInt("context"), it.optInt("input"), it.optInt("output")) },
            modalities = json.optJSONObject("modalities")?.let { Modalities(it.optJSONArray("input")?.let { arr -> (0 until arr.length()).map { i -> arr.getString(i) } }, it.optJSONArray("output")?.let { arr -> (0 until arr.length()).map { i -> arr.getString(i) } }) },
            experimental = json.optBoolean("experimental"),
            status = json.optString("status"),
            options = json.optJSONObject("options")?.toMap(),
            headers = json.optJSONObject("headers")?.toMapString(),
            provider = json.optJSONObject("provider")?.let { ModelProvider(it.optString("npm"), it.optString("api")) },
            variants = json.optJSONObject("variants")?.toMapStringAny()
        )
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            val value = opt(key)
            map[key] = when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value
            }
        }
        return map
    }

    private fun JSONObject.toMapString(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        keys().forEach { key ->
            map[key] = optString(key, "")
        }
        return map
    }

    private fun JSONObject.toMapStringAny(): Map<String, Map<String, Any>> {
        val map = mutableMapOf<String, Map<String, Any>>()
        keys().forEach { key ->
            val value = opt(key)
            if (value is JSONObject) {
                map[key] = value.toMap()
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        return (0 until length()).map { opt(it) }
    }
}

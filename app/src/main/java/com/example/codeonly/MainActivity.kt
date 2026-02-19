package com.example.codeonly

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private lateinit var baseUrlInput: EditText
    private lateinit var promptInput: EditText
    private lateinit var statusText: TextView
    private lateinit var endpointsText: TextView
    private lateinit var chatText: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var profileSpinner: Spinner
    private lateinit var providerSpinner: Spinner
    private lateinit var modelSpinner: Spinner

    private val profiles = mutableListOf<String>()
    private val providerItems = mutableListOf<String>()
    private val modelItems = mutableListOf<String>()
    private val modelMap = mutableMapOf<String, Pair<String, String>>()

    private lateinit var profileAdapter: ArrayAdapter<String>
    private lateinit var providerAdapter: ArrayAdapter<String>
    private lateinit var modelAdapter: ArrayAdapter<String>

    private var sessionId: String? = null
    private var eventSource: EventSource? = null
    private var pendingStreamMessageId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        baseUrlInput = findViewById(R.id.baseUrlInput)
        promptInput = findViewById(R.id.promptInput)
        statusText = findViewById(R.id.statusText)
        endpointsText = findViewById(R.id.endpointsText)
        chatText = findViewById(R.id.chatText)
        chatScroll = findViewById(R.id.chatScroll)
        profileSpinner = findViewById(R.id.profileSpinner)
        providerSpinner = findViewById(R.id.providerSpinner)
        modelSpinner = findViewById(R.id.modelSpinner)

        profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profiles).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            profileSpinner.adapter = it
        }
        providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providerItems).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            providerSpinner.adapter = it
        }
        modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelItems).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = it
        }

        baseUrlInput.setText("https://opencode.ai")
        loadProfiles()

        findViewById<Button>(R.id.saveProfileButton).setOnClickListener {
            saveCurrentProfile()
        }
        findViewById<Button>(R.id.loadSpecButton).setOnClickListener {
            loadOpenApiSpec()
        }
        findViewById<Button>(R.id.createSessionButton).setOnClickListener {
            createSession()
        }
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            sendMessage()
        }
        findViewById<Button>(R.id.loadModelsButton).setOnClickListener {
            loadProvidersAndModels()
        }

        profileSpinner.setOnItemSelectedListener(SimpleItemSelectedListener { selected ->
            if (selected.isNotBlank()) {
                baseUrlInput.setText(selected)
            }
        })

        providerSpinner.setOnItemSelectedListener(SimpleItemSelectedListener {
            rebuildModelList(it.substringBefore(" "))
        })
    }

    override fun onDestroy() {
        eventSource?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun normalizedBaseUrl(): String {
        return baseUrlInput.text.toString().trim().trimEnd('/')
    }

    private fun loadOpenApiSpec() {
        val baseUrl = normalizedBaseUrl()
        if (baseUrl.isBlank()) {
            setStatus("Please set a base URL")
            return
        }
        setStatus("Loading OpenAPI spec...")
        scope.launch {
            runCatching {
                val response = getJson("$baseUrl/openapi.json")
                val paths = response.optJSONObject("paths") ?: JSONObject()
                val lines = mutableListOf<String>()
                val keys = paths.keys().asSequence().toList().sorted()
                for (path in keys) {
                    val methodsObj = paths.optJSONObject(path) ?: continue
                    val methods = methodsObj.keys().asSequence()
                        .map { it.uppercase() }
                        .filter { it in setOf("GET", "POST", "PUT", "PATCH", "DELETE") }
                        .sorted()
                        .joinToString(",")
                    lines.add(if (methods.isBlank()) path else "$methods $path")
                }
                lines
            }.onSuccess { lines ->
                endpointsText.text = if (lines.isEmpty()) "No endpoints found" else lines.joinToString("\n")
                setStatus("Loaded ${lines.size} endpoints")
            }.onFailure { err ->
                setStatus("Failed to load spec: ${err.message}")
            }
        }
    }

    private fun createSession() {
        val baseUrl = normalizedBaseUrl()
        if (baseUrl.isBlank()) {
            setStatus("Please set a base URL")
            return
        }
        setStatus("Creating session...")
        scope.launch {
            runCatching {
                val response = postJson("$baseUrl/session", JSONObject())
                val id = response.optString("id", "")
                if (id.isBlank()) throw IOException("Session ID missing in response")
                ensureEventStream(baseUrl)
                id
            }.onSuccess { id ->
                sessionId = id
                appendChat("System", "Session created: $id")
                setStatus("Session ready")
            }.onFailure { err ->
                setStatus("Failed to create session: ${err.message}")
            }
        }
    }

    private fun sendMessage() {
        val baseUrl = normalizedBaseUrl()
        val sid = sessionId
        val prompt = promptInput.text.toString().trim()
        if (baseUrl.isBlank()) {
            setStatus("Please set a base URL")
            return
        }
        if (sid.isNullOrBlank()) {
            setStatus("Create a session first")
            return
        }
        if (prompt.isBlank()) {
            setStatus("Type a message first")
            return
        }

        appendChat("You", prompt)
        promptInput.setText("")
        setStatus("Sending message...")

        scope.launch {
            runCatching {
                val modelSelection = modelSpinner.selectedItem?.toString().orEmpty()
                val modelPair = modelMap[modelSelection]
                val body = JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
                )
                if (modelPair != null) {
                    body.put(
                        "model",
                        JSONObject()
                            .put("providerID", modelPair.first)
                            .put("modelID", modelPair.second)
                    )
                }
                val messageId = "msg_${System.currentTimeMillis()}"
                body.put("messageID", messageId)
                pendingStreamMessageId = messageId
                ensureEventStream(baseUrl)
                postJson("$baseUrl/session/$sid/prompt_async", body)
                ""
            }.onSuccess { assistantText ->
                if (assistantText.isNotBlank()) {
                    appendChat("OpenCode", assistantText)
                }
                setStatus("Message submitted (streaming)")
            }.onFailure { err ->
                appendChat("System", "Request failed: ${err.message}")
                setStatus("Request failed")
            }
        }
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            JSONObject(raw)
        }
    }

    private suspend fun postJson(url: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            JSONObject(raw)
        }
    }

    private fun extractAssistantText(response: JSONObject): String {
        val parts = response.optJSONArray("parts") ?: return response.toString(2)
        val texts = mutableListOf<String>()
        for (i in 0 until parts.length()) {
            val obj = parts.optJSONObject(i) ?: continue
            when (obj.optString("type")) {
                "text" -> texts.add(obj.optString("text"))
                "reasoning" -> texts.add(obj.optString("text"))
            }
        }
        return texts.joinToString("\n").trim()
    }

    private fun ensureEventStream(baseUrl: String) {
        if (eventSource != null) return
        val httpUrl = "$baseUrl/event".toHttpUrlOrNull() ?: throw IOException("Invalid base URL")
        val request = Request.Builder().url(httpUrl).get().build()
        eventSource = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                runCatching {
                    handleSseEvent(data)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                runOnUiThread {
                    setStatus("Event stream disconnected")
                }
                this@MainActivity.eventSource = null
            }
        })
    }

    private fun handleSseEvent(data: String) {
        val payload = JSONObject(data)
        val eventType = payload.optString("type")
        if (eventType == "message.part.delta") {
            val props = payload.optJSONObject("properties") ?: return
            val sid = props.optString("sessionID")
            val mid = props.optString("messageID")
            val delta = props.optString("delta")
            val currentSid = sessionId ?: return
            val pendingMid = pendingStreamMessageId
            if (sid == currentSid && (pendingMid == null || pendingMid == mid) && delta.isNotEmpty()) {
                runOnUiThread {
                    appendStreamDelta(delta)
                }
            }
            return
        }
        if (eventType == "session.idle") {
            val props = payload.optJSONObject("properties") ?: return
            val sid = props.optString("sessionID")
            if (sid == sessionId) {
                pendingStreamMessageId = null
                runOnUiThread { appendChat("System", "Response complete") }
            }
        }
    }

    private fun appendStreamDelta(delta: String) {
        val current = chatText.text.toString()
        val marker = "OpenCode:"
        val next = if (current.endsWith(marker) || current.contains("\n\n$marker")) {
            "$current$delta"
        } else {
            if (current == "Chat log") "$marker$delta" else "$current\n\n$marker$delta"
        }
        chatText.text = next
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun saveCurrentProfile() {
        val value = normalizedBaseUrl()
        if (value.isBlank()) {
            setStatus("Base URL is empty")
            return
        }
        if (!profiles.contains(value)) {
            profiles.add(value)
            profiles.sort()
            profileAdapter.notifyDataSetChanged()
        }
        getSharedPreferences("opencode_profiles", MODE_PRIVATE)
            .edit()
            .putStringSet("urls", profiles.toSet())
            .apply()
        setStatus("Saved URL profile")
    }

    private fun loadProfiles() {
        val saved = getSharedPreferences("opencode_profiles", MODE_PRIVATE)
            .getStringSet("urls", emptySet())
            .orEmpty()
            .toMutableList()
        if (!saved.contains("https://opencode.ai")) saved.add("https://opencode.ai")
        profiles.clear()
        profiles.addAll(saved.sorted())
        profileAdapter.notifyDataSetChanged()
    }

    private fun loadProvidersAndModels() {
        val baseUrl = normalizedBaseUrl()
        if (baseUrl.isBlank()) {
            setStatus("Please set a base URL")
            return
        }
        setStatus("Loading providers/models...")
        scope.launch {
            runCatching {
                val response = getJson("$baseUrl/provider")
                val all = response.optJSONArray("all") ?: JSONArray()
                val providerToModels = linkedMapOf<String, MutableList<String>>()
                modelMap.clear()
                for (i in 0 until all.length()) {
                    val provider = all.optJSONObject(i) ?: continue
                    val providerId = provider.optString("id")
                    if (providerId.isBlank()) continue
                    val modelsObj = provider.optJSONObject("models") ?: JSONObject()
                    val models = mutableListOf<String>()
                    val keys = modelsObj.keys().asSequence().toList().sorted()
                    for (modelId in keys) {
                        val model = modelsObj.optJSONObject(modelId)
                        val modelName = model?.optString("name")?.takeIf { it.isNotBlank() } ?: modelId
                        val label = "$providerId / $modelId - $modelName"
                        modelMap[label] = providerId to modelId
                        models.add(label)
                    }
                    providerToModels[providerId] = models
                }
                providerToModels
            }.onSuccess { providerToModels ->
                providerItems.clear()
                providerItems.addAll(providerToModels.keys)
                providerAdapter.notifyDataSetChanged()
                rebuildModelList(providerItems.firstOrNull().orEmpty())
                setStatus("Loaded ${providerItems.size} providers")
            }.onFailure { err ->
                setStatus("Failed loading models: ${err.message}")
            }
        }
    }

    private fun rebuildModelList(providerId: String) {
        modelItems.clear()
        if (providerId.isNotBlank()) {
            val filtered = modelMap.keys.filter { it.startsWith("$providerId /") }.sorted()
            modelItems.addAll(filtered)
        }
        modelAdapter.notifyDataSetChanged()
    }

    private fun appendChat(author: String, message: String) {
        val current = chatText.text.toString()
        val next = if (current == "Chat log") {
            "$author: $message"
        } else {
            "$current\n\n$author: $message"
        }
        chatText.text = next
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun setStatus(value: String) {
        statusText.text = value
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: (String) -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: android.view.View?,
        position: Int,
        id: Long
    ) {
        val value = parent?.getItemAtPosition(position)?.toString().orEmpty()
        onSelected(value)
    }

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}

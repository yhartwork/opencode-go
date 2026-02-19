package com.example.codeonly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.codeonly.api.ChatBubble
import com.example.codeonly.api.OpenCodeClient
import com.example.codeonly.databinding.ActivityMainBinding
import com.example.codeonly.ui.ChatAdapter
import com.example.codeonly.ui.SessionAdapter
import com.example.codeonly.util.Preferences
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Preferences
    private lateinit var client: OpenCodeClient
    private lateinit var sessionAdapter: SessionAdapter
    private lateinit var chatAdapter: ChatAdapter

    private var sessions = mutableListOf<com.example.codeonly.api.Session>()
    private var currentSessionId: String? = null
    private var isStreaming = false
    private var pendingMessageId: String? = null
    private var reconnectJob: Job? = null
    private var currentProviderId: String? = null
    private var currentModelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Preferences(this)

        if (prefs.baseUrl.isBlank()) {
            navigateToConnection()
            return
        }

        client = OpenCodeClient(prefs.baseUrl)

        setupToolbar()
        setupDrawer()
        setupChat()
        setupInput()

        loadInitialData()
        connectEventStream()
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        client.disconnectEventSource()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawer() {
        binding.serverInfoText.text = "Connected to ${prefs.baseUrl}"

        sessionAdapter = SessionAdapter(
            onSessionClick = { session ->
                selectSession(session.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onSessionLongClick = { session ->
                showDeleteSessionDialog(session)
            }
        )

        binding.sessionRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionRecyclerView.adapter = sessionAdapter

        binding.newSessionButton.setOnClickListener {
            createNewSession()
        }

        binding.refreshSessionsButton.setOnClickListener {
            loadSessions()
        }

        binding.disconnectButton.setOnClickListener {
            prefs.clear()
            navigateToConnection()
        }
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = chatAdapter
    }

    private fun setupInput() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun navigateToConnection() {
        startActivity(Intent(this, ConnectionActivity::class.java))
        finish()
    }

    private fun loadInitialData() {
        loadSessions()
        loadProviders()
    }

    private fun loadSessions() {
        scope.launch {
            try {
                sessions = client.listSessions().toMutableList()
                sessionAdapter.submitList(sessions)

                if (currentSessionId == null && sessions.isNotEmpty()) {
                    selectSession(sessions.first().id)
                } else if (currentSessionId != null) {
                    val exists = sessions.any { it.id == currentSessionId }
                    if (!exists && sessions.isNotEmpty()) {
                        selectSession(sessions.first().id)
                    }
                }
            } catch (e: Exception) {
                showError("Failed to load sessions: ${e.message}")
            }
        }
    }

    private fun loadProviders() {
        scope.launch {
            try {
                val providers = client.getProviders()
                if (providers.all.isNotEmpty()) {
                    val savedProvider = prefs.lastProviderId
                    val savedModel = prefs.lastModelId

                    if (savedProvider.isNotBlank() && savedModel.isNotBlank()) {
                        currentProviderId = savedProvider
                        currentModelId = savedModel
                    } else {
                        val firstProvider = providers.all.first()
                        currentProviderId = firstProvider.id
                        val firstModel = firstProvider.models.keys.firstOrNull()
                        currentModelId = firstModel
                        if (firstProvider.id.isNotBlank() && firstModel != null) {
                            prefs.lastProviderId = firstProvider.id
                            prefs.lastModelId = firstModel
                        }
                    }
                }
            } catch (e: Exception) {
                showError("Failed to load providers: ${e.message}")
            }
        }
    }

    private fun selectSession(sessionId: String) {
        currentSessionId = sessionId
        sessionAdapter.setSelectedSession(sessionId)
        loadSessionMessages(sessionId)

        val session = sessions.find { it.id == sessionId }
        binding.toolbar.title = session?.title ?: session?.slug ?: "Session"
    }

    private fun loadSessionMessages(sessionId: String) {
        scope.launch {
            try {
                val messages = client.getSessionMessages(sessionId)
                val bubbles = mutableListOf<ChatBubble>()

                for (msg in messages) {
                    val role = msg.info.role ?: continue
                    if (role != "user" && role != "assistant") continue

                    val textParts = mutableListOf<String>()
                    var reasoning: String? = null

                    for (part in msg.parts) {
                        when (part) {
                            is com.example.codeonly.api.Part.Text -> textParts.add(part.text)
                            is com.example.codeonly.api.Part.Reasoning -> reasoning = part.text
                            else -> {}
                        }
                    }

                    if (textParts.isNotEmpty()) {
                        bubbles.add(ChatBubble(
                            role = role,
                            text = textParts.joinToString("\n"),
                            reasoning = reasoning
                        ))
                    }
                }

                chatAdapter.submitList(bubbles)
                scrollToBottom()
            } catch (e: Exception) {
                showError("Failed to load messages: ${e.message}")
            }
        }
    }

    private fun createNewSession() {
        scope.launch {
            try {
                val session = client.createSession()
                sessions.add(0, session)
                sessionAdapter.submitList(sessions.toList())
                selectSession(session.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } catch (e: Exception) {
                showError("Failed to create session: ${e.message}")
            }
        }
    }

    private fun showDeleteSessionDialog(session: com.example.codeonly.api.Session) {
        AlertDialog.Builder(this)
            .setTitle("Delete Session")
            .setMessage("Delete \"${session.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSession(session)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSession(session: com.example.codeonly.api.Session) {
        scope.launch {
            try {
                client.deleteSession(session.id)
                sessions.removeAll { it.id == session.id }
                sessionAdapter.submitList(sessions.toList())

                if (currentSessionId == session.id) {
                    currentSessionId = null
                    chatAdapter.submitList(emptyList())
                    if (sessions.isNotEmpty()) {
                        selectSession(sessions.first().id)
                    }
                }
            } catch (e: Exception) {
                showError("Failed to delete session: ${e.message}")
            }
        }
    }

    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isBlank()) return

        val sessionId = currentSessionId
        if (sessionId == null) {
            showError("No session selected")
            return
        }

        val providerId = currentProviderId
        val modelId = currentModelId

        binding.messageInput.setText("")
        chatAdapter.submitList(chatAdapter.currentList + ChatBubble("user", text))
        scrollToBottom()

        isStreaming = true
        val messageId = "msg_${System.currentTimeMillis()}"
        pendingMessageId = messageId

        chatAdapter.submitList(chatAdapter.currentList + ChatBubble("assistant", "", isStreaming = true))
        scrollToBottom()

        scope.launch {
            try {
                client.sendMessageAsync(sessionId, text, providerId, modelId, messageId)
            } catch (e: Exception) {
                isStreaming = false
                chatAdapter.finishStreaming()
                showError("Failed to send: ${e.message}")
            }
        }
    }

    private fun connectEventStream() {
        try {
            client.connectEventSource(
                onDelta = { sessionId, messageId, delta ->
                    if (sessionId == currentSessionId && messageId == pendingMessageId) {
                        runOnUiThread {
                            chatAdapter.appendStreamingText(delta)
                            scrollToBottom()
                        }
                    }
                },
                onIdle = { sessionId ->
                    if (sessionId == currentSessionId) {
                        runOnUiThread {
                            isStreaming = false
                            pendingMessageId = null
                            chatAdapter.finishStreaming()
                            loadSessions()
                        }
                    }
                },
                onError = { sessionId, error ->
                    if (sessionId == currentSessionId) {
                        runOnUiThread {
                            isStreaming = false
                            chatAdapter.finishStreaming()
                            showError("Error: $error")
                        }
                    }
                },
                onConnected = {
                    runOnUiThread {
                        binding.connectionBanner.visibility = View.GONE
                    }
                },
                onDisconnected = {
                    runOnUiThread {
                        scheduleReconnect()
                    }
                }
            )
        } catch (e: Exception) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        binding.connectionBanner.visibility = View.VISIBLE

        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                val delayTime = minOf(1000L * (1 shl attempt), 30000L)
                delay(delayTime)

                try {
                    client.disconnectEventSource()
                    connectEventStream()
                    break
                } catch (e: Exception) {
                    // continue retrying
                }
            }
        }
    }

    private fun scrollToBottom() {
        binding.chatRecyclerView.post {
            val itemCount = chatAdapter.itemCount
            if (itemCount > 0) {
                binding.chatRecyclerView.smoothScrollToPosition(itemCount - 1)
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

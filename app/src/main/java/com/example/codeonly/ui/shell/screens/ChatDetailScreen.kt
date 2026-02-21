package com.example.codeonly.ui.shell.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codeonly.domain.model.TimelineEvent
import com.example.codeonly.feature.chatdetail.ChatDetailViewModel
import com.example.codeonly.feature.chatdetail.ChatDetailViewModelFactory

@Composable
fun ChatDetailScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ChatDetailViewModel = viewModel(
        factory = ChatDetailViewModelFactory(context.applicationContext as Application, sessionId)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Column {
                Text(text = state.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${state.providerLabel} / ${state.modelLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.streamEnabled) {
                Button(onClick = viewModel::stopStreaming, enabled = state.streamConnected) {
                    Text("Stop")
                }
            } else {
                Button(onClick = viewModel::startStreaming) {
                    Text("Resume")
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        }

        state.message?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.events, key = { it.id }) { event ->
                    TimelineEventCard(event)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChanged,
                    placeholder = { Text("Type a prompt...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                )
                Button(
                    onClick = viewModel::sendMessage,
                    enabled = !state.isSending && state.inputText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(if (state.isSending) "Sending..." else "Send")
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelineEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (event) {
                is TimelineEvent.UserText -> {
                    Text("User", style = MaterialTheme.typography.labelMedium)
                    Text(event.text, style = MaterialTheme.typography.bodyLarge)
                }
                is TimelineEvent.AssistantText -> {
                    Text("Assistant", style = MaterialTheme.typography.labelMedium)
                    Text(event.text, style = MaterialTheme.typography.bodyLarge)
                    if (event.isStreaming) {
                        Text("streaming", style = MaterialTheme.typography.labelSmall)
                    }
                }
                is TimelineEvent.Reasoning -> {
                    Text("Reasoning", style = MaterialTheme.typography.labelMedium)
                    Text(event.text, style = MaterialTheme.typography.bodyMedium)
                }
                is TimelineEvent.ToolCall -> {
                    Text("Tool", style = MaterialTheme.typography.labelMedium)
                    Text("${event.name} (${event.status})", style = MaterialTheme.typography.bodyLarge)
                    event.input?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                is TimelineEvent.ToolResult -> {
                    Text("Tool Result", style = MaterialTheme.typography.labelMedium)
                    Text(event.output ?: "(no output)", style = MaterialTheme.typography.bodySmall)
                }
                is TimelineEvent.PatchSummary -> {
                    Text("Patch Summary", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "+${event.additions} -${event.deletions} (${event.filesChanged} files)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is TimelineEvent.ErrorEvent -> {
                    Text("Error", style = MaterialTheme.typography.labelMedium)
                    Text(event.title, style = MaterialTheme.typography.bodyLarge)
                    event.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                is TimelineEvent.SystemEvent -> {
                    Text("System", style = MaterialTheme.typography.labelMedium)
                    Text(event.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

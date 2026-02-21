package com.example.codeonly.ui.shell.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberDismissState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import com.example.codeonly.domain.model.SessionListItem
import com.example.codeonly.feature.chats.ChatsIntent
import com.example.codeonly.feature.chats.ChatsViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel = viewModel(),
    onOpenSession: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.onIntent(ChatsIntent.Refresh) }) {
                Text("Refresh")
            }
            Button(onClick = { viewModel.onIntent(ChatsIntent.NewSession) }) {
                Text("New Session")
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = state.isLoading,
            onRefresh = { viewModel.onIntent(ChatsIntent.Refresh) }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.sessions, key = { it.id }) { item ->
                    val dismissState = rememberDismissState(
                        confirmStateChange = { value ->
                            if (value == DismissValue.DismissedToEnd || value == DismissValue.DismissedToStart) {
                                viewModel.onIntent(ChatsIntent.DeleteSession(item.id))
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
                        background = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentAlignment = androidx.compose.ui.Alignment.CenterStart
                            ) {
                                Text("Delete", style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        dismissContent = {
                            SessionCard(
                                item = item,
                                selected = state.selectedSessionId == item.id,
                                onClick = {
                                    viewModel.onIntent(ChatsIntent.SelectSession(item.id))
                                    onOpenSession(item.id)
                                },
                                onDelete = { viewModel.onIntent(ChatsIntent.DeleteSession(item.id)) }
                            )
                        }
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = state.isLoading,
                state = pullRefreshState,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun SessionCard(
    item: SessionListItem,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (selected) "${item.title} (selected)" else item.title,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Session actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
            Text(text = item.preview, style = MaterialTheme.typography.bodyMedium)
            Text(text = item.updatedAtLabel, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(item.modelLabel) })
                AssistChip(onClick = {}, label = { Text(item.changeSummary) })
                if (item.isStreaming) {
                    AssistChip(onClick = {}, label = { Text("streaming") })
                }
                if (item.hasError) {
                    AssistChip(onClick = {}, label = { Text("error") })
                }
            }
        }
    }
}

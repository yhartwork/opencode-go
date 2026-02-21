package com.example.codeonly.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Chats("chats", "Chats", Icons.Outlined.Chat),
    Workspace("workspace", "Workspace", Icons.Outlined.Storage),
    Models("models", "Models", Icons.Outlined.Tune),
    Settings("settings", "Settings", Icons.Outlined.Settings)
}

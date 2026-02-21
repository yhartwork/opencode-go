package com.example.codeonly.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codeonly.feature.connection.ConnectionViewModel
import com.example.codeonly.ui.shell.screens.ChatDetailScreen
import com.example.codeonly.ui.shell.screens.ChatsScreen
import com.example.codeonly.ui.shell.screens.ModelsScreen
import com.example.codeonly.ui.shell.screens.SettingsScreen
import com.example.codeonly.ui.shell.screens.WorkspaceScreen

@Composable
fun OpenCodeApp() {
    MaterialTheme {
        val connectionViewModel: ConnectionViewModel = viewModel()
        val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()

        if (connectionState.isBootstrapping) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MaterialTheme
        }

        if (!connectionState.isConnected) {
            SettingsScreen(viewModel = connectionViewModel)
            return@MaterialTheme
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        Scaffold(
            bottomBar = {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Chats.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(TopLevelDestination.Chats.route) {
                    ChatsScreen(
                        onOpenSession = { sessionId ->
                            navController.navigate("chat/$sessionId")
                        }
                    )
                }
                composable(TopLevelDestination.Workspace.route) { WorkspaceScreen() }
                composable(TopLevelDestination.Models.route) { ModelsScreen() }
                composable(TopLevelDestination.Settings.route) { SettingsScreen(viewModel = connectionViewModel) }
                composable("chat/{sessionId}") { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    ChatDetailScreen(
                        sessionId = sessionId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

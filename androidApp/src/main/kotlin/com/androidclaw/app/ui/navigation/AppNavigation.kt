package com.androidclaw.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.androidclaw.app.ui.chat.ChatScreen
import com.androidclaw.app.ui.conversations.ConversationListScreen
import com.androidclaw.app.ui.settings.ModelManagementScreen
import com.androidclaw.app.ui.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "conversations") {
        composable("conversations") {
            ConversationListScreen(
                onConversationClick = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onNewConversation = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable(
            "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate("settings/models") }
            )
        }

        composable("settings/models") {
            ModelManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

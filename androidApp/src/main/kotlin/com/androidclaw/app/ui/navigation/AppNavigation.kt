package com.androidclaw.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.androidclaw.shared.memory.ConversationRepository
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.app.ui.chat.ChatScreen
import com.androidclaw.app.ui.conversations.ConversationListScreen
import com.androidclaw.app.ui.onboarding.OnboardingScreen
import com.androidclaw.app.ui.settings.ModelManagementScreen
import com.androidclaw.app.ui.settings.SettingsScreen
import com.androidclaw.app.ui.search.GlobalSearchScreen
import com.androidclaw.app.ui.settings.SystemPromptScreen
import com.androidclaw.app.ui.settings.ToolsScreen
import com.androidclaw.app.ui.settings.UsageStatsScreen
import org.koin.compose.koinInject

@Composable
fun AppNavigation(shortcutRoute: String? = null) {
    val navController = rememberNavController()
    val settingsManager = koinInject<SettingsManager>()
    val conversationRepo = koinInject<ConversationRepository>()
    val onboardingCompleted by settingsManager.onboardingCompleted.collectAsState()

    val startDestination = if (onboardingCompleted) "conversations" else "onboarding"

    // Handle shortcut intents
    LaunchedEffect(shortcutRoute) {
        if (shortcutRoute != null && onboardingCompleted) {
            when (shortcutRoute) {
                "new_conversation", "new_conversation_voice" -> {
                    val id = conversationRepo.createConversation()
                    navController.navigate("chat/$id")
                }
                "search" -> navController.navigate("search")
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    navController.navigate("conversations") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

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
                },
                onGlobalSearchClick = {
                    navController.navigate("search")
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
                onNavigateToModels = { navController.navigate("settings/models") },
                onNavigateToPersona = { navController.navigate("settings/persona") },
                onNavigateToStats = { navController.navigate("settings/stats") },
                onNavigateToTools = { navController.navigate("settings/tools") }
            )
        }

        composable("settings/tools") {
            ToolsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings/models") {
            ModelManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings/persona") {
            SystemPromptScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings/stats") {
            UsageStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("search") {
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onConversationClick = { conversationId ->
                    navController.navigate("chat/$conversationId")
                }
            )
        }
    }
}

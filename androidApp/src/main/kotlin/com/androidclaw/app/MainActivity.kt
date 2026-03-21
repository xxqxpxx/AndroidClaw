package com.androidclaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.androidclaw.app.shortcuts.AppShortcuts
import com.androidclaw.app.ui.navigation.AppNavigation
import com.androidclaw.app.ui.theme.AndroidClawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val shortcutAction = intent?.action
        val startRoute = when (shortcutAction) {
            AppShortcuts.ACTION_NEW_CONVERSATION -> "new_conversation"
            AppShortcuts.ACTION_VOICE_INPUT -> "new_conversation_voice"
            AppShortcuts.ACTION_SEARCH -> "search"
            else -> null
        }

        setContent {
            AndroidClawTheme {
                AppNavigation(shortcutRoute = startRoute)
            }
        }
    }
}

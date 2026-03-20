package com.androidclaw.app.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.androidclaw.app.R

/**
 * Manages dynamic Android App Shortcuts (long-press on launcher icon).
 * Provides quick actions: New Conversation, Voice Input, Search.
 */
object AppShortcuts {

    const val ACTION_NEW_CONVERSATION = "com.androidclaw.action.NEW_CONVERSATION"
    const val ACTION_VOICE_INPUT = "com.androidclaw.action.VOICE_INPUT"
    const val ACTION_SEARCH = "com.androidclaw.action.SEARCH"

    fun setup(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        setupShortcuts(context)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun setupShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val launchActivity = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.component ?: return

        val newConversation = ShortcutInfo.Builder(context, "new_conversation")
            .setShortLabel("New Chat")
            .setLongLabel("Start a new conversation")
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_add))
            .setIntent(Intent(ACTION_NEW_CONVERSATION).apply {
                setClassName(context.packageName, launchActivity.className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            .setRank(0)
            .build()

        val voiceInput = ShortcutInfo.Builder(context, "voice_input")
            .setShortLabel("Voice")
            .setLongLabel("Start voice conversation")
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_btn_speak_now))
            .setIntent(Intent(ACTION_VOICE_INPUT).apply {
                setClassName(context.packageName, launchActivity.className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            .setRank(1)
            .build()

        val search = ShortcutInfo.Builder(context, "search")
            .setShortLabel("Search")
            .setLongLabel("Search conversations")
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_search))
            .setIntent(Intent(ACTION_SEARCH).apply {
                setClassName(context.packageName, launchActivity.className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            .setRank(2)
            .build()

        shortcutManager.dynamicShortcuts = listOf(newConversation, voiceInput, search)
    }
}

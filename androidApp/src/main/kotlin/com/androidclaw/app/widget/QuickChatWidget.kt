package com.androidclaw.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.androidclaw.app.MainActivity
import com.androidclaw.app.R

/**
 * Home screen widget for quick access to AndroidClaw.
 * Shows last response snippet and provides quick-ask and voice buttons.
 */
class QuickChatWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_QUICK_ASK = "com.androidclaw.QUICK_ASK"
        const val ACTION_VOICE_INPUT = "com.androidclaw.VOICE_INPUT"
        const val EXTRA_WIDGET_TEXT = "widget_last_response"

        fun updateWidget(context: Context, lastResponse: String? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, QuickChatWidget::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            for (id in widgetIds) {
                updateAppWidget(context, manager, id, lastResponse)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            lastResponse: String? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_chat)

            // Open app on tap
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_ASK
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_ask_button, openPending)
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            // Voice button - open app in voice mode
            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_VOICE_INPUT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val voicePending = PendingIntent.getActivity(context, 1, voiceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_voice_button, voicePending)

            // Update last response if provided
            if (lastResponse != null) {
                views.setTextViewText(R.id.widget_last_response, lastResponse)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
